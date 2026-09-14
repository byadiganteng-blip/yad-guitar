package com.yad.guitar

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File

/**
 * LogViewerActivity — lihat log langsung di app.
 *
 * Fitur:
 *  - Lihat log real-time
 *  - Refresh
 *  - Share log via WhatsApp/email
 *  - Clear log
 *  - Info folder log
 */
class LogViewerActivity : AppCompatActivity() {

    private lateinit var tvLog: TextView
    private lateinit var tvInfo: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.yad.guitar.R.layout.activity_log_viewer)

        Logger.i("LogViewer", "LogViewerActivity opened")

        tvLog = findViewById(R.id.tvLog)
        tvInfo = findViewById(R.id.tvInfo)

        findViewById<Button>(R.id.btnRefresh).setOnClickListener {
            Logger.d("LogViewer", "Refresh clicked")
            loadLog()
        }

        findViewById<Button>(R.id.btnShare).setOnClickListener {
            Logger.d("LogViewer", "Share clicked")
            shareLog()
        }

        findViewById<Button>(R.id.btnClear).setOnClickListener {
            Logger.d("LogViewer", "Clear clicked")
            confirmClear()
        }

        findViewById<Button>(R.id.btnClose).setOnClickListener {
            finish()
        }

        loadLog()
    }

    private fun loadLog() {
        try {
            val logContent = Logger.readAllLogs()
            tvLog.text = if (logContent.isBlank()) "(log kosong)" else logContent

            // Scroll ke bawah
            findViewById<ScrollView>(R.id.scrollLog).post {
                findViewById<ScrollView>(R.id.scrollLog).fullScroll(ScrollView.FOCUS_DOWN)
            }

            // Info
            val logDir = Logger.getLogDir()
            val files = Logger.getAllLogFiles()
            val totalSize = files.sumOf { it.length() } / 1024
            tvInfo.text = "📁 ${logDir?.absolutePath}\n" +
                    "📄 ${files.size} file (${totalSize} KB)"

            Logger.d("LogViewer", "Log loaded: ${files.size} files, ${totalSize} KB")
        } catch (e: Exception) {
            Logger.e("LogViewer", "loadLog error", e)
            tvLog.text = "Error: ${e.message}"
        }
    }

    private fun shareLog() {
        try {
            val allLogs = Logger.readAllLogs()
            if (allLogs.isBlank()) {
                Toast.makeText(this, "Log kosong", Toast.LENGTH_SHORT).show()
                return
            }

            // Buat file temp untuk share
            val tempFile = File(cacheDir, "yad_guitar_logs.txt")
            tempFile.writeText(allLogs)

            val uri = FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                tempFile
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "YAD Guitar Logs")
                putExtra(Intent.EXTRA_TEXT, allLogs)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(intent, "Share log via..."))
            Logger.i("LogViewer", "Log shared")
        } catch (e: Exception) {
            Logger.e("LogViewer", "shareLog error", e)
            Toast.makeText(this, "Share error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle("Hapus Log?")
            .setMessage("Semua log akan dihapus permanen.")
            .setPositiveButton("Hapus") { _, _ ->
                Logger.clearLogs()
                loadLog()
                Toast.makeText(this, "Log dihapus", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        loadLog()
    }
}
