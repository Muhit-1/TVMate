package com.tvmate.app.atv

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Just enough protobuf to speak the Android TV Remote v2 protocol (no code generation needed). */
internal class PbWriter {
    private val out = ByteArrayOutputStream()

    private fun varint(value: Long) {
        var v = value
        while (v and 0x7FL.inv() != 0L) {
            out.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
        out.write(v.toInt())
    }

    fun int(field: Int, value: Long): PbWriter { varint((field shl 3).toLong()); varint(value); return this }
    fun int(field: Int, value: Int) = int(field, value.toLong())
    fun bytes(field: Int, value: ByteArray): PbWriter { varint(((field shl 3) or 2).toLong()); varint(value.size.toLong()); out.write(value); return this }
    fun string(field: Int, value: String) = bytes(field, value.toByteArray(Charsets.UTF_8))
    fun message(field: Int, build: PbWriter.() -> Unit) = bytes(field, PbWriter().apply(build).toByteArray())
    fun toByteArray(): ByteArray = out.toByteArray()

    /** Wire format used on the socket: a varint length followed by the message. */
    fun toFrame(): ByteArray {
        val body = toByteArray()
        val framed = PbWriter()
        framed.varint(body.size.toLong())
        return framed.out.toByteArray() + body
    }
}

internal class PbField(val number: Int, val wireType: Int, val long: Long, val bytes: ByteArray)

internal class PbReader(private val data: ByteArray) {
    private var pos = 0

    private fun varint(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            val b = data[pos++].toInt()
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
            require(shift < 64) { "bad varint" }
        }
    }

    fun fields(): List<PbField> {
        val list = ArrayList<PbField>()
        while (pos < data.size) {
            val tag = varint().toInt()
            val number = tag ushr 3
            when (val wire = tag and 7) {
                0 -> list += PbField(number, wire, varint(), ByteArray(0))
                2 -> {
                    val len = varint().toInt()
                    list += PbField(number, wire, 0, data.copyOfRange(pos, pos + len))
                    pos += len
                }
                1 -> { pos += 8; list += PbField(number, wire, 0, ByteArray(0)) }
                5 -> { pos += 4; list += PbField(number, wire, 0, ByteArray(0)) }
                else -> error("unsupported wire type $wire")
            }
        }
        return list
    }

    companion object {
        fun parse(bytes: ByteArray) = PbReader(bytes).fields()

        /** Reads one length-prefixed message from the stream. Returns null at end of stream. */
        fun readFrame(input: InputStream): ByteArray? {
            var len = 0L
            var shift = 0
            while (true) {
                val b = input.read()
                if (b < 0) return null
                len = len or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) break
                shift += 7
                require(shift < 35) { "bad frame length" }
            }
            require(len in 0..1_000_000) { "frame too large" }
            val buf = ByteArray(len.toInt())
            var read = 0
            while (read < buf.size) {
                val n = input.read(buf, read, buf.size - read)
                if (n < 0) return null
                read += n
            }
            return buf
        }
    }
}
