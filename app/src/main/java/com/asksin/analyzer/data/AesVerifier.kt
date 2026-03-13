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
     * @param original The original message with AES flag
     * @param challenge The RESPONSE_AES (0x03) containing the 6-byte challenge
     * @param response The AES response containing the 4-byte authentication tag
     * @return true if verified, false if failed, null if verifier is disabled
     */
    fun verify(original: Telegram, challenge: Telegram, response: Telegram): Boolean? {
        val aesKey = key ?: return null

        try {
            // Extract 6-byte challenge from RESPONSE_AES payload
            if (challenge.payload.size < 6) return false
            val challengeBytes = challenge.payload.copyOfRange(0, 6)

            // Extract 4-byte authentication tag from AES response payload
            if (response.payload.size < 4) return false
            val tag = response.payload.copyOfRange(0, 4)

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

            // Compare last 4 bytes of ciphertext to the authentication tag
            if (encrypted.size < 4) return false
            val computed = encrypted.copyOfRange(encrypted.size - 4, encrypted.size)
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
