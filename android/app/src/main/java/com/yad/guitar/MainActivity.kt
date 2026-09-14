package com.yad.guitar

import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

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

        guitarView = findViewById(R.id.guitarView)
        tvChordInfo = findViewById(R.id.tvChordInfo)
        btnAutoStrum = findViewById(R.id.btnAutoStrum)
        vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator

        setupSoundPool()
        setupChordButtons()

        guitarView.onStringPlucked = { idx -> playString(idx) }

        findViewById<Button>(R.id.btnStrumDown).setOnClickListener { strumDown() }
        findViewById<Button>(R.id.btnStrumUp).setOnClickListener { strumUp() }
        btnAutoStrum.setOnClickListener { toggleAutoStrum() }

        // Tap chord info untuk ganti pattern
        tvChordInfo.setOnClickListener {
            currentPattern = (currentPattern + 1) % patterns.size
            Toast.makeText(
                this,
                "Pattern: ${patterns[currentPattern].name}",
                Toast.LENGTH_SHORT
            ).show()
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
        if (index < 0 || index >= 6) return
        if (!soundLoaded) return

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
            vibrator.vibrate(
                VibrationEffect.createOneShot(
                    duration,
                    (volume * VibrationEffect.DEFAULT_AMPLITUDE).toInt()
                )
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
        isAutoStrumming = !isAutoStrumming
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
        currentChord = chord
        tvChordInfo.text = chord.name
        guitarView.setChord(chord)
        Toast.makeText(this, "Chord: ${chord.name}", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        soundPool.release()
    }

    // ============================================================
    // DATA CLASS
    // ============================================================
    data class Stroke(val stringIdx: Int, val delayMs: Long, val volume: Float)
    data class Pattern(val name: String, val tempoMs: Long, val strokes: List<Stroke>)
}
