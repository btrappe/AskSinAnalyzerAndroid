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
        var type: SequenceType,
        val telegramIds: MutableList<Long>,
        val firstId: Long,
        val expectedSrc: String,
        val expectedDst: String,
        val expectedCounter: Int,
        val startTime: Long,
        var acceptsMore: Boolean,
        var pairingSrc: String? = null
    )

    // Recently completed sequences, keyed by "counter:addrA:addrB" (sorted pair)
    // Used to retroactively upgrade sequences when RESPONSE_AES arrives late
    private data class RecentSequence(
        val open: OpenSequence,
        val completedTime: Long
    )

    private val openSequences = mutableListOf<OpenSequence>()
    private val recentlyCompleted = mutableMapOf<String, RecentSequence>()
    private val _completedSequences = mutableMapOf<Long, TelegramSequence>()
    private val _telegramToSequence = mutableMapOf<Long, Long>()

    val completedSequences: Map<Long, TelegramSequence> get() = _completedSequences
    val telegramToSequence: Map<Long, Long> get() = _telegramToSequence

    fun ingest(t: Telegram) {
        expireOpenSequences(t.timestamp)

        // Try to match against open sequences or recently completed (for AES upgrade)
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
        closeAllOpen()
        return _completedSequences.toMap()
    }

    fun clear() {
        openSequences.clear()
        recentlyCompleted.clear()
        _completedSequences.clear()
        _telegramToSequence.clear()
    }

    fun allSequences(): Map<Long, TelegramSequence> {
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
        // Special handling: RESPONSE_AES (0x03) may need to re-open a recently completed sequence
        if (t.msgType == 0x03) {
            if (tryMatchAesReopen(t)) return true
        }

        val iter = openSequences.iterator()
        while (iter.hasNext()) {
            val open = iter.next()

            if (t.timestamp - open.startTime > SEQUENCE_TIMEOUT_MS) {
                iter.remove()
                completeSequence(open, isComplete = false, t.timestamp)
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

                // If this is a RESPONSE_AES matching an open non-AES sequence, upgrade it
                if (t.msgType == 0x03 && open.type != SequenceType.AES_HANDSHAKE) {
                    open.type = SequenceType.AES_HANDSHAKE
                    open.acceptsMore = true  // expect final ACK
                } else if (!open.acceptsMore) {
                    iter.remove()
                    completeSequence(open, isComplete = true, t.timestamp)
                }
                return true
            }
        }
        return false
    }

    /**
     * When a RESPONSE_AES arrives and no open sequence matches,
     * check recently completed sequences and re-open them as AES_HANDSHAKE.
     */
    private fun tryMatchAesReopen(t: Telegram): Boolean {
        val key = recentKey(t.msgCounter, t.srcAddress, t.dstAddress)
        val recent = recentlyCompleted[key] ?: return false

        if (t.timestamp - recent.open.startTime > SEQUENCE_TIMEOUT_MS) {
            recentlyCompleted.remove(key)
            return false
        }

        // Re-open the sequence as AES_HANDSHAKE
        val open = recent.open
        open.type = SequenceType.AES_HANDSHAKE
        open.acceptsMore = true  // expect final ACK
        open.telegramIds.add(t.id)
        _telegramToSequence[t.id] = open.firstId

        // Remove from completed, add back to open
        recentlyCompleted.remove(key)
        _completedSequences.remove(open.firstId)
        openSequences.add(open)
        return true
    }

    private fun matchStandard(t: Telegram, open: OpenSequence): Boolean {
        return t.msgCounter == open.expectedCounter &&
                t.srcAddress == open.expectedSrc &&
                t.dstAddress == open.expectedDst
    }

    private fun matchPairing(t: Telegram, open: OpenSequence): Boolean {
        if (open.telegramIds.size == 1) {
            if (t.msgType == 0x01 && t.dstAddress == open.expectedDst) {
                open.pairingSrc = t.srcAddress
                return true
            }
        } else if (open.pairingSrc != null) {
            val src = open.pairingSrc!!
            val dst = open.expectedDst
            if ((t.srcAddress == src && t.dstAddress == dst) ||
                (t.srcAddress == dst && t.dstAddress == src)) {
                if (t.msgType == 0x02 && open.telegramIds.size >= 3) {
                    open.acceptsMore = false
                }
                return true
            }
        }
        return false
    }

    private fun matchAesHandshake(t: Telegram, open: OpenSequence): Boolean {
        // AES handshake accepts messages between the address pair with matching counter
        if (t.msgCounter != open.expectedCounter) return false

        val addrA = open.expectedDst  // original sender
        val addrB = open.expectedSrc  // original responder
        val matchesPair = (t.srcAddress == addrA && t.dstAddress == addrB) ||
                (t.srcAddress == addrB && t.dstAddress == addrA)
        if (!matchesPair) return false

        // RESPONSE (0x02) as final ACK closes the sequence
        if (t.msgType == 0x02) {
            open.acceptsMore = false
        }
        return true
    }

    private fun matchConfigRead(t: Telegram, open: OpenSequence): Boolean {
        if (t.srcAddress == open.expectedSrc && t.dstAddress == open.expectedDst) {
            if (t.msgType == 0x02) {
                return true
            } else {
                open.acceptsMore = false
                completeSequence(open, isComplete = true, t.timestamp)
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

        if (!hasBidi) return  // All remaining types require BIDI

        // Config subtypes (payload[1] is subcommand, payload[0] is channel)
        if (t.msgType == 0x01 && t.payload.size >= 2) {
            val subtype = t.payload[1].toInt() and 0xFF
            when (subtype) {
                0x03, 0x04 -> {
                    startSequence(t, SequenceType.CONFIG_READ,
                        expectedSrc = t.dstAddress, expectedDst = t.srcAddress,
                        acceptsMore = true)
                    return
                }
                0x01, 0x02, 0x08 -> {
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

    private fun completeSequence(open: OpenSequence, isComplete: Boolean, currentTime: Long = 0L) {
        if (open.telegramIds.size > 1) {
            _completedSequences[open.firstId] = TelegramSequence(
                id = open.firstId,
                type = open.type,
                telegramIds = open.telegramIds.toList(),
                isComplete = isComplete
            )
            // Track recently completed for potential AES upgrade
            val key = recentKey(open.expectedCounter, open.expectedSrc, open.expectedDst)
            recentlyCompleted[key] = RecentSequence(open, currentTime)
        } else {
            _telegramToSequence.remove(open.firstId)
        }
    }

    private fun recentKey(counter: Int, addr1: String, addr2: String): String {
        // Sort addresses so the key is direction-independent
        val (a, b) = if (addr1 < addr2) addr1 to addr2 else addr2 to addr1
        return "$counter:$a:$b"
    }

    private fun expireOpenSequences(currentTime: Long) {
        val iter = openSequences.iterator()
        while (iter.hasNext()) {
            val open = iter.next()
            if (currentTime - open.startTime > SEQUENCE_TIMEOUT_MS) {
                iter.remove()
                completeSequence(open, isComplete = false, currentTime)
            }
        }
        // Also expire recently completed
        recentlyCompleted.entries.removeAll { (_, v) ->
            currentTime - v.open.startTime > SEQUENCE_TIMEOUT_MS * 2
        }
    }

    private fun closeAllOpen() {
        for (open in openSequences) {
            completeSequence(open, isComplete = false)
        }
        openSequences.clear()
    }
}
