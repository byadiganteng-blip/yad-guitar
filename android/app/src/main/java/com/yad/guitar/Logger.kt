package com.yad.guitar

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * Logger — sistem logging lengkap untuk YAD Guitar.
 *
 * Fitur:
 *  - Log ke Logcat + file
 *  - Rotating log (auto-hapus > 7 hari)
 *  - Multi-level: DEBUG, INFO, WARN, ERROR
 *  - Tag per fitur (GUITAR, RECORDER, AUTOTUNE, UI)
 *  - Thread-safe
 *  - Auto-flush
 *  - Crash handler
 */
object Logger {

    private const val TAG = "YadGuitar"
    private const val MAX_LOG_AGE_DAYS = 7
    private const val MAX_LOG_SIZE_MB = 5

    private var logDir: File? = null
    private var currentLogFile: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val fileNameFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val lock = Any()

    // ============================================================
    // INIT — dipanggil dari Application.onCreate()
    // ============================================================
    fun init(context: Context) {
        synchronized(lock) {
            try {
                // Path: /Android/data/com.yad.guitar/files/logs/
                val baseDir = context.getExternalFilesDir(null)
                    ?: context.filesDir
                logDir = File(baseDir, "logs")
                if (!logDir!!.exists()) logDir!!.mkdirs()

                // Buat file log hari ini
                val today = fileNameFormat.format(Date())
                currentLogFile = File(logDir, "guitar_$today.log")

                // Rotate log lama
                cleanOldLogs()

                // Install crash handler
                installCrashHandler(context)

                // Log start
                logToFile("INFO", "YadGuitar", "=" .repeat(60))
                logToFile("INFO", "YadGuitar", "SESSION START")
                logToFile("INFO", "YadGuitar", "=" .repeat(60))
                logToFile("INFO", "YadGuitar", "App: YAD Guitar")
                logToFile("INFO", "YadGuitar", "Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                logToFile("INFO", "YadGuitar", "Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                logToFile("INFO", "YadGuitar", "Log dir: ${logDir!!.absolutePath}")
                logToFile("INFO", "YadGuitar", "=" .repeat(60))
            } catch (e: Exception) {
                Log.e(TAG, "Logger init failed", e)
            }
        }
    }

    // ============================================================
    // LOG METHODS
    // ============================================================
    fun d(tag: String, message: String) {
        Log.d(tag, message)
        logToFile("DEBUG", tag, message)
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        logToFile("INFO", tag, message)
    }

    fun w(tag: String, message: String) {
        Log.w(tag, message)
        logToFile("WARN", tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        var msg = message
        if (throwable != null) {
            msg += "\n" + getStackTrace(throwable)
        }
        logToFile("ERROR", tag, msg)
    }

    fun crash(tag: String, throwable: Throwable) {
        Log.e(tag, "CRASH", throwable)
        logToFile("CRASH", tag, "Uncaught exception:\n" + getStackTrace(throwable))
    }

    // ============================================================
    // FILE WRITER — thread-safe
    // ============================================================
    private fun logToFile(level: String, tag: String, message: String) {
        synchronized(lock) {
            try {
                val file = currentLogFile ?: return

                // Rotate kalau file > MAX_LOG_SIZE_MB
                if (file.exists() && file.length() > MAX_LOG_SIZE_MB * 1024 * 1024) {
                    rotateLogFile()
                }

                val timestamp = dateFormat.format(Date())
                val line = "[$timestamp] [$level] [$tag] $message\n"

                FileWriter(file, true).use { writer ->
                    writer.append(line)
                    writer.flush()
                }
            } catch (e: Exception) {
                Log.e(TAG, "logToFile failed", e)
            }
        }
    }

    private fun rotateLogFile() {
        try {
            val file = currentLogFile ?: return
            val timestamp = System.currentTimeMillis()
            val newName = file.nameWithoutExtension + "_" + timestamp + ".log"
            val newFile = File(logDir, newName)
            file.renameTo(newFile)

            // Buat file baru
            val today = fileNameFormat.format(Date())
            currentLogFile = File(logDir, "guitar_$today.log")
        } catch (e: Exception) {
            Log.e(TAG, "rotateLogFile failed", e)
        }
    }

    // ============================================================
    // CLEAN OLD LOGS
    // ============================================================
    private fun cleanOldLogs() {
        try {
            val dir = logDir ?: return
            val cutoff = System.currentTimeMillis() - (MAX_LOG_AGE_DAYS * 24 * 60 * 60 * 1000L)
            dir.listFiles()?.forEach { file ->
                if (file.isFile && file.lastModified() < cutoff) {
                    file.delete()
                    Log.d(TAG, "Deleted old log: ${file.name}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "cleanOldLogs failed", e)
        }
    }

    // ============================================================
    // CRASH HANDLER
    // ============================================================
    private fun installCrashHandler(context: Context) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                logToFile("CRASH", "UncaughtException",
                    "Thread: ${thread.name}\n" +
                    "Message: ${throwable.message}\n" +
                    getStackTrace(throwable))
            } catch (_: Exception) {}

            // Panggil handler default (biar Android bisa restart app)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun getStackTrace(throwable: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        return sw.toString()
    }

    // ============================================================
    // PUBLIC API
    // ============================================================
    fun getLogDir(): File? = logDir

    fun getCurrentLogFile(): File? = currentLogFile

    fun getAllLogFiles(): List<File> {
        return logDir?.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".log") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    fun readCurrentLog(): String {
        return try {
            currentLogFile?.readText() ?: "(log kosong)"
        } catch (e: Exception) {
            "Error membaca log: ${e.message}"
        }
    }

    fun readAllLogs(): String {
        val sb = StringBuilder()
        getAllLogFiles().forEach { file ->
            sb.append("\n\n===== ${file.name} =====\n")
            try {
                sb.append(file.readText())
            } catch (_: Exception) {}
        }
        return sb.toString()
    }

    fun clearLogs() {
        synchronized(lock) {
            try {
                logDir?.listFiles()?.forEach { it.delete() }
                val today = fileNameFormat.format(Date())
                currentLogFile = File(logDir, "guitar_$today.log")
                i("Logger", "Logs cleared")
            } catch (e: Exception) {
                Log.e(TAG, "clearLogs failed", e)
            }
        }
    }

    // ============================================================
    // TRACKING METHODS — detail per kategori
    // ============================================================
    
    /**
     * Track UI event — tombol ditekan, activity dibuka, dll.
     */
    fun uiEvent(activity: String, action: String, detail: String = "") {
        val msg = if (detail.isNotEmpty()) "UI | $activity | $action | $detail"
                  else "UI | $activity | $action"
        i("TRACK_UI", msg)
    }
    
    /**
     * Track audio event — petik senar, strum, dll.
     */
    fun audioEvent(action: String, detail: String = "") {
        val msg = if (detail.isNotEmpty()) "AUDIO | $action | $detail"
                  else "AUDIO | $action"
        i("TRACK_AUDIO", msg)
    }
    
    /**
     * Track recording event — rekam, stop, play, dll.
     */
    fun recEvent(action: String, detail: String = "") {
        val msg = if (detail.isNotEmpty()) "REC | $action | $detail"
                  else "REC | $action"
        i("TRACK_REC", msg)
    }
    
    /**
     * Track system event — lifecycle, memory, dll.
     */
    fun sysEvent(action: String, detail: String = "") {
        val msg = if (detail.isNotEmpty()) "SYS | $action | $detail"
                  else "SYS | $action"
        i("TRACK_SYS", msg)
    }
    
    /**
     * Track error dengan context.
     */
    fun trackError(activity: String, action: String, e: Throwable) {
        e("TRACK_ERR", "$activity | $action | ${e.message}", e)
    }
    
    /**
     * Track button press dengan timing.
     */
    fun buttonPress(activity: String, buttonName: String) {
        i("BTN_PRESS", "$activity | $buttonName | ${System.currentTimeMillis()}")
    }
    
    /**
     * Track method entry — untuk debug flow.
     */
    fun methodEntry(tag: String, methodName: String) {
        d(tag, "→ ENTER: $methodName")
    }
    
    /**
     * Track method exit — untuk debug flow.
     */
    fun methodExit(tag: String, methodName: String) {
        d(tag, "← EXIT: $methodName")
    }
}
