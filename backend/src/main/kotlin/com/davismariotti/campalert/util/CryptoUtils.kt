package com.davismariotti.campalert.util

import java.security.MessageDigest

object CryptoUtils {
    fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
