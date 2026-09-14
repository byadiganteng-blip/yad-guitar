package com.yad.guitar

import android.app.Activity
import android.content.Intent
import android.media.AudioAttributes
import android.media.SoundPool
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * LyricPlayerActivity — putar lirik dengan chord otomatis.
 *
 * Fitur:
 *  - Upload file TXT
 *  - Tampilkan lirik + chord
 *  - Auto-scroll
 *  - Auto-play chord
 *  - Tempo control
 */
class LyricPlayerActivity : AppCompatActivity() {

    companion object {
        private const val REQ_PICK_TXT = 3001
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvStatus: TextView
    private lateinit var btnPlay: Button
    private lateinit var btnStop: Button
    private lateinit var btnUpload: Button
    private lateinit var seekBar: SeekBar
    private lateinit var tvBpm: TextView
    private lateinit var tvTranspose: TextView

    private lateinit var adapter: LyricAdapter
    private lateinit var soundPool: SoundPool
    private val stringSoundIds = IntArray(6)
    private var soundLoaded = false

    private var lyricLines: List<LyricParser.LyricLine> = emptyList()
    private var currentLineIndex = 0
    private var isPlaying = false
    private var bpm = 100
    private var transpose = 0

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lyric_player)

        Logger.sysEvent("onCreate", "LyricPlayerActivity")

        recyclerView = findViewById(R.id.recyclerLyric)
        tvStatus = findViewById(R.id.tvLyricStatus)
        btnPlay = findViewById(R.id.btnLyricPlay)
        btnStop = findViewById(R.id.btnLyricStop)
        btnUpload = findViewById(R.id.btnLyricUpload)
        seekBar = findViewById(R.id.seekBarTempo)
        tvBpm = findViewById(R.id.tvBpm)
        tvTranspose = findViewById(R.id.tvTranspose)

        // Setup SoundPool
        setupSoundPool()

        // Setup RecyclerView
        adapter = LyricAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // Buttons
        btnUpload.setOnClickListener {
            Logger.buttonPress("LyricPlayer", "btnUpload")
            pickTxtFile()
        }

        btnPlay.setOnClickListener {
            Logger.buttonPress("LyricPlayer", "btnPlay")
            togglePlay()
        }

        btnStop.setOnClickListener {
            Logger.buttonPress("LyricPlayer", "btnStop")
            stopPlay()
        }

        findViewById<Button>(R.id.btnTransposeUp).setOnClickListener {
            transpose = (transpose + 1).coerceAtMost(12)
            applyTranspose()
        }

        findViewById<Button>(R.id.btnTransposeDown).setOnClickListener {
            transpose = (transpose - 1).coerceAtLeast(-12)
            applyTranspose()
        }

        // Tempo
        seekBar.max = 200  // 40-240 BPM
        seekBar.progress = bpm - 40
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                bpm = progress + 40
                tvBpm.text = "${bpm} BPM"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        tvBpm.text = "${bpm} BPM"
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

        val resIds = intArrayOf(
            R.raw.guitar_e, R.raw.guitar_a, R.raw.guitar_d,
            R.raw.guitar_g, R.raw.guitar_b, R.raw.guitar_e_high,
        )
        for (i in 0 until 6) {
            stringSoundIds[i] = soundPool.load(this, resIds[i], 1)
        }
    }

    private fun pickTxtFile() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "text/plain"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(Intent.createChooser(intent, "Pilih file lirik (.txt)"), REQ_PICK_TXT)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK_TXT && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            loadLyricFromUri(uri)
        }
    }

    private fun loadLyricFromUri(uri: Uri) {
        Logger.recEvent("loadLyric", uri.toString())
        lyricLines = LyricParser.parseUri(this, uri)

        if (lyricLines.isEmpty()) {
            tvStatus.text = "❌ File kosong atau tidak valid"
            Toast.makeText(this, "File lirik kosong", Toast.LENGTH_SHORT).show()
            return
        }

        adapter.setData(lyricLines)
        tvStatus.text = "✅ ${lyricLines.size} baris dimuat"
        currentLineIndex = 0
        Toast.makeText(this, "${lyricLines.size} baris dimuat", Toast.LENGTH_SHORT).show()
    }

    private fun togglePlay() {
        if (isPlaying) {
            stopPlay()
        } else {
            startPlay()
        }
    }

    private fun startPlay() {
        if (lyricLines.isEmpty()) {
            Toast.makeText(this, "Upload file lirik dulu", Toast.LENGTH_SHORT).show()
            return
        }

        isPlaying = true
        btnPlay.text = "⏸️ PAUSE"
        currentLineIndex = 0
        playNextLine()
    }

    private fun stopPlay() {
        isPlaying = false
        btnPlay.text = "▶️ PLAY"
        handler.removeCallbacksAndMessages(null)
        adapter.setActiveIndex(-1)
    }

    private fun playNextLine() {
        if (!isPlaying || currentLineIndex >= lyricLines.size) {
            stopPlay()
            return
        }

        val line = lyricLines[currentLineIndex]
        adapter.setActiveIndex(currentLineIndex)

        // Scroll ke baris aktif
        recyclerView.smoothScrollToPosition(currentLineIndex)

        // Mainkan chord di baris ini
        if (line.chords.isNotEmpty()) {
            val beatDurationMs = 60000L / bpm  // durasi 1 beat
            for ((i, chordPos) in line.chords.withIndex()) {
                val chord = chordPos.chord
                handler.postDelayed({
                    playChord(chord)
                }, i * beatDurationMs / 2)  // setengah beat per chord
            }
        }

        // Jadwalkan baris berikutnya
        val lineDurationMs = when {
            line.isSection -> (60000L / bpm) * 2  // section: 2 beat
            line.chords.isEmpty() -> (60000L / bpm) * 4  // tanpa chord: 4 beat
            else -> (60000L / bpm) * line.chords.size * 2  // ada chord: 2 beat per chord
        }

        currentLineIndex++
        handler.postDelayed({
            playNextLine()
        }, lineDurationMs)
    }

    private fun playChord(chord: String) {
        // Cari chord di database
        val chordData = findChord(chord)
        if (chordData == null) {
            Logger.w("LyricPlayer", "Chord not found: $chord")
            return
        }

        // Mainkan senar sesuai chord
        val strumDelay = 25L  // delay antar senar (ms)
        for (i in 0 until 6) {
            val fret = chordData.frets[i]
            if (fret == -1) continue  // skip senar yang tidak dipetik

            val delay = i * strumDelay
            handler.postDelayed({
                if (soundLoaded) {
                    soundPool.play(stringSoundIds[i], 0.9f, 0.9f, 1, 0, 1.0f)
                }
            }, delay)
        }
    }

    private fun findChord(chordName: String): Chord? {
        val baseName = chordName.replace("#", "#")
        return Chord.ALL_CHORDS.find {
            it.name.equals(baseName, ignoreCase = true) ||
            it.name.equals(chordName, ignoreCase = true)
        }
    }

    private fun applyTranspose() {
        tvTranspose.text = if (transpose > 0) "+$transpose" else "$transpose"
        // Update adapter dengan transpose
        val transposed = lyricLines.map { line ->
            line.copy(
                chords = line.chords.map { cp ->
                    cp.copy(chord = LyricParser.transpose(cp.chord, transpose))
                }
            )
        }
        adapter.setData(transposed)
        Toast.makeText(this, "Transpose: $transpose", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        Logger.sysEvent("onDestroy", "LyricPlayerActivity")
        handler.removeCallbacksAndMessages(null)
        soundPool.release()
    }
}
