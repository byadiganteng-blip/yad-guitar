package com.yad.guitar

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * AutoTuneHelper — koreksi pitch sederhana (auto-tune).
 *
 * FIX v2: Decode AAC → PCM via MediaCodec (bukan baca raw bytes).
 */
object AutoTuneHelper {

    private const val TAG = "AutoTune"

    private val NOTE_FREQS = doubleArrayOf(
        261.63, 277.18, 293.66, 311.13, 329.63, 349.23,
        369.99, 392.00, 415.30, 440.00, 466.16, 493.88
    )
    private val NOTE_NAMES = arrayOf(
        "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"
    )

    fun snapToNote(freq: Double): Pair<Double, String> {
        if (freq <= 0) return 0.0 to "—"

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

    fun detectPitch(buffer: ShortArray, sampleRate: Int): Double {
        val size = buffer.size
        var bestLag = -1
        var bestCorr = 0.0

        val minLag = sampleRate / 1000
        val maxLag = sampleRate / 50

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
     * FIX v2: Decode AAC → PCM via MediaCodec.
     */
    private fun decodeToPcm(
        extractor: MediaExtractor,
        format: MediaFormat,
        onProgress: (Float) -> Unit
    ): Pair<ShortArray, Int>? {
        val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
        val decoder = try {
            MediaCodec.createDecoderByType(mime)
        } catch (e: Exception) {
            Log.e(TAG, "createDecoderByType failed: $mime", e)
            return null
        }

        try {
            decoder.configure(format, null, null, 0)
            decoder.start()
        } catch (e: Exception) {
            Log.e(TAG, "decoder configure/start failed", e)
            decoder.release()
            return null
        }

        val samples = mutableListOf<Short>()
        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val durationUs = try {
            format.getLong(MediaFormat.KEY_DURATION)
        } catch (e: Exception) { 1L }.coerceAtLeast(1L)

        var safetyCounter = 0
        val maxIterations = 100000  // prevent infinite loop

        while (!outputDone && safetyCounter < maxIterations) {
            safetyCounter++

            // Feed input
            if (!inputDone) {
                val inIdx = decoder.dequeueInputBuffer(10_000)
                if (inIdx >= 0) {
                    val inBuf = decoder.getInputBuffer(inIdx)
                    if (inBuf != null) {
                        val size = extractor.readSampleData(inBuf, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inIdx, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inIdx, 0, size,
                                extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
            }

            // Drain output
            val outIdx = decoder.dequeueOutputBuffer(bufferInfo, 10_000)
            if (outIdx >= 0) {
                if (bufferInfo.size > 0) {
                    val outBuf = decoder.getOutputBuffer(outIdx)
                    if (outBuf != null) {
                        outBuf.position(bufferInfo.offset)
                        outBuf.limit(bufferInfo.offset + bufferInfo.size)
                        val shorts = outBuf.asShortBuffer()
                        val arr = ShortArray(shorts.remaining())
                        shorts.get(arr)
                        for (s in arr) samples.add(s)

                        if (bufferInfo.presentationTimeUs > 0) {
                            onProgress(
                                (bufferInfo.presentationTimeUs.toFloat() / durationUs)
                                    .coerceIn(0f, 1f)
                            )
                        }
                    }
                }
                decoder.releaseOutputBuffer(outIdx, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    outputDone = true
                }
            } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                Log.d(TAG, "Decoder output format changed")
            }
        }

        try {
            decoder.stop()
        } catch (_: Exception) {}
        decoder.release()

        Log.d(TAG, "Decoded ${samples.size} PCM samples, rate=$sampleRate")
        return samples.toShortArray() to sampleRate
    }

    fun autoTuneFile(
        inputFile: File,
        outputFile: File,
        onProgress: (Float) -> Unit = {}
    ): Boolean {
        return try {
            if (!inputFile.exists() || inputFile.length() < 1024) {
                Log.e(TAG, "Input file invalid: exists=${inputFile.exists()} size=${inputFile.length()}")
                return false
            }

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
                extractor.release()
                return false
            }

            extractor.selectTrack(audioTrack)
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            // FIX: decode AAC → PCM
            val decoded = decodeToPcm(extractor, format, onProgress)
            extractor.release()

            if (decoded == null || decoded.first.isEmpty()) {
                Log.e(TAG, "Decode failed or empty")
                return false
            }

            val (samples, _) = decoded
            Log.d(TAG, "Read ${samples.size} samples, rate=$sampleRate, ch=$channelCount")

            // Proses auto-tune per frame
            val frameSize = 2048
            val hopSize = 1024
            val processed = ShortArray(samples.size)

            var frameIdx = 0
            var i = 0
            val totalFrames = ((samples.size - frameSize) / hopSize).coerceAtLeast(1)

            while (i + frameSize <= samples.size) {
                val frame = ShortArray(frameSize) { samples[i + it] }
                val detectedFreq = detectPitch(frame, sampleRate)

                var pitchRatio = 1.0
                if (detectedFreq > 50.0) {
                    val (snappedFreq, _) = snapToNote(detectedFreq)
                    if (snappedFreq > 0 && detectedFreq > 0) {
                        pitchRatio = snappedFreq / detectedFreq
                        pitchRatio = pitchRatio.coerceIn(0.94, 1.06)
                    }
                }

                for (j in 0 until frameSize) {
                    val srcPos = j / pitchRatio
                    val srcIdx = srcPos.toInt()
                    if (srcIdx + 1 < frameSize) {
                        val frac = srcPos - srcIdx
                        val sample = (
                            frame[srcIdx] * (1 - frac) +
                            frame[srcIdx + 1] * frac
                        ).toInt().toShort()

                        val outIdx = i + j
                        if (outIdx < processed.size) {
                            processed[outIdx] = sample
                        }
                    }
                }

                i += hopSize
                frameIdx++

                if (frameIdx % 20 == 0) {
                    onProgress(0.5f + (frameIdx.toFloat() / totalFrames) * 0.5f)
                }
            }

            onProgress(1.0f)

            writeWav(outputFile, processed, sampleRate, channelCount)
            Log.d(TAG, "Auto-tune selesai: ${outputFile.absolutePath}")
            true

        } catch (e: Exception) {
            Log.e(TAG, "autoTuneFile error", e)
            false
        }
    }

    private fun writeWav(file: File, samples: ShortArray, sampleRate: Int, channels: Int) {
        val dataSize = samples.size * 2
        val totalSize = 36 + dataSize

        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(totalSize)
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16)
            putShort(1)
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(sampleRate * channels * 2)
            putShort((channels * 2).toShort())
            putShort(16)
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
