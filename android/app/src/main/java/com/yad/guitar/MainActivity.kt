package com.yad.guitar

import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Button
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    // ============================================================
    //  STRUMMING PATTERN dari wikiHow
    // ============================================================
    private var currentWikiPattern: StrummingPattern.Pattern? = null
    private var currentPatternIndex = 0

    /**
     * Ganti pattern sesuai wikiHow.
     */
    private fun nextWikiPattern() {
        currentPatternIndex = (currentPatternIndex + 1) % StrummingPattern.ALL_PATTERNS.size
        currentWikiPattern = StrummingPattern.ALL_PATTERNS[currentPatternIndex]
        Toast.makeText(
            this,
            "Pattern: ${currentWikiPattern?.name}\n${currentWikiPattern?.description}",
            Toast.LENGTH_SHORT
        ).show()
        Logger.i("MainActivity", "Wiki pattern changed: ${currentWikiPattern?.name}")
    }

    /**
     * Mainkan pattern wikiHow (versi update).
     */
    private fun playWikiPattern() {
        val pattern = currentWikiPattern ?: return
        if (!isAutoStrumming) return

        val bpm = pattern.bpm
        val beatMs = 60000L / bpm
        val noteMs = (beatMs * pattern.noteType.beatsPerNote).toLong()

        for (stroke in pattern.strokes) {
            val delay = (stroke.beat * beatMs).toLong()
            
            handler.postDelayed({
                if (!isAutoStrumming) return@postDelayed
                
                when (stroke.type) {
                    StrummingPattern.StrokeType.DOWN -> {
                        // Strum dari bass ke treble
                        val chord = currentChord
                        for (i in 0 until 6) {
                            val fret = chord?.frets?.get(i) ?: 0
                            if (fret == -1) continue
                            val vol = stroke.volume * (if (i < 3) 1.0f else 0.9f)
                            val finalI = i
                            handler.postDelayed({
                                if (isAutoStrumming) playString(finalI, vol)
                            }, (i * 15).toLong())
                        }
                    }
                    StrummingPattern.StrokeType.UP -> {
                        // Strum dari treble ke bass
                        val chord = currentChord
                        for (i in 5 downTo 0) {
                            val fret = chord?.frets?.get(i) ?: 0
                            if (fret == -1) continue
                            val vol = stroke.volume * (if (i < 3) 1.0f else 0.9f)
                            val finalI = i
                            handler.postDelayed({
                                if (isAutoStrumming) playString(finalI, vol)
                            }, ((5 - i) * 15).toLong())
                        }
                    }
                    StrummingPattern.StrokeType.DOWN_MUTED -> {
                        // Strum dengan volume rendah (palm-mute effect)
                        val chord = currentChord
                        for (i in 0 until 6) {
                            val fret = chord?.frets?.get(i) ?: 0
                            if (fret == -1) continue
                            val vol = stroke.volume * 0.4f  // volume rendah = palm-mute
                            val finalI = i
                            handler.postDelayed({
                                if (isAutoStrumming) playString(finalI, vol)
                            }, (i * 15).toLong())
                        }
                    }
                    StrummingPattern.StrokeType.UP_MUTED -> {
                        val chord = currentChord
                        for (i in 5 downTo 0) {
                            val fret = chord?.frets?.get(i) ?: 0
                            if (fret == -1) continue
                            val vol = stroke.volume * 0.4f
                            val finalI = i
                            handler.postDelayed({
                                if (isAutoStrumming) playString(finalI, vol)
                            }, ((5 - i) * 15).toLong())
                        }
                    }
                    StrummingPattern.StrokeType.REST -> {
                        // Diam, tidak mainkan apa-apa
                    }
                }
            }, delay)
        }

        // Ulang pattern
        val patternDuration = (pattern.beatsPerBar * beatMs).toLong() + 100L
        handler.postDelayed({
            if (isAutoStrumming) playWikiPattern()
        }, patternDuration)
    }



    companion object {
        private const val REQ_RECORD_AUDIO = 1001
    }

    // Recording state
    private var isRecording = false
    private var lastRecording: File? = null
    private var isPlayingRecording = false
    private var tvRecStatus: TextView? = null
    private var btnRecord: Button? = null
    private var btnAutoTune: Button? = null

    private lateinit var guitarView: GuitarView
    private lateinit var tvChordInfo: TextView
    private lateinit var btnAutoStrum: Button
    private lateinit var soundPool: SoundPool
    private lateinit var vibrator: Vibrator

    private val stringSoundIds = IntArray(6)
    private var soundLoaded = false
    private var currentChord: Chord? = null

    // ============================================================
    // AUTO STRUM STATE
    // ============================================================
    private val handler = Handler(Looper.getMainLooper())
    private var isAutoStrumming = false
    private var currentPattern = 0
    private var currentTempoMs = 500L   // 120 BPM default
    private var patternStep = 0

    // ============================================================
    // STRUMMING PATTERNS PROFESIONAL
    // Setiap pattern: array of (stringIdx, delayMs, volume)
    // stringIdx: 0-5 (0 = senar 6/bass, 5 = senar 1/treble)
    // volume: 0.0-1.0 (dinamika)
    // ============================================================
    private val patterns = listOf(
        // Pattern 1: Down-Up sederhana (dasar)
        Pattern("Basic Down-Up", 500L, listOf(
            Stroke(5, 0, 1.0f), Stroke(4, 0, 0.9f), Stroke(3, 0, 0.8f),
            Stroke(2, 0, 0.8f), Stroke(1, 0, 0.9f), Stroke(0, 0, 1.0f),
            Stroke(0, 250, 0.7f), Stroke(1, 250, 0.7f), Stroke(2, 250, 0.7f),
            Stroke(3, 250, 0.7f), Stroke(4, 250, 0.7f), Stroke(5, 250, 0.7f),
        )),

        // Pattern 2: D-DU-UDU (paling umum)
        Pattern("D-DU-UDU", 500L, listOf(
            Stroke(5, 0, 1.0f), Stroke(4, 0, 0.9f), Stroke(3, 0, 0.85f),
            Stroke(2, 0, 0.85f), Stroke(1, 0, 0.9f), Stroke(0, 0, 1.0f),

            Stroke(5, 125, 0.6f), Stroke(4, 125, 0.6f),
            Stroke(5, 250, 0.9f), Stroke(4, 250, 0.85f), Stroke(3, 250, 0.8f),
            Stroke(2, 250, 0.8f), Stroke(1, 250, 0.85f), Stroke(0, 250, 0.9f),

            Stroke(0, 375, 0.5f), Stroke(1, 375, 0.5f), Stroke(2, 375, 0.5f),
            Stroke(3, 375, 0.5f), Stroke(4, 375, 0.5f), Stroke(5, 375, 0.5f),

            Stroke(5, 500, 0.9f), Stroke(4, 500, 0.85f), Stroke(3, 500, 0.8f),
            Stroke(2, 500, 0.8f), Stroke(1, 500, 0.85f), Stroke(0, 500, 0.9f),
        )),

        // Pattern 3: Bass + Arpeggio (fingerstyle)
        Pattern("Bass Arpeggio", 600L, listOf(
            Stroke(5, 0, 1.0f),       // Petik bass dulu
            Stroke(3, 150, 0.8f),
            Stroke(2, 300, 0.7f),
            Stroke(1, 450, 0.8f),
            Stroke(2, 600, 0.7f),
            Stroke(3, 750, 0.8f),
            Stroke(4, 900, 0.7f),
            Stroke(3, 1050, 0.75f),
            Stroke(2, 1200, 0.8f),
        )),

        // Pattern 4: Folk strum (campuran)
        Pattern("Folk Strum", 550L, listOf(
            Stroke(5, 0, 1.0f), Stroke(4, 0, 0.95f), Stroke(3, 0, 0.9f),
            Stroke(2, 0, 0.9f), Stroke(1, 0, 0.95f), Stroke(0, 0, 1.0f),

            Stroke(0, 275, 0.6f), Stroke(1, 275, 0.55f),

            Stroke(2, 550, 0.9f), Stroke(3, 550, 0.85f), Stroke(4, 550, 0.85f),
            Stroke(5, 550, 0.9f),

            Stroke(5, 825, 0.5f), Stroke(4, 825, 0.5f),
            Stroke(3, 825, 0.5f), Stroke(2, 825, 0.5f),

            Stroke(1, 1100, 0.85f), Stroke(2, 1100, 0.8f), Stroke(3, 1100, 0.8f),
            Stroke(4, 1100, 0.85f), Stroke(5, 1100, 0.9f),
        )),

        // Pattern 5: Reggae / Ska (aksen di off-beat)
        Pattern("Reggae Ska", 450L, listOf(
            // Diam di beat 1
            Stroke(0, 225, 0.4f), Stroke(1, 225, 0.4f), Stroke(2, 225, 0.5f),
            Stroke(3, 225, 0.5f), Stroke(4, 225, 0.4f), Stroke(5, 225, 0.4f),

            Stroke(5, 450, 1.0f), Stroke(4, 450, 0.95f), Stroke(3, 450, 0.9f),
            Stroke(2, 450, 0.9f), Stroke(1, 450, 0.95f), Stroke(0, 450, 1.0f),

            Stroke(0, 675, 0.5f), Stroke(1, 675, 0.5f),
            Stroke(2, 675, 0.5f), Stroke(3, 675, 0.5f),

            Stroke(5, 900, 1.0f), Stroke(4, 900, 0.95f), Stroke(3, 900, 0.9f),
            Stroke(2, 900, 0.9f), Stroke(1, 900, 0.95f), Stroke(0, 900, 1.0f),
        )),

        // Pattern 6: Ballad (lambat, penuh perasaan)
        Pattern("Ballad", 800L, listOf(
            Stroke(5, 0, 1.0f),
            Stroke(4, 200, 0.8f),
            Stroke(3, 400, 0.75f),
            Stroke(2, 600, 0.75f),
            Stroke(1, 800, 0.8f),
            Stroke(2, 1000, 0.75f),
            Stroke(3, 1200, 0.75f),
            Stroke(4, 1400, 0.8f),
            Stroke(5, 1600, 0.9f),
        )),

        // Pattern 7: Metal / Power chord (cepat, agresif)
        Pattern("Metal Power", 350L, listOf(
            Stroke(5, 0, 1.0f), Stroke(4, 0, 0.9f),
            Stroke(5, 175, 0.9f), Stroke(4, 175, 0.85f),
            Stroke(5, 350, 1.0f), Stroke(4, 350, 0.9f),
            Stroke(5, 525, 0.9f), Stroke(4, 525, 0.85f),
            Stroke(5, 700, 1.0f), Stroke(4, 700, 0.9f),
        )),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // === TRACKING: onCreate ===
        Logger.sysEvent("onCreate", "MainActivity start")
        Logger.sysEvent("Device", "${Build.MANUFACTURER} ${Build.MODEL}")
        Logger.sysEvent("Android", "${Build.VERSION.RELEASE} SDK ${Build.VERSION.SDK_INT}")

        guitarView = findViewById(R.id.guitarView)
        tvChordInfo = findViewById(R.id.tvChordInfo)
        btnAutoStrum = findViewById(R.id.btnAutoStrum)
        vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator

        setupSoundPool()
        setupChordButtons()

        guitarView.onStringPlucked = { idx -> playString(idx) }

        findViewById<Button>(R.id.btnStrumDown).setOnClickListener {
            Logger.buttonPress("MainActivity", "btnStrumDown")
            try {
                strumDown()
            } catch (e: Exception) {
                Logger.trackError("MainActivity", "btnStrumDown", e)
            }
        }
        findViewById<Button>(R.id.btnStrumUp).setOnClickListener {
            Logger.buttonPress("MainActivity", "btnStrumUp")
            try {
                strumUp()
            } catch (e: Exception) {
                Logger.trackError("MainActivity", "btnStrumUp", e)
            }
        }
        btnAutoStrum.setOnClickListener {
            Logger.buttonPress("MainActivity", "btnAutoStrum")
            try {
                toggleAutoStrum()
            } catch (e: Exception) {
                Logger.trackError("MainActivity", "btnAutoStrum", e)
            }
        }

        // Tap chord info untuk ganti pattern
        tvChordInfo.setOnClickListener {
            currentPattern = (currentPattern + 1) % patterns.size
            Toast.makeText(
                this,
                "Pattern: ${patterns[currentPattern].name}",
                Toast.LENGTH_SHORT
            ).show()
        }

        // ============================================================
        //  RECORDING CONTROLS
        // ============================================================
        tvRecStatus = findViewById(R.id.tvRecStatus)
        btnRecord = findViewById(R.id.btnRecord)
        btnAutoTune = findViewById(R.id.btnAutoTune)

        // Request RECORD_AUDIO permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQ_RECORD_AUDIO
            )
        }

        findViewById<Button>(R.id.btnRecord).setOnClickListener {
            Logger.buttonPress("MainActivity", "btnRecord")
            Logger.methodEntry("MainActivity", "startRecording")
            try {
                startRecording()
            } catch (e: Exception) {
                Logger.trackError("MainActivity", "btnRecord", e)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                Logger.methodExit("MainActivity", "startRecording")
            }
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            Logger.buttonPress("MainActivity", "btnStop")
            Logger.methodEntry("MainActivity", "stopRecording")
            try {
                stopRecording()
            } catch (e: Exception) {
                Logger.trackError("MainActivity", "btnStop", e)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                Logger.methodExit("MainActivity", "stopRecording")
            }
        }

        findViewById<Button>(R.id.btnPlayRec).setOnClickListener {
            Logger.buttonPress("MainActivity", "btnPlayRec")
            Logger.methodEntry("MainActivity", "playRecording")
            try {
                playRecording()
            } catch (e: Exception) {
                Logger.trackError("MainActivity", "btnPlayRec", e)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                Logger.methodExit("MainActivity", "playRecording")
            }
        }

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            Logger.buttonPress("MainActivity", "btnSave")
            Logger.methodEntry("MainActivity", "saveRecording")
            try {
                saveRecording()
            } catch (e: Exception) {
                Logger.trackError("MainActivity", "btnSave", e)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                Logger.methodExit("MainActivity", "saveRecording")
            }
        }

        findViewById<Button>(R.id.btnAutoTune).setOnClickListener {
            Logger.buttonPress("MainActivity", "btnAutoTune")
            Logger.methodEntry("MainActivity", "tuneRecording")
            try {
                tuneRecording()
            } catch (e: Exception) {
                Logger.trackError("MainActivity", "btnAutoTune", e)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                Logger.methodExit("MainActivity", "tuneRecording")
            }
        }
    }

    // ============================================================
    //  RECORDING METHODS
    // ============================================================
    private fun startRecording() {
        Logger.recEvent("startRecording", "begin")
        try {
            if (isRecording) {
                Toast.makeText(this, "Sudah merekam", Toast.LENGTH_SHORT).show()
                return
            }
            val file = AudioRecorderHelper.startRecording(this)
            if (file != null) {
                isRecording = true
                tvRecStatus?.text = "🔴 Merekam..."
                Logger.i("MainActivity", "Recording started: ${file.name}")
                Toast.makeText(this, "Merekam...", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Gagal mulai rekam", Toast.LENGTH_SHORT).show()
                Logger.e("MainActivity", "startRecording failed", null)
            }
        } catch (e: Exception) {
            Logger.e("MainActivity", "startRecording exception", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopRecording() {
        Logger.recEvent("stopRecording", "begin")
        try {
            if (!isRecording) {
                Toast.makeText(this, "Belum merekam", Toast.LENGTH_SHORT).show()
                return
            }
            val file = AudioRecorderHelper.stopRecording()
            isRecording = false
            if (file != null && file.exists()) {
                lastRecording = file
                val sizeKb = file.length() / 1024
                tvRecStatus?.text = "✅ Selesai: ${file.name} (${sizeKb} KB)"
                Logger.i("MainActivity", "Recording stopped: ${file.name}")
                Toast.makeText(this, "Rekaman tersimpan", Toast.LENGTH_SHORT).show()
            } else {
                tvRecStatus?.text = "❌ Gagal rekam"
                Toast.makeText(this, "Gagal simpan rekaman", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Logger.e("MainActivity", "stopRecording exception", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun playRecording() {
        Logger.recEvent("playRecording", "begin")
        try {
            if (isPlayingRecording) {
                AudioRecorderHelper.stopPlayback()
                isPlayingRecording = false
                tvRecStatus?.text = "⏹️ Playback berhenti"
                return
            }
            val file = lastRecording ?: AudioRecorderHelper.getLatestRecording(this)
            if (file == null) {
                Toast.makeText(this, "Belum ada rekaman", Toast.LENGTH_SHORT).show()
                return
            }
            isPlayingRecording = true
            tvRecStatus?.text = "▶️ Memutar: ${file.name}"
            Logger.i("MainActivity", "Playing: ${file.name}")
            AudioRecorderHelper.play(file) {
                runOnUiThread {
                    isPlayingRecording = false
                    tvRecStatus?.text = "✅ Playback selesai"
                }
            }
        } catch (e: Exception) {
            Logger.e("MainActivity", "playRecording exception", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveRecording() {
        Logger.recEvent("saveRecording", "begin")
        try {
            val file = lastRecording ?: AudioRecorderHelper.getLatestRecording(this)
            if (file == null) {
                Toast.makeText(this, "Belum ada rekaman", Toast.LENGTH_SHORT).show()
                return
            }
            val ok = AudioRecorderHelper.saveToGallery(this, file)
            if (ok) {
                tvRecStatus?.text = "💾 Tersimpan di galeri: ${file.name}"
                Toast.makeText(this, "Tersimpan di Music/YadGuitar", Toast.LENGTH_LONG).show()
                Logger.i("MainActivity", "Saved to gallery: ${file.name}")
            } else {
                Toast.makeText(this, "Gagal simpan ke galeri", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Logger.e("MainActivity", "saveRecording exception", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun tuneRecording() {
        Logger.recEvent("tuneRecording", "begin")
        try {
            val file = lastRecording ?: AudioRecorderHelper.getLatestRecording(this)
            if (file == null) {
                Toast.makeText(this, "Belum ada rekaman", Toast.LENGTH_SHORT).show()
                return
            }

            tvRecStatus?.text = "🎼 Auto-tune: proses..."
            Toast.makeText(this, "Auto-tune diproses...", Toast.LENGTH_SHORT).show()
            Logger.i("MainActivity", "Auto-tune start: ${file.name}")

            // Proses di background thread
            Thread {
                val outputFile = File(file.parent, "tuned_${file.nameWithoutExtension}.wav")
                val ok = AutoTuneHelper.autoTuneFile(file, outputFile) { progress ->
                    runOnUiThread {
                        tvRecStatus?.text = "🎼 Auto-tune: ${(progress * 100).toInt()}%"
                    }
                }

                runOnUiThread {
                    if (ok && outputFile.exists()) {
                        tvRecStatus?.text = "✅ Auto-tune selesai: ${outputFile.name}"
                        lastRecording = outputFile
                        Toast.makeText(this, "Auto-tune selesai", Toast.LENGTH_LONG).show()
                        Logger.i("MainActivity", "Auto-tune complete: ${outputFile.name}")
                    } else {
                        tvRecStatus?.text = "❌ Auto-tune gagal"
                        Toast.makeText(this, "Auto-tune gagal", Toast.LENGTH_LONG).show()
                        Logger.e("MainActivity", "Auto-tune failed", null)
                    }
                }
            }.start()
        } catch (e: Exception) {
            Logger.e("MainActivity", "tuneRecording exception", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_RECORD_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Izin mic diberikan", Toast.LENGTH_SHORT).show()
                Logger.i("MainActivity", "RECORD_AUDIO permission granted")
            } else {
                Toast.makeText(this, "Izin mic diperlukan untuk rekam", Toast.LENGTH_LONG).show()
                Logger.w("MainActivity", "RECORD_AUDIO permission denied")
            }
        }
    }

    private fun setupSoundPool() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(12)
            .setAudioAttributes(attrs)
            .build()

        soundPool.setOnLoadCompleteListener { _, _, status ->
            if (status == 0) soundLoaded = true
        }

        val stringResIds = intArrayOf(
            R.raw.guitar_e, R.raw.guitar_a, R.raw.guitar_d,
            R.raw.guitar_g, R.raw.guitar_b, R.raw.guitar_e_high,
        )
        for (i in 0 until 6) {
            stringSoundIds[i] = soundPool.load(this, stringResIds[i], 1)
        }
    }

    // ============================================================
    // PLAY STRING dengan dinamika
    // ============================================================
    private fun playString(index: Int, volume: Float = 1.0f) {
        Logger.audioEvent("playString", "index=$index volume=$volume")
        if (index < 0 || index >= 6) {
            Logger.w("MainActivity", "playString: invalid index $index")
            return
        }
        if (!soundLoaded) {
            Logger.w("MainActivity", "playString: sound not loaded")
            return
        }

        // Skip senar kalau tidak ada di chord (kalau chord aktif)
        currentChord?.let { chord ->
            val fret = chord.frets[index]
            if (fret == -1) return  // -1 = tidak dipetik
        }

        // Mainkan suara
        soundPool.play(stringSoundIds[index], volume, volume, 1, 0, 1.0f)

                // Getar sesuai volume
        val duration = (20 + (volume * 30)).toLong()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // FIX: amplitude harus 1-255 (Android strict)
            val amplitude = (volume * 255).toInt().coerceIn(1, 255)
            vibrator.vibrate(
                VibrationEffect.createOneShot(duration, amplitude)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }

        guitarView.flashString(index)
    }

    // ============================================================
    // MANUAL STRUM
    // ============================================================
    private fun strumDown() {
        Logger.audioEvent("strumDown", "start")
        // Strum dari bass ke treble
        val strumDurationMs = 60L  // total durasi strum
        val stepDelay = strumDurationMs / 6

        for (i in 0 until 6) {
            val stringIdx = 5 - i  // 5,4,3,2,1,0
            val volume = 0.7f + (stringIdx * 0.05f)  // bass lebih keras
            handler.postDelayed({
                playString(stringIdx, volume.coerceAtMost(1.0f))
            }, i * stepDelay)
        }
    }

    private fun strumUp() {
        Logger.audioEvent("strumUp", "start")
        // Strum dari treble ke bass
        val strumDurationMs = 60L
        val stepDelay = strumDurationMs / 6

        for (i in 0 until 6) {
            val stringIdx = i  // 0,1,2,3,4,5
            val volume = 0.6f + (stringIdx * 0.05f)  // bass lebih keras
            handler.postDelayed({
                playString(stringIdx, volume.coerceAtMost(1.0f))
            }, i * stepDelay)
        }
    }

    // ============================================================
    // AUTO STRUM dengan pattern profesional
    // ============================================================
    private fun toggleAutoStrum() {
        Logger.audioEvent("toggleAutoStrum", "current=$isAutoStrumming")
        isAutoStrumming = !isAutoStrumming
        Logger.audioEvent("toggleAutoStrum", "new=$isAutoStrumming")
        if (isAutoStrumming) {
            btnAutoStrum.text = "⏹️ STOP"
            btnAutoStrum.backgroundTintList =
                android.content.res.ColorStateList.valueOf(0xFFEF4444.toInt())
            patternStep = 0
            playPattern()
            Toast.makeText(
                this,
                "Pattern: ${patterns[currentPattern].name}",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            btnAutoStrum.text = "🎵 AUTO"
            btnAutoStrum.backgroundTintList =
                android.content.res.ColorStateList.valueOf(0xFF10B981.toInt())
            handler.removeCallbacksAndMessages(null)
        }
    }

    private fun playPattern() {
        if (!isAutoStrumming) return

        val pattern = patterns[currentPattern]
        val strokes = pattern.strokes

        // Schedule semua stroke
        handler.postDelayed({
            if (!isAutoStrumming) return@postDelayed

            for (stroke in strokes) {
                handler.postDelayed({
                    if (isAutoStrumming) {
                        playString(stroke.stringIdx, stroke.volume)
                    }
                }, stroke.delayMs)
            }

            // Ulang setelah pattern selesai
            val patternDuration = strokes.maxOfOrNull { it.delayMs } ?: 500L
            handler.postDelayed({
                if (isAutoStrumming) {
                    playPattern()
                }
            }, patternDuration + 100L)  // +100ms jeda antar pattern

        }, 0)
    }

    // ============================================================
    // CHORD SETUP
    // ============================================================
    private fun setupChordButtons() {
        val container = findViewById<LinearLayout>(R.id.chordContainer)

        for (chord in Chord.ALL_CHORDS) {
            val btn = Button(this).apply {
                text = chord.name
                textSize = 14f
                setTextColor(0xFFFFFFFF.toInt())
                backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF2A2A4E.toInt())
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    80
                )
                params.marginEnd = 8
                layoutParams = params
                setPadding(24, 8, 24, 8)
                setOnClickListener { selectChord(chord) }
            }
            container.addView(btn)
        }
    }

    private fun selectChord(chord: Chord) {
        Logger.uiEvent("MainActivity", "selectChord", chord.name)
        currentChord = chord
        tvChordInfo.text = chord.name
        guitarView.setChord(chord)
        Toast.makeText(this, "Chord: ${chord.name}", Toast.LENGTH_SHORT).show()
    }

    override fun onPause() {
        super.onPause()
        Logger.sysEvent("onPause", "App pause")
    }

    override fun onResume() {
        super.onResume()
        Logger.sysEvent("onResume", "App resume")
    }

    override fun onDestroy() {
        super.onDestroy()
        Logger.sysEvent("onDestroy", "MainActivity end")
        handler.removeCallbacksAndMessages(null)
        soundPool.release()
    }

    // ============================================================
    // DATA CLASS
    // ============================================================
    data class Stroke(val stringIdx: Int, val delayMs: Long, val volume: Float)
    data class Pattern(val name: String, val tempoMs: Long, val strokes: List<Stroke>)
}
