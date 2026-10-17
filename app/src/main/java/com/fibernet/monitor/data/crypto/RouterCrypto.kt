package com.fibernet.monitor.data.crypto

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object RouterCrypto {

    /**
     * Mengekstrak kunci enkripsi AES-128 dari respons string acsRandom.
     * Format acsRandom FiberHome: 6 karakter acak + 16 karakter kunci + suffix acak.
     */
    fun extractAesKey(acsRandom: String): String {
        return if (acsRandom.length >= 22) {
            acsRandom.substring(6, 22)
        } else {
            acsRandom.padEnd(16, '0').substring(0, 16)
        }
    }

    /**
     * Mengenkripsi password menggunakan algoritma FiberHome: AES-128-CBC dengan Key = IV = key,
     * padding PKCS5Padding, dan menghasilkan string representasi Hexadesimal Uppercase.
     */
    fun encryptPassword(password: String, aesKey: String): String {
        val keyBytes = aesKey.toByteArray(Charsets.UTF_8)
        val keySpec = SecretKeySpec(keyBytes, "AES")
        val ivSpec = IvParameterSpec(keyBytes)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        val encryptedBytes = cipher.doFinal(password.toByteArray(Charsets.UTF_8))

        return bytesToHex(encryptedBytes)
    }

    /**
     * Mendekripsi string ciphertext hexadesimal dari respons router FiberHome
     * (misal SSID atau PreSharedKey) menggunakan AES-128-CBC dengan key = IV = aesKey.
     */
    fun decrypt(hexCiphertext: String, aesKey: String): String {
        if (hexCiphertext.isBlank() || hexCiphertext.length % 2 != 0) return hexCiphertext
        return try {
            val keyBytes = aesKey.toByteArray(Charsets.UTF_8)
            val keySpec = SecretKeySpec(keyBytes, "AES")
            val ivSpec = IvParameterSpec(keyBytes)

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            val decryptedBytes = cipher.doFinal(hexToBytes(hexCiphertext))
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            hexCiphertext
        }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val result = ByteArray(hex.length / 2)
        for (i in result.indices) {
            val index = i * 2
            result[i] = hex.substring(index, index + 2).toInt(16).toByte()
        }
        return result
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = "0123456789ABCDEF"
        val result = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val i = b.toInt() and 0xFF
            result.append(hexChars[i shr 4])
            result.append(hexChars[i and 0x0F])
        }
        return result.toString()
    }
}

