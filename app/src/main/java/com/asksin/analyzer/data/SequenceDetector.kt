package com.asksin.analyzer.data

import com.asksin.analyzer.model.Telegram
import com.asksin.analyzer.model.TelegramSequence
import com.asksin.analyzer.model.SequenceType

class SequenceDetector {

    private companion object {
        const val SEQUENCE_TIMEOUT_MS = 1500L
        const val FLAG_BIDI = 0x20
    }

    private data class OpenSequence(
        val type: SequenceType,
        val telegramIds: MutableList<Long>,
        val firstId: Long,
        val expectedSrc: String,
        val expectedDst: String,
        val expectedCounter: Int,
        val startTime: Long,
        var acceptsMore: Boolean,       // for config read: accepts multiple responses
        var pairingSrc: String? = null   // for pairing: any source can respond
    )

    private val openSequences = mutableListOf<OpenSequence>()
    private val _completedSequences = mutableMapOf<Long, TelegramSequence>()
    private val _telegramToSequence = mutableMapOf<Long, Long>()

    val completedSequences: Map<Long, TelegramSequence> get() = _completedSequences
    val telegramToSequence: Map<Long, Long> get() = _telegramToSequence

    fun ingest(t: Telegram) {
        expireOpenSequences(t.timestamp)

        // Try to match against open sequences
        if (tryMatch(t)) return

        // Try to start a new sequence
        tryStartSequence(t)
    }

    fun detectAll(telegrams: List<Telegram>): Map<Long, TelegramSequence> {
        clear()
        val sorted = telegrams.sortedBy { it.timestamp }
        for (t in sorted) {
            ingest(t)
        }
        // Close all remaining open sequences
        closeAllOpen()
        return _completedSequences.toMap()
    }

    fun clear() {
        openSequences.clear()
        _completedSequences.clear()
        _telegramToSequence.clear()
    }

    fun allSequences(): Map<Long, TelegramSequence> {
        // Return completed + snapshot of open as incomplete sequences
        val result = _completedSequences.toMutableMap()
        for (open in openSequences) {
            result[open.firstId] = TelegramSequence(
                id = open.firstId,
                type = open.type,
                telegramIds = open.telegramIds.toList(),
                isComplete = false
            )
        }
        return result
    }

    private fun tryMatch(t: Telegram): Boolean {
        val iter = openSequences.iterator()
        while (iter.hasNext()) {
            val open = iter.next()

            // Check timeout
            if (t.timestamp - open.startTime > SEQUENCE_TIMEOUT_MS) {
                iter.remove()
                completeSequence(open, isComplete = false)
                continue
            }

            val matched = when (open.type) {
                SequenceType.PAIRING -> matchPairing(t, open)
                SequenceType.CONFIG_READ -> matchConfigRead(t, open)
                SequenceType.AES_HANDSHAKE -> matchAesHandshake(t, open)
                else -> matchStandard(t, open)
            }

            if (matched) {
                open.telegramIds.add(t.id)
                _telegramToSequence[t.id] = open.firstId

                if (!open.acceptsMore) {
                    iter.remove()
                    completeSequence(open, isComplete = true)
                }
                return true
            }
        }
        return false
    }

    private fun matchStandard(t: Telegram, open: OpenSequence): Boolean {
        return t.msgCounter == open.expectedCounter &&
                t.srcAddress == open.expectedSrc &&
                t.dstAddress == open.expectedDst
    }

    private fun matchPairing(t: Telegram, open: OpenSequence): Boolean {
        // Pairing: DEVINFO(broadcast) → CONFIG from any CCU → ACK → KEY_EXCHANGE...
        // After first response, lock to that address pair
        if (open.telegramIds.size == 1) {
            // Expecting CONFIG (0x01) from any source to the DEVINFO sender
            if (t.msgType == 0x01 && t.dstAddress == open.expectedDst) {
                open.pairingSrc = t.srcAddress
                return true
            }
        } else if (open.pairingSrc != null) {
            // Subsequent messages between the pair
            val src = open.pairingSrc!!
            val dst = open.expectedDst
            if ((t.srcAddress == src && t.dstAddress == dst) ||
                (t.srcAddress == dst && t.dstAddress == src)) {
                // Close after ACK/KEY_EXCHANGE round
                if (t.msgType == 0x02 && open.telegramIds.size >= 3) {
                    open.acceptsMore = false
                }
                return true
            }
        }
        return false
    }

    private fun matchAesHandshake(t: Telegram, open: OpenSequence): Boolean {
        val msgIndex = open.telegramIds.size  // 1-based: next expected message
        return when (msgIndex) {
            1 -> {
                // Message 2: RESPONSE_AES (0x03) from dst→src
                t.msgType == 0x03 &&
                        t.srcAddress == open.expectedSrc &&
                        t.dstAddress == open.expectedDst
            }
            2 -> {
                // Message 3: AES response from original sender back
                val matches = t.srcAddress == open.expectedDst &&
                        t.dstAddress == open.expectedSrc
                if (matches) open.acceptsMore = false
                matches
            }
            else -> false
        }
    }

