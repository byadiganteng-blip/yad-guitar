package com.yad.guitar

import android.content.ContentValues
import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileInputStream

/**
 * AudioRecorderHelper — rekam suara, putar, simpan ke galeri.
 */
object AudioRecorderHelper {

    private const val TAG = "AudioRecorder"
    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var outputFile: File? = null

    /**
     * Mulai rekam. Return: file output, atau null kalau gagal.
     */
    fun startRecording(context: Context): File? {
        stopRecording()
        stopPlayback()

        try {
            val dir = File(context.getExternalFilesDir(null), "recordings")
            if (!dir.exists()) dir.mkdirs()

            val file = File(dir, "rec_${System.currentTimeMillis()}.m4a")
            outputFile = file

            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mr.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            recorder = mr
            Log.d(TAG, "Recording started: ${file.absolutePath}")
            return file
        } catch (e: Exception) {
            Log.e(TAG, "startRecording error", e)
            return null
        }
    }

    /**
     * Stop rekam. Return: file hasil, atau null kalau tidak ada.
     */
    fun stopRecording(): File? {
        val file = outputFile
        try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            outputFile = null
            Log.d(TAG, "Recording stopped: ${file?.absolutePath}")
            return file
        } catch (e: Exception) {
            Log.e(TAG, "stopRecording error", e)
            recorder?.release()
            recorder = null
            outputFile = null
            return null
        }
    }

    fun isRecording(): Boolean = recorder != null

    /**
     * Putar file audio.
     */
    fun play(file: File, onComplete: () -> Unit = {}) {
        stopPlayback()
        try {
            val mp = MediaPlayer()
            mp.setDataSource(file.absolutePath)
            mp.setOnCompletionListener {
                it.release()
                player = null
                onComplete()
            }
            mp.setOnErrorListener { _, _, _ ->
                stopPlayback()
                onComplete()
                true
            }
            mp.prepare()
            mp.start()
            player = mp
        } catch (e: Exception) {
            Log.e(TAG, "play error", e)
            stopPlayback()
            onComplete()
        }
    }

    fun stopPlayback() {
        try {
            player?.apply {
                if (isPlaying) stop()
                release()
            }
            player = null
        } catch (_: Exception) {}
    }

    fun isPlaying(): Boolean = try {
        player?.isPlaying == true
    } catch (_: Exception) { false }

    /**
     * Simpan file ke galeri (MediaStore).
     */
    fun saveToGallery(context: Context, file: File): Boolean {
        return try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, "audio/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_MUSIC + "/YadGuitar")
                }
            }

            val uri = context.contentResolver.insert(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values
            ) ?: return false

            context.contentResolver.openOutputStream(uri)?.use { out ->
                FileInputStream(file).use { input ->
                    input.copyTo(out)
                }
            }
            Log.d(TAG, "Saved to gallery: $uri")
            true
        } catch (e: Exception) {
            Log.e(TAG, "saveToGallery error", e)
            false
        }
    }

    /**
     * Get list recording files.
     */
    fun listRecordings(context: Context): List<File> {
        val dir = File(context.getExternalFilesDir(null), "recordings")
        if (!dir.exists()) return emptyList()
        return dir.listFiles()
            ?.filter { it.extension == "m4a" }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    fun getLatestRecording(context: Context): File? {
        return listRecordings(context).firstOrNull()
    }
}
