package com.asksin.analyzer.data

import com.asksin.analyzer.model.Telegram
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class AesVerifier(hexKey: String) {

    private val key: ByteArray? = parseHexKey(hexKey)

    val isEnabled: Boolean get() = key != null

    /**
     * Verify an AES handshake sequence.
     * The sequence is: original → challenge RESPONSE → RESPONSE_AES → final ACK
     *
     * Finds the RESPONSE_AES (0x03) and the preceding RESPONSE (challenge) in the
     * telegram list. The challenge RESPONSE payload contains the 6-byte nonce, and
     * the RESPONSE_AES payload contains the 16-byte encrypted authentication data.
     *
     * @param sequenceTelegrams All telegrams in the AES handshake sequence, in order
     * @return true if verified, false if failed, null if verifier disabled or insufficient data
     */
    fun verify(sequenceTelegrams: List<Telegram>): Boolean? {
        val aesKey = key ?: return null

        // Find the key messages in the sequence
        val original = sequenceTelegrams.firstOrNull() ?: return null
        val responseAes = sequenceTelegrams.find { it.msgType == 0x03 } ?: return null

        // The challenge is in the RESPONSE (0x02) that comes BEFORE the RESPONSE_AES
        val aesIdx = sequenceTelegrams.indexOf(responseAes)
        val challenge = sequenceTelegrams.subList(0, aesIdx).findLast { it.msgType == 0x02 }

        // Without the challenge RESPONSE, we can't verify
        if (challenge == null) return null

        try {
            // Extract 6-byte challenge from RESPONSE payload
            if (challenge.payload.size < 6) return false
            val challengeBytes = challenge.payload.copyOfRange(0, 6)

            // The RESPONSE_AES payload is the encrypted AES data (16 bytes)
            if (responseAes.payload.size < 4) return false

            // Build plaintext: challenge + msgCounter + (flags & 0xBF) + msgType + src + dst + payload
            val srcBytes = hexToBytes(original.srcAddress)
            val dstBytes = hexToBytes(original.dstAddress)

            val plaintext = mutableListOf<Byte>()
            plaintext.addAll(challengeBytes.toList())
            plaintext.add((original.msgCounter and 0xFF).toByte())
            plaintext.add((original.flags and 0xBF).toByte())  // clear RPTED bit
            plaintext.add((original.msgType and 0xFF).toByte())
            plaintext.addAll(srcBytes.toList())
            plaintext.addAll(dstBytes.toList())
            plaintext.addAll(original.payload.toList())

            // Zero-pad to 16-byte block boundary
            val padded = plaintext.toByteArray()
            val blockSize = 16
            val paddedLen = ((padded.size + blockSize - 1) / blockSize) * blockSize
            val input = ByteArray(paddedLen)
            padded.copyInto(input)

            // AES-128-CBC encrypt with IV=0
            val cipher = Cipher.getInstance("AES/CBC/NoPadding")
            val keySpec = SecretKeySpec(aesKey, "AES")
            val ivSpec = IvParameterSpec(ByteArray(16))
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
            val encrypted = cipher.doFinal(input)

            // Compare last 4 bytes of ciphertext to the first 4 bytes of RESPONSE_AES payload
            if (encrypted.size < 4) return false
            val computed = encrypted.copyOfRange(encrypted.size - 4, encrypted.size)
            val tag = responseAes.payload.copyOfRange(0, 4)
            return computed.contentEquals(tag)
        } catch (_: Exception) {
            return false
        }
    }

    private fun hexToBytes(hex: String): ByteArray {
        return ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    companion object {
        private fun parseHexKey(hex: String): ByteArray? {
            if (hex.length != 32) return null
            return try {
                ByteArray(16) { i ->
                    hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}
