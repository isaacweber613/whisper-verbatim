package com.kafkasl.phonewhisper

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile

class AudioSplitterTest {
    private val second = 32000

    /** [seconds] of loud audio (every sample 8000) with a silent stretch from [quietFrom] to [quietTo] seconds. */
    private fun audio(seconds: Int, quietFrom: Double = -1.0, quietTo: Double = -1.0): ByteArray {
        val pcm = ByteArray(seconds * second)
        for (i in pcm.indices step 2) {
            val t = i.toDouble() / second
            val s = if (t >= quietFrom && t < quietTo) 0 else 8000
            pcm[i] = (s and 0xFF).toByte()
            pcm[i + 1] = (s shr 8).toByte()
        }
        return pcm
    }

    @Test fun `finds the quiet stretch`() {
        val cut = AudioSplitter.quietest(audio(30, 12.0, 13.0))
        assertTrue(cut in 12 * second..13 * second)
        assertEquals(0, cut % 2)
    }

    @Test fun `a short file is one piece`() {
        val file = File.createTempFile("pcm", null)
        try {
            file.writeBytes(audio(40))
            RandomAccessFile(file, "r").use { assertEquals(40 * second, AudioSplitter.pieceSize(it, 0)) }
        } finally { file.delete() }
    }

    @Test fun `a long file is cut at the quiet stretch near five minutes`() {
        val file = File.createTempFile("pcm", null)
        try {
            // 7 minutes, silent from 4:40 to 4:41.
            file.writeBytes(audio(7 * 60, 280.0, 281.0))
            RandomAccessFile(file, "r").use { f ->
                val first = AudioSplitter.pieceSize(f, 0)
                assertTrue(first in 280 * second..281 * second)
                // What is left (about 2:20) is the last piece.
                assertEquals(7 * 60 * second - first, AudioSplitter.pieceSize(f, first.toLong()))
            }
        } finally { file.delete() }
    }
}
