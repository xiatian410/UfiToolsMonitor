package com.xingyue.ufitools.monitor.data

import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import okhttp3.OkHttpClient

object NetClient {

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val sha256Digest = ThreadLocal.withInitial { MessageDigest.getInstance("SHA-256") }
    private val hmacMd5Mac = ThreadLocal.withInitial { Mac.getInstance("HmacMD5") }

    fun sha256(input: String): String =
        bytesToHex(sha256Bytes(input.toByteArray(Charsets.UTF_8)))

    private fun sha256Bytes(input: ByteArray): ByteArray {
        val digest = sha256Digest.get()!!
        digest.reset()
        return digest.digest(input)
    }

    private fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    private fun hmacMd5(data: String, key: String): ByteArray {
        val mac = hmacMd5Mac.get()!!
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacMD5"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8))
    }

    /**
     * UFI-TOOLS 签名：HMAC-MD5 → 16 bytes 二分（各 8 bytes）
     * → SHA256(part1)+SHA256(part2) 拼接 64 bytes → SHA256 → hex
     */
    fun generateKanoSign(method: String, path: String, timestamp: Long, secretKey: String): String {
        val rawData = "minikano${method.uppercase()}$path$timestamp"
        val hmacBytes = hmacMd5(rawData, secretKey)
        val mid = hmacBytes.size / 2
        val sha1 = sha256Bytes(hmacBytes.copyOfRange(0, mid))
        val sha2 = sha256Bytes(hmacBytes.copyOfRange(mid, hmacBytes.size))
        return bytesToHex(sha256Bytes(sha1 + sha2))
    }
}