    private fun matchConfigRead(t: Telegram, open: OpenSequence): Boolean {
        // Accepts multiple RESPONSE (0x02) from the expected source
        if (t.srcAddress == open.expectedSrc && t.dstAddress == open.expectedDst) {
            if (t.msgType == 0x02) {
                return true  // another config response frame
            } else {
                // Non-RESPONSE from same source closes the sequence
                open.acceptsMore = false
                completeSequence(open, isComplete = true)
                openSequences.remove(open)
                return false
            }
        }
        return false
    }

    private fun tryStartSequence(t: Telegram) {
        val hasBidi = t.flags and FLAG_BIDI != 0

        // Pairing: DEVINFO to broadcast
        if (t.msgType == 0x00 && t.isBroadcast) {
            startSequence(t, SequenceType.PAIRING,
                expectedSrc = "", expectedDst = t.srcAddress,
                acceptsMore = true)
            return
        }

        // AES Handshake: message with AES flag (0x08) + BIDI
        if (t.flags and 0x08 != 0 && hasBidi) {
            startSequence(t, SequenceType.AES_HANDSHAKE,
                expectedSrc = t.dstAddress, expectedDst = t.srcAddress,
                acceptsMore = true)
            return
        }

        if (!hasBidi) return  // All remaining types require BIDI

        // Config subtypes (payload[0] determines operation)
        if (t.msgType == 0x01 && t.payload.isNotEmpty()) {
            val subtype = t.payload[0].toInt() and 0xFF
            when (subtype) {
                0x03, 0x04 -> {
                    // Config Read (PeerListReq / ParamReq) — expects multiple responses
                    startSequence(t, SequenceType.CONFIG_READ,
                        expectedSrc = t.dstAddress, expectedDst = t.srcAddress,
                        acceptsMore = true)
                    return
                }
                0x01, 0x02, 0x08 -> {
                    // Config Write (PeerAdd / PeerRemove / ParamSet) — expects single ACK
                    startSequence(t, SequenceType.CONFIG_WRITE,
                        expectedSrc = t.dstAddress, expectedDst = t.srcAddress)
                    return
                }
            }
        }

        // Key Exchange
        if (t.msgType == 0x04) {
            startSequence(t, SequenceType.KEY_EXCHANGE,
                expectedSrc = t.dstAddress, expectedDst = t.srcAddress)
            return
        }

        // Command + Status: ACTION expects INFO response
        if (t.msgType == 0x11) {
            startSequence(t, SequenceType.COMMAND_STATUS,
                expectedSrc = t.dstAddress, expectedDst = t.srcAddress)
            return
        }

        // GET → INFO
        if (t.msgType == 0x12) {
            startSequence(t, SequenceType.GET_INFO,
                expectedSrc = t.dstAddress, expectedDst = t.srcAddress)
            return
        }

        // Event + ACK
        if (t.msgType in setOf(0x40, 0x41, 0x53, 0x58, 0x70)) {
            startSequence(t, SequenceType.EVENT_ACK,
                expectedSrc = t.dstAddress, expectedDst = t.srcAddress)
            return
        }

        // Generic BIDI fallback
        startSequence(t, SequenceType.GENERIC_BIDI,
            expectedSrc = t.dstAddress, expectedDst = t.srcAddress)
    }

    private fun startSequence(
        t: Telegram,
        type: SequenceType,
        expectedSrc: String,
        expectedDst: String,
        acceptsMore: Boolean = false
    ) {
        val open = OpenSequence(
            type = type,
            telegramIds = mutableListOf(t.id),
            firstId = t.id,
            expectedSrc = expectedSrc,
            expectedDst = expectedDst,
            expectedCounter = t.msgCounter,
            startTime = t.timestamp,
            acceptsMore = acceptsMore
        )
        openSequences.add(open)
        _telegramToSequence[t.id] = t.id
    }

    private fun completeSequence(open: OpenSequence, isComplete: Boolean) {
        // Only create a sequence if it has more than 1 telegram
        if (open.telegramIds.size > 1) {
            _completedSequences[open.firstId] = TelegramSequence(
                id = open.firstId,
                type = open.type,
                telegramIds = open.telegramIds.toList(),
                isComplete = isComplete
            )
        } else {
            // Single telegram — remove the telegramToSequence mapping
            _telegramToSequence.remove(open.firstId)
        }
    }

    private fun expireOpenSequences(currentTime: Long) {
        val iter = openSequences.iterator()
        while (iter.hasNext()) {
            val open = iter.next()
            if (currentTime - open.startTime > SEQUENCE_TIMEOUT_MS) {
                iter.remove()
                completeSequence(open, isComplete = false)
            }
        }
    }

    private fun closeAllOpen() {
        for (open in openSequences) {
            completeSequence(open, isComplete = false)
        }
        openSequences.clear()
    }
}
