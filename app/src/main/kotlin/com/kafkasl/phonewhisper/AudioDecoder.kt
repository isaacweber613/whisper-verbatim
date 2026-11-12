package com.kafkasl.phonewhisper

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteOrder

/** Turns any audio file Android can play into the format recordings use: 16 kHz, 16-bit, mono PCM. */
object AudioDecoder {
    const val RATE = 16000

    /**
     * Writes the audio to [pcm], a file and not memory, because an hour of audio is over 100 MB.
     * Throws when the shared file can't be opened, has no audio, or Android can't decode it.
     */
    fun decode(context: Context, uri: Uri, pcm: File) {
        // Copied into the app's cache first: some apps share a stream that can only be read once,
        // front to back, and the decoder needs to jump around in the file.
        val file = File.createTempFile("shared", null, context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)!!.use { input ->
                file.outputStream().use { input.copyTo(it) }
            }
            pcm.outputStream().buffered().use { decode(file.path, it) }
        } finally {
            file.delete()
        }
    }

    private fun decode(path: String, out: OutputStream) {
        val extractor = MediaExtractor()
        var started: MediaCodec? = null
        try {
            extractor.setDataSource(path)
            val track = (0 until extractor.trackCount).first {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            }
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!).also { started = it }
            codec.configure(format, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            var resampler: MonoResampler? = null
            var float = false
            var inputDone = false
            while (true) {
                if (!inputDone) {
                    val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val n = extractor.readSampleData(codec.getInputBuffer(i)!!, 0)
                        if (n < 0) {
                            codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(i, 0, n, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) resampler = null
                if (o < 0) continue
                val r = resampler ?: codec.outputFormat.let { f ->
                    float = Build.VERSION.SDK_INT >= 24 && f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    MonoResampler(f.getInteger(MediaFormat.KEY_CHANNEL_COUNT), f.getInteger(MediaFormat.KEY_SAMPLE_RATE), out)
                        .also { resampler = it }
                }
                val buf = codec.getOutputBuffer(o)!!.order(ByteOrder.nativeOrder())
                buf.limit(info.offset + info.size)
                buf.position(info.offset)
                if (float) {
                    val samples = buf.asFloatBuffer()
                    while (samples.hasRemaining()) r.add((samples.get().coerceIn(-1f, 1f) * 32767).toInt())
                } else {
                    val samples = buf.asShortBuffer()
                    while (samples.hasRemaining()) r.add(samples.get().toInt())
                }
                codec.releaseOutputBuffer(o, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            }
        } finally {
            try { started?.stop() } catch (_: Exception) {}
            started?.release()
            extractor.release()
        }
    }
}

/**
 * Takes 16-bit samples as a decoder gives them (left, right, left, right, ...), mixes the
 * channels to one and writes 16 kHz samples to [out]. A faster file is slowed by averaging the
 * samples each new one covers; a slower file repeats samples.
 */
internal class MonoResampler(private val channels: Int, sourceRate: Int, private val out: OutputStream) {
    private val step = AudioDecoder.RATE.toDouble() / sourceRate
    private var phase = 0.0
    private var sum = 0L
    private var count = 0
    private var frame = 0
    private var frameFill = 0

    fun add(sample: Int) {
        frame += sample
        if (++frameFill < channels) return
        sum += frame / channels
        count++
        frame = 0
        frameFill = 0
        phase += step
        if (phase < 1.0) return
        val v = (sum / count).toInt()
        while (phase >= 1.0) {
            out.write(v and 0xFF)
            out.write(v shr 8 and 0xFF)
            phase -= 1.0
        }
        sum = 0
        count = 0
    }
}

/**
 * Cuts a long recording into pieces the speech service accepts (Isaac, 2026-11-11). OpenRouter
 * rejects a request over 25 MB, about 13 minutes of this audio, and its providers stop after 60
 * seconds of work on one request. The phone also holds a piece in memory several times over
 * while sending it. So a piece is about 5 minutes, and it ends at the quietest moment of its
 * last 30 seconds so a word isn't cut in half.
 */
object AudioSplitter {
    private const val SECOND = AudioDecoder.RATE * 2
    private const val PIECE = 5 * 60 * SECOND
    private const val SEARCH = 30 * SECOND
    private const val FRAME = SECOND / 5

    /** How many bytes of [pcm] the piece starting at [start] takes. The last piece can run 30 seconds over. */
    fun pieceSize(pcm: RandomAccessFile, start: Long): Int {
        val remaining = pcm.length() - start
        if (remaining <= PIECE + SEARCH) return remaining.toInt()
        val window = ByteArray(SEARCH)
        pcm.seek(start + PIECE - SEARCH)
        pcm.readFully(window)
        return PIECE - SEARCH + quietest(window)
    }

    /** The middle of the quietest fifth of a second in [pcm], as a byte offset on a sample boundary. */
    internal fun quietest(pcm: ByteArray): Int {
        var best = 0
        var bestEnergy = Long.MAX_VALUE
        var at = 0
        while (at + FRAME <= pcm.size) {
            var energy = 0L
            for (i in at until at + FRAME step 2) {
                val s = ((pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xFF)).toLong()
                energy += s * s
            }
            if (energy < bestEnergy) {
                bestEnergy = energy
                best = at
            }
            at += FRAME
        }
        return best + FRAME / 2
    }
}
