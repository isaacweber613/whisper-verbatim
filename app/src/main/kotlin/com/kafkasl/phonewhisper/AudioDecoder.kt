package com.kafkasl.phonewhisper

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteOrder

/** Turns any audio file Android can play into the format recordings use: 16 kHz, 16-bit, mono PCM. */
object AudioDecoder {
    const val RATE = 16000

    /** Throws when the file can't be opened, has no audio, or Android can't decode it. */
    fun decode(context: Context, uri: Uri): ByteArray {
        // Copied into the app's cache first: some apps share a stream that can only be read once,
        // front to back, and the decoder needs to jump around in the file.
        val file = File.createTempFile("shared", null, context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)!!.use { input ->
                file.outputStream().use { input.copyTo(it) }
            }
            return decode(file.path)
        } finally {
            file.delete()
        }
    }

    private fun decode(path: String): ByteArray {
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

            val out = ByteArrayOutputStream()
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
            return out.toByteArray()
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
internal class MonoResampler(private val channels: Int, sourceRate: Int, private val out: ByteArrayOutputStream) {
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
