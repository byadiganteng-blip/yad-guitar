package com.yad.guitar

import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
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

    // Sound IDs untuk 6 senar (E-A-D-G-B-E)
    private val stringSoundIds = IntArray(6)
    private var soundLoaded = false

    // Chord aktif (indeks senar yang dipetik + fret yang ditekan)
    private var currentChord: Chord? = null

    // Auto strum
    private val handler = Handler(Looper.getMainLooper())
    private var isAutoStrumming = false
    private var autoStrumPattern = 0
    private val autoStrumPatterns = listOf(
        intArrayOf(5, 4, 3, 2, 1, 0),           // Down
        intArrayOf(0, 1, 2, 3, 4, 5),           // Up
        intArrayOf(5, 3, 4, 2, 3, 1, 2, 0),     // Down-up pattern
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        guitarView = findViewById(R.id.guitarView)
        tvChordInfo = findViewById(R.id.tvChordInfo)
        btnAutoStrum = findViewById(R.id.btnAutoStrum)

        vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator

        // Init SoundPool
        setupSoundPool()

        // Setup chord buttons
        setupChordButtons()

        // Setup guitar view (pluck listener)
        guitarView.onStringPlucked = { stringIndex ->
            playString(stringIndex)
        }

        // Setup strum buttons
        findViewById<Button>(R.id.btnStrumDown).setOnClickListener {
            strumDown()
        }
        findViewById<Button>(R.id.btnStrumUp).setOnClickListener {
            strumUp()
        }
        btnAutoStrum.setOnClickListener {
            toggleAutoStrum()
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
            if (status == 0) {
                soundLoaded = true
            }
        }

        // Load suara senar dari raw resources
        // Nanti file guitar_e.wav, guitar_a.wav, dst harus ada di res/raw/
        val stringResIds = intArrayOf(
            R.raw.guitar_e,
            R.raw.guitar_a,
            R.raw.guitar_d,
            R.raw.guitar_g,
            R.raw.guitar_b,
            R.raw.guitar_e_high,
        )
        for (i in 0 until 6) {
            stringSoundIds[i] = soundPool.load(this, stringResIds[i], 1)
        }
    }

    private fun playString(index: Int, volume: Float = 1.0f) {
        if (index < 0 || index >= 6) return
        if (!soundLoaded) return

        // Play suara senar
        soundPool.play(stringSoundIds[index], volume, volume, 1, 0, 1.0f)

        // Getar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(30)
        }

        // Visual feedback
        guitarView.flashString(index)
    }

    private fun strumDown() {
        // Petik dari senar 6 (bass) ke 1 (treble)
        for (i in 5 downTo 0) {
            val delay = ((5 - i) * 25).toLong()
            handler.postDelayed({
                playString(i)
            }, delay)
        }
    }

    private fun strumUp() {
        // Petik dari senar 1 (treble) ke 6 (bass)
        for (i in 0..5) {
            val delay = (i * 25).toLong()
            handler.postDelayed({
                playString(i)
            }, delay)
        }
    }

    private fun toggleAutoStrum() {
        isAutoStrumming = !isAutoStrumming
        if (isAutoStrumming) {
            btnAutoStrum.text = "⏹️ STOP"
            btnAutoStrum.backgroundTintList =
                android.content.res.ColorStateList.valueOf(0xFFEF4444.toInt())
            startAutoStrum()
        } else {
            btnAutoStrum.text = "🎵 AUTO"
            btnAutoStrum.backgroundTintList =
                android.content.res.ColorStateList.valueOf(0xFF10B981.toInt())
            handler.removeCallbacksAndMessages(null)
        }
    }

    private fun startAutoStrum() {
        if (!isAutoStrumming) return
        val pattern = autoStrumPatterns[autoStrumPattern]
        for ((i, stringIdx) in pattern.withIndex()) {
            handler.postDelayed({
                if (isAutoStrumming) playString(stringIdx)
            }, (i * 100).toLong())
        }
        // Ulang setiap 1.5 detik
        handler.postDelayed({
            if (isAutoStrumming) {
                autoStrumPattern = (autoStrumPattern + 1) % autoStrumPatterns.size
                startAutoStrum()
            }
        }, 1500)
    }

    // ============================================================
    //  CHORD SETUP
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
                setOnClickListener {
                    selectChord(chord)
                }
            }
            container.addView(btn)
        }
    }

    private fun selectChord(chord: Chord) {
        currentChord = chord
        tvChordInfo.text = chord.name

        // Update guitar view — tampilkan fret yang ditekan
        guitarView.setChord(chord)

        // Toast info
        Toast.makeText(this, "Chord: ${chord.name}", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        soundPool.release()
    }
}
