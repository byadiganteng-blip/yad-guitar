package com.yad.guitar

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * AutoTuneHelper — koreksi pitch sederhana (auto-tune).
 *
 * Algoritma:
 *  1. Baca file audio
 *  2. Deteksi pitch dominan per frame
 *  3. Snapping pitch ke nada terdekat (12-TET)
 *  4. Rekode audio dengan pitch shift
 *
 * Catatan: Ini versi sederhana (bukan seperti Antares).
 *   Untuk hasil profesional, pakai library eksternal.
 */
object AutoTuneHelper {

    private const val TAG = "AutoTune"

    // Frekuensi nada dasar (C4 = 261.63 Hz)
    private val NOTE_FREQS = doubleArrayOf(
        261.63, 277.18, 293.66, 311.13, 329.63, 349.23,
        369.99, 392.00, 415.30, 440.00, 466.16, 493.88
    )
    private val NOTE_NAMES = arrayOf(
        "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"
    )

    /**
     * Snapping frekuensi ke nada terdekat.
     */
    fun snapToNote(freq: Double): Pair<Double, String> {
        if (freq <= 0) return 0.0 to "—"

        // Normalisasi ke oktaf 4
        var f = freq
        var octave = 4
        while (f < 261.63) { f *= 2; octave-- }
        while (f > 493.88) { f /= 2; octave++ }

        var bestDist = Double.MAX_VALUE
        var bestIdx = 0
        for (i in NOTE_FREQS.indices) {
            val dist = kotlin.math.abs(NOTE_FREQS[i] - f)
            if (dist < bestDist) {
                bestDist = dist
                bestIdx = i
            }
        }

        val snapped = NOTE_FREQS[bestIdx] * Math.pow(2.0, (octave - 4).toDouble())
        return snapped to "${NOTE_NAMES[bestIdx]}$octave"
    }

    /**
     * Deteksi pitch dominan dari buffer PCM (autokorelasi sederhana).
     */
    fun detectPitch(buffer: ShortArray, sampleRate: Int): Double {
        // Autokorelasi
        val size = buffer.size
        var bestLag = -1
        var bestCorr = 0.0

        val minLag = sampleRate / 1000  // max 1000 Hz
        val maxLag = sampleRate / 50    // min 50 Hz

        for (lag in minLag until maxLag.coerceAtMost(size / 2)) {
            var corr = 0.0
            for (i in 0 until (size - lag)) {
                corr += buffer[i].toDouble() * buffer[i + lag].toDouble()
            }
            if (corr > bestCorr) {
                bestCorr = corr
                bestLag = lag
            }
        }

        return if (bestLag > 0) sampleRate.toDouble() / bestLag else 0.0
    }

    /**
     * Auto-tune sederhana: baca PCM dari file, deteksi pitch,
     * dan geser pitch ke nada terdekat.
     *
     * PENTING: Versi ini hanya demo — produksi butuh
     * phase vocoder atau PSOLA untuk hasil yang natural.
     */
    fun autoTuneFile(
        inputFile: File,
        outputFile: File,
        onProgress: (Float) -> Unit = {}
    ): Boolean {
        return try {
            // Baca file audio
            val extractor = MediaExtractor()
            extractor.setDataSource(inputFile.absolutePath)

            var audioTrack = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrack = i
                    format = f
                    break
                }
            }

            if (audioTrack == -1 || format == null) {
                Log.e(TAG, "No audio track found")
                return false
            }

            extractor.selectTrack(audioTrack)
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            // Baca semua sample
            val bufferSize = 1024 * 1024
            val inputBuffer = ByteBuffer.allocate(bufferSize)
            val samples = mutableListOf<Short>()

            while (true) {
                inputBuffer.clear()
                val sampleSize = extractor.readSampleData(inputBuffer, 0)
                if (sampleSize < 0) break

                inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
                inputBuffer.position(0)
                val shortBuffer = inputBuffer.asShortBuffer()
                val nShorts = sampleSize / 2
                for (i in 0 until nShorts) {
                    samples.add(shortBuffer.get(i))
                }
                extractor.advance()
            }
            extractor.release()

            Log.d(TAG, "Read ${samples.size} samples, rate=$sampleRate, ch=$channelCount")

            // Proses auto-tune per frame
            val frameSize = 2048
            val hopSize = 1024
            val processed = ShortArray(samples.size)

            var frameIdx = 0
            var i = 0
            val totalFrames = ((samples.size - frameSize) / hopSize).coerceAtLeast(1)

            while (i + frameSize <= samples.size) {
                // Ambil frame
                val frame = ShortArray(frameSize) { samples[i + it] }

                // Deteksi pitch
                val detectedFreq = detectPitch(frame, sampleRate)

                // Geser pitch ke nada terdekat
                var pitchRatio = 1.0
                if (detectedFreq > 50.0) {
                    val (snappedFreq, _) = snapToNote(detectedFreq)
                    if (snappedFreq > 0 && detectedFreq > 0) {
                        pitchRatio = snappedFreq / detectedFreq
                        // Batasi perubahan pitch (max 1 semitone)
                        pitchRatio = pitchRatio.coerceIn(0.94, 1.06)
                    }
                }

                // Simple pitch shift: resample frame (linear interpolation)
                for (j in 0 until frameSize) {
                    val srcPos = j / pitchRatio
                    val srcIdx = srcPos.toInt()
                    if (srcIdx + 1 < frameSize) {
                        val frac = srcPos - srcIdx
                        val sample = (
                            frame[srcIdx] * (1 - frac) +
                            frame[srcIdx + 1] * frac
                        ).toInt().toShort()

                        // Overlap-add
                        val outIdx = i + j
                        if (outIdx < processed.size) {
                            processed[outIdx] = sample
                        }
                    }
                }

                i += hopSize
                frameIdx++

                if (frameIdx % 20 == 0) {
                    onProgress(frameIdx.toFloat() / totalFrames)
                }
            }

            onProgress(1.0f)

            // Tulis output sebagai WAV
            writeWav(outputFile, processed, sampleRate, channelCount)
            Log.d(TAG, "Auto-tune selesai: ${outputFile.absolutePath}")
            true

        } catch (e: Exception) {
            Log.e(TAG, "autoTuneFile error", e)
            false
        }
    }

    /**
     * Tulis WAV file dari ShortArray.
     */
    private fun writeWav(file: File, samples: ShortArray, sampleRate: Int, channels: Int) {
        val dataSize = samples.size * 2
        val totalSize = 36 + dataSize

        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(totalSize)
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16)                      // subchunk size
            putShort(1)                     // PCM
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(sampleRate * channels * 2)  // byte rate
            putShort((channels * 2).toShort())  // block align
            putShort(16)                    // bits per sample
            put("data".toByteArray())
            putInt(dataSize)
        }

        FileOutputStream(file).use { out ->
            out.write(header.array())
            val buf = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
            for (s in samples) buf.putShort(s)
            out.write(buf.array())
        }
    }
}
