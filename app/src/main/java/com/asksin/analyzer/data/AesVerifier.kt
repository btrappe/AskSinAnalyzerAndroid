package com.asksin.analyzer.data

import com.asksin.analyzer.model.Telegram
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class AesVerifier(hexKey: String) {

    private val key: ByteArray? = parseHexKey(hexKey)

    val isEnabled: Boolean get() = key != null

    /**
     * Verify a BidCoS AES handshake sequence.
     *
     * Algorithm (from Homegear AesHandshake.cpp getAFrame):
     * 1. Derive tempKey: XOR key[0..5] with challenge payload bytes [1..6]
     * 2. AES-ECB decrypt the RESPONSE_AES 16-byte payload → pd
     * 3. XOR pd[0..n] with original message payload[1..n+1]
     * 4. AES-ECB decrypt pd again → pdd
     * 5. Verify pdd[6..15] matches original message fields
     *
     * @param sequenceTelegrams All telegrams in the AES handshake sequence, in order
     * @return true if verified, false if failed, null if disabled or insufficient data
     */
    fun verify(sequenceTelegrams: List<Telegram>): Boolean? {
        val aesKey = key ?: return null

        val original = sequenceTelegrams.firstOrNull() ?: return null
        val responseAes = sequenceTelegrams.find { it.msgType == 0x03 } ?: return null

        // The challenge is in the RESPONSE (0x02) that comes BEFORE the RESPONSE_AES
        val aesIdx = sequenceTelegrams.indexOf(responseAes)
        val challenge = sequenceTelegrams.subList(0, aesIdx).findLast { it.msgType == 0x02 }
            ?: return null  // can't verify without the challenge

        if (challenge.payload.size < 7) return null  // need at least 7 bytes (index 1..6)
        if (responseAes.payload.size != 16) return false

        try {
            // Step 1: Derive temp key — XOR key[0..5] with challenge.payload[1..6]
            val tempKey = aesKey.copyOf()
            for (j in 0..5) {
                tempKey[j] = (aesKey[j].toInt() xor (challenge.payload[j + 1].toInt() and 0xFF)).toByte()
            }

            val keySpec = SecretKeySpec(tempKey, "AES")
            val cipher = Cipher.getInstance("AES/ECB/NoPadding")

            // Step 2: First AES-ECB decrypt of RESPONSE_AES payload
            cipher.init(Cipher.DECRYPT_MODE, keySpec)
            val pd = cipher.doFinal(responseAes.payload)

            // Step 3: XOR pd with original message payload starting at index 1
            for (j in 1 until original.payload.size) {
                if (j - 1 >= pd.size) break
                pd[j - 1] = (pd[j - 1].toInt() xor (original.payload[j].toInt() and 0xFF)).toByte()
            }

            // Step 4: Second AES-ECB decrypt
            cipher.init(Cipher.DECRYPT_MODE, keySpec)
            val pdd = cipher.doFinal(pd)

            // Step 5: Verify pdd[6..15] against original message fields
            val srcBytes = hexToBytes(original.srcAddress)
            val dstBytes = hexToBytes(original.dstAddress)

            if (pdd[6].toInt() and 0xFF != original.msgCounter and 0xFF) return false
            if (pdd[7].toInt() and 0xBF != original.flags and 0xBF) return false
            if (pdd[8].toInt() and 0xFF != original.msgType and 0xFF) return false
            if (pdd[9] != srcBytes[0]) return false
            if (pdd[10] != srcBytes[1]) return false
            if (pdd[11] != srcBytes[2]) return false
            if (pdd[12] != dstBytes[0]) return false
            if (pdd[13] != dstBytes[1]) return false
            if (pdd[14] != dstBytes[2]) return false
            if (original.payload.isNotEmpty() && pdd[15].toInt() and 0xFF != original.payload[0].toInt() and 0xFF) return false

            return true
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
