package com.kafkasl.phonewhisper

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream

class MonoResamplerTest {

    private fun sample(bytes: ByteArray, i: Int) =
        ((bytes[i * 2 + 1].toInt() shl 8) or (bytes[i * 2].toInt() and 0xFF)).toShort().toInt()

    @Test fun `one second of 48 kHz stereo becomes one second of 16 kHz mono`() {
        val out = ByteArrayOutputStream()
        val r = MonoResampler(2, 48000, out)
        repeat(48000) { r.add(1000); r.add(3000) }
        val bytes = out.toByteArray()
        assertEquals(16000 * 2, bytes.size)
        // Left and right average to 2000.
        assertEquals(2000, sample(bytes, 0))
        assertEquals(2000, sample(bytes, 15999))
    }

    @Test fun `16 kHz mono passes through unchanged`() {
        val out = ByteArrayOutputStream()
        val r = MonoResampler(1, 16000, out)
        val input = listOf(0, 1, -1, 32767, -32768, 1234)
        input.forEach { r.add(it) }
        val bytes = out.toByteArray()
        assertEquals(input, input.indices.map { sample(bytes, it) })
    }

    @Test fun `8 kHz is stretched to twice as many samples`() {
        val out = ByteArrayOutputStream()
        val r = MonoResampler(1, 8000, out)
        repeat(8000) { r.add(-500) }
        val bytes = out.toByteArray()
        assertEquals(16000 * 2, bytes.size)
        assertEquals(-500, sample(bytes, 7))
    }

    @Test fun `44100 Hz comes out the right length`() {
        val out = ByteArrayOutputStream()
        val r = MonoResampler(1, 44100, out)
        repeat(44100) { r.add(100) }
        assertEquals(16000.0, out.size() / 2.0, 1.0)
    }
}
