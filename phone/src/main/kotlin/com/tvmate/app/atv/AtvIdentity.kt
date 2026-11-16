package com.tvmate.app.atv

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.math.BigInteger
import java.net.Socket
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509KeyManager
import javax.net.ssl.X509TrustManager
import javax.security.auth.x500.X500Principal

/**
 * This phone's identity for the Android TV Remote v2 protocol: an RSA key + self-signed certificate
 * generated once inside the Android Keystore (the private key never leaves it). The TV remembers the
 * certificate after pairing, so no password is stored.
 */
class AtvIdentity {
    private val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    @Synchronized
    private fun ensureKey() {
        if (ks.containsAlias(ALIAS)) return
        val now = System.currentTimeMillis()
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
            .setKeySize(2048)
            .setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA1, KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512)
            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1, KeyProperties.SIGNATURE_PADDING_RSA_PSS)
            .setCertificateSubject(X500Principal("CN=tvmate"))
            .setCertificateSerialNumber(BigInteger.valueOf(1000))
            .setCertificateNotBefore(Date(now - 60_000))
            .setCertificateNotAfter(Date(now + 10L * 365 * 24 * 60 * 60 * 1000))
            .build()
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore").apply { initialize(spec) }.generateKeyPair()
    }

    val certificate: X509Certificate
        get() { ensureKey(); return ks.getCertificate(ALIAS) as X509Certificate }

    /**
     * TLS socket to the TV. The TV uses a self-signed certificate, so it can't be verified by a CA; the
     * pairing code is what binds the two certificates together (see [AtvPairing]).
     */
    fun openSocket(host: String, port: Int, timeoutMs: Int): SSLSocket {
        ensureKey()
        val key = ks.getKey(ALIAS, null) as PrivateKey
        val chain = arrayOf(certificate)
        val keyManager = object : X509KeyManager {
            override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out java.security.Principal>?, socket: Socket?) = ALIAS
            override fun getClientAliases(keyType: String?, issuers: Array<out java.security.Principal>?) = arrayOf(ALIAS)
            override fun getCertificateChain(alias: String?) = chain
            override fun getPrivateKey(alias: String?) = key
            override fun chooseServerAlias(keyType: String?, issuers: Array<out java.security.Principal>?, socket: Socket?): String? = null
            override fun getServerAliases(keyType: String?, issuers: Array<out java.security.Principal>?): Array<String>? = null
        }
        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val ctx = SSLContext.getInstance("TLS").apply { init(arrayOf(keyManager), arrayOf(trustAll), SecureRandom()) }
        val socket = ctx.socketFactory.createSocket() as SSLSocket
        try {
            socket.connect(java.net.InetSocketAddress(host, port), timeoutMs)
            socket.soTimeout = timeoutMs
            socket.startHandshake()
        } catch (e: Exception) {
            runCatching { socket.close() }
            throw e
        }
        return socket
    }

    private companion object {
        const val ALIAS = "tvmate_remote_v2_client"
    }
}
