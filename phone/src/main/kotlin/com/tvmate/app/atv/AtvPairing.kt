package com.tvmate.app.atv

import android.os.Build
import java.io.Closeable
import java.io.IOException
import java.math.BigInteger
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import javax.net.ssl.SSLSocket

open class AtvException(message: String) : Exception(message)

/** The typed code doesn't match; the session is still usable, so the user can try again. */
class AtvWrongCode(message: String) : AtvException(message)

/**
 * One pairing conversation with an Android TV (port 6467). The TV shows a 6-character hex code; the
 * code only works if it matches a hash of both certificates, which is what stops anyone else on the
 * network from pairing in the middle.
 *
 * Call [begin], let the user read the code from the TV, then [finish].
 */
class AtvPairing private constructor(
    private val socket: SSLSocket,
    private val identity: AtvIdentity,
) : Closeable {

    private val input = socket.inputStream
    private val output = socket.outputStream

    private fun outer(build: PbWriter.() -> Unit): ByteArray =
        PbWriter().int(1, 2).int(2, STATUS_OK).apply(build).toFrame()

    private fun send(frame: ByteArray) { output.write(frame); output.flush() }

    private fun receive(): List<PbField> {
        val frame = try { PbReader.readFrame(input) } catch (e: IOException) { null }
            ?: throw AtvException("The TV stopped answering during pairing.")
        val fields = PbReader.parse(frame)
        val status = fields.firstOrNull { it.number == 2 }?.long ?: STATUS_OK.toLong()
        if (status != STATUS_OK.toLong()) throw AtvException("The TV refused the pairing (status $status).")
        return fields
    }

    private fun expect(field: Int): PbField =
        receive().firstOrNull { it.number == field } ?: throw AtvException("Unexpected answer from the TV while pairing.")

    /** Runs the handshake up to the point where the TV displays its code. */
    fun begin() {
        send(outer { message(10) { string(1, "atvremote"); string(2, "TVMate (${Build.MODEL})") } })
        expect(11) // pairing_request_ack
        send(outer {
            message(20) {
                message(1) { int(1, ENCODING_HEX); int(2, CODE_LENGTH) }
                int(3, ROLE_INPUT)
            }
        })
        expect(20) // options from the TV
        send(outer {
            message(30) {
                message(1) { int(1, ENCODING_HEX); int(2, CODE_LENGTH) }
                int(2, ROLE_INPUT)
            }
        })
        expect(31) // configuration_ack: the TV now shows the code
    }

    /** Sends the secret derived from [code]. Throws [AtvException] if the code is wrong. */
    fun finish(code: String) {
        val clean = code.trim().uppercase()
        if (clean.length != CODE_LENGTH || !clean.all { it in "0123456789ABCDEF" }) {
            throw AtvWrongCode("The code is 6 characters: digits 0–9 and letters A–F.")
        }
        val clientKey = identity.certificate.publicKey as RSAPublicKey
        val serverCert = socket.session.peerCertificates.firstOrNull() as? X509Certificate
            ?: throw AtvException("The TV didn’t present a certificate.")
        val serverKey = serverCert.publicKey as RSAPublicKey

        val hash = MessageDigest.getInstance("SHA-256").apply {
            update(clientKey.modulus.unsigned())
            update(clientKey.publicExponent.unsigned())
            update(serverKey.modulus.unsigned())
            update(serverKey.publicExponent.unsigned())
            update(clean.substring(2).chunked(2).map { it.toInt(16).toByte() }.toByteArray())
        }.digest()

        if ((hash[0].toInt() and 0xFF) != clean.substring(0, 2).toInt(16)) {
            throw AtvWrongCode("That code isn’t right. Check the code on your TV and try again.")
        }
        send(outer { message(40) { bytes(1, hash) } })
        try {
            expect(41) // secret_ack
        } catch (e: AtvException) {
            throw AtvException("The TV didn’t accept the code. Start pairing again.")
        }
    }

    override fun close() { runCatching { socket.close() } }

    private fun BigInteger.unsigned(): ByteArray =
        toByteArray().let { if (it.size > 1 && it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it }

    companion object {
        private const val STATUS_OK = 200
        private const val ENCODING_HEX = 3
        private const val ROLE_INPUT = 1
        private const val CODE_LENGTH = 6
        const val PORT = 6467

        /** Connects to the TV's pairing port. Throws [AtvException] with a user-readable message. */
        fun connect(host: String, identity: AtvIdentity): AtvPairing {
            val socket = try {
                identity.openSocket(host, PORT, 10_000)
            } catch (e: Exception) {
                throw AtvException("Couldn’t reach the TV’s remote service. Make sure the TV is on and on the same Wi-Fi.")
            }
            return AtvPairing(socket, identity)
        }
    }
}
