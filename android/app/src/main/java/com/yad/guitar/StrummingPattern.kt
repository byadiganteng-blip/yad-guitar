package com.yad.guitar

/**
 * StrummingPattern — pola strumming sesuai panduan wikiHow.
 *
 * Konsep dari wikiHow "Cara Memetik Gitar":
 *  1. Down-up per ketukan (B-A)
 *  2. Quarter note = 1x per ketukan
 *  3. Eighth note = 2x per ketukan
 *  4. Pola pop-rock = B-B-A-A-B-A
 *  5. Aksen berbeda tiap petikan
 *  6. Palm-mute = redam dengan telapak
 */
object StrummingPattern {

    private const val TAG = "StrummingPattern"

    /**
     * Tipe petikan.
     */
    enum class StrokeType {
        DOWN,           // Bawah
        UP,             // Atas
        DOWN_MUTED,     // Bawah + palm-mute
        UP_MUTED,       // Atas + palm-mute
        REST            // Diam (tangan gerak, tapi tidak petik)
    }

    /**
     * Satu petikan dalam pola.
     */
    data class Stroke(
        val type: StrokeType,
        val beat: Float,        // posisi dalam 1 bar (0.0 - 1.0)
        val volume: Float = 1.0f,  // aksen (0.0 - 1.0)
        val duration: Long = 50L   // durasi petikan (ms)
    )

    /**
     * Satu pola strumming.
     */
    data class Pattern(
        val name: String,
        val description: String,
        val beatsPerBar: Int,        // 4 = 4/4 time
        val noteType: NoteType,      // QUARTER, EIGHTH
        val bpm: Int,                // tempo default
        val strokes: List<Stroke>
    )

    enum class NoteType(val displayName: String, val beatsPerNote: Float) {
        QUARTER("1/4", 1.0f),
        EIGHTH("1/8", 0.5f),
        SIXTEENTH("1/16", 0.25f)
    }

    // ============================================================
    //  DAFTAR POLA SESUAI WIKIHOW
    // ============================================================
    val ALL_PATTERNS = listOf(

        // 1. DOWN-UP DASAR (wikiHow Step 1, Bagian 3)
        Pattern(
            name = "Down-Up Dasar",
            description = "Pola dasar: Bawah-Atas per ketukan. Cocok untuk pemula.",
            beatsPerBar = 4,
            noteType = NoteType.QUARTER,
            bpm = 80,
            strokes = listOf(
                Stroke(StrokeType.DOWN, 0.0f, 1.0f),
                Stroke(StrokeType.UP, 0.5f, 0.7f),
                Stroke(StrokeType.DOWN, 1.0f, 1.0f),
                Stroke(StrokeType.UP, 1.5f, 0.7f),
                Stroke(StrokeType.DOWN, 2.0f, 1.0f),
                Stroke(StrokeType.UP, 2.5f, 0.7f),
                Stroke(StrokeType.DOWN, 3.0f, 1.0f),
                Stroke(StrokeType.UP, 3.5f, 0.7f),
            )
        ),

        // 2. EIGHTH NOTE (wikiHow Step 2, Bagian 3)
        Pattern(
            name = "Eighth Note",
            description = "2x petikan per ketukan. Tempo sama, tapi lebih cepat.",
            beatsPerBar = 4,
            noteType = NoteType.EIGHTH,
            bpm = 80,
            strokes = listOf(
                // 1 ketukan = 2 petikan (D-U)
                Stroke(StrokeType.DOWN, 0.0f, 1.0f),
                Stroke(StrokeType.UP, 0.25f, 0.6f),
                Stroke(StrokeType.DOWN, 0.5f, 0.9f),
                Stroke(StrokeType.UP, 0.75f, 0.6f),
                Stroke(StrokeType.DOWN, 1.0f, 1.0f),
                Stroke(StrokeType.UP, 1.25f, 0.6f),
                Stroke(StrokeType.DOWN, 1.5f, 0.9f),
                Stroke(StrokeType.UP, 1.75f, 0.6f),
                Stroke(StrokeType.DOWN, 2.0f, 1.0f),
                Stroke(StrokeType.UP, 2.25f, 0.6f),
                Stroke(StrokeType.DOWN, 2.5f, 0.9f),
                Stroke(StrokeType.UP, 2.75f, 0.6f),
                Stroke(StrokeType.DOWN, 3.0f, 1.0f),
                Stroke(StrokeType.UP, 3.25f, 0.6f),
                Stroke(StrokeType.DOWN, 3.5f, 0.9f),
                Stroke(StrokeType.UP, 3.75f, 0.6f),
            )
        ),

        // 3. POP-ROCK (wikiHow Step 4, Bagian 3)
        Pattern(
            name = "Pop-Rock",
            description = "Pola pop-rock klasik: B-B-A-A-B-A. Cocok untuk lagu pop/rock.",
            beatsPerBar = 4,
            noteType = NoteType.EIGHTH,
            bpm = 100,
            strokes = listOf(
                // Bawah - Bawah - Atas - Atas - Bawah - Atas
                Stroke(StrokeType.DOWN, 0.0f, 1.0f),      // Beat 1
                Stroke(StrokeType.DOWN, 0.5f, 0.9f),      // Beat 1.5
                Stroke(StrokeType.UP, 1.0f, 0.7f),        // Beat 2
                Stroke(StrokeType.UP, 1.5f, 0.7f),        // Beat 2.5
                Stroke(StrokeType.DOWN, 2.0f, 1.0f),      // Beat 3
                Stroke(StrokeType.UP, 2.5f, 0.7f),        // Beat 3.5
                Stroke(StrokeType.DOWN, 3.0f, 0.9f),      // Beat 4
                Stroke(StrokeType.UP, 3.5f, 0.6f),        // Beat 4.5
            )
        ),

        // 4. FOLK (wikiHow Step 6)
        Pattern(
            name = "Folk",
            description = "Pola folk klasik: B-A-B-A-B-A-B-A + bass note.",
            beatsPerBar = 4,
            noteType = NoteType.QUARTER,
            bpm = 90,
            strokes = listOf(
                Stroke(StrokeType.DOWN, 0.0f, 1.0f),      // Bass
                Stroke(StrokeType.UP, 0.5f, 0.7f),
                Stroke(StrokeType.DOWN, 1.0f, 0.9f),
                Stroke(StrokeType.UP, 1.5f, 0.7f),
                Stroke(StrokeType.DOWN, 2.0f, 1.0f),
                Stroke(StrokeType.UP, 2.5f, 0.7f),
                Stroke(StrokeType.DOWN, 3.0f, 0.9f),
                Stroke(StrokeType.UP, 3.5f, 0.7f),
            )
        ),

        // 5. BALLAD (lambat, penuh perasaan)
        Pattern(
            name = "Ballad",
            description = "Pola ballad lambat: B-A-B-A + arpeggio bass.",
            beatsPerBar = 4,
            noteType = NoteType.QUARTER,
            bpm = 70,
            strokes = listOf(
                Stroke(StrokeType.DOWN, 0.0f, 1.0f),
                Stroke(StrokeType.UP, 0.5f, 0.6f),
                Stroke(StrokeType.DOWN, 1.0f, 0.8f),
                Stroke(StrokeType.UP, 1.5f, 0.6f),
                Stroke(StrokeType.DOWN, 2.0f, 1.0f),
                Stroke(StrokeType.UP, 2.5f, 0.6f),
                Stroke(StrokeType.DOWN, 3.0f, 0.8f),
                Stroke(StrokeType.UP, 3.5f, 0.6f),
            )
        ),

        // 6. REGGAE / SKA (aksen di off-beat)
        Pattern(
            name = "Reggae Ska",
            description = "Aksen di off-beat. Tangan tetap gerak, tapi hanya petik di off-beat.",
            beatsPerBar = 4,
            noteType = NoteType.EIGHTH,
            bpm = 120,
            strokes = listOf(
                Stroke(StrokeType.REST, 0.0f, 0.0f),      // Diam di beat 1
                Stroke(StrokeType.UP, 0.5f, 1.0f),        // Petik di off-beat
                Stroke(StrokeType.REST, 1.0f, 0.0f),
                Stroke(StrokeType.UP, 1.5f, 0.9f),
                Stroke(StrokeType.REST, 2.0f, 0.0f),
                Stroke(StrokeType.UP, 2.5f, 1.0f),
                Stroke(StrokeType.REST, 3.0f, 0.0f),
                Stroke(StrokeType.UP, 3.5f, 0.9f),
            )
        ),

        // 7. METAL (power chord, cepat)
        Pattern(
            name = "Metal",
            description = "Power chord cepat: B-B-B-B dengan palm-mute.",
            beatsPerBar = 4,
            noteType = NoteType.EIGHTH,
            bpm = 140,
            strokes = listOf(
                Stroke(StrokeType.DOWN_MUTED, 0.0f, 1.0f),
                Stroke(StrokeType.DOWN_MUTED, 0.5f, 0.9f),
                Stroke(StrokeType.DOWN, 1.0f, 1.0f),
                Stroke(StrokeType.DOWN_MUTED, 1.5f, 0.9f),
                Stroke(StrokeType.DOWN_MUTED, 2.0f, 1.0f),
                Stroke(StrokeType.DOWN_MUTED, 2.5f, 0.9f),
                Stroke(StrokeType.DOWN, 3.0f, 1.0f),
                Stroke(StrokeType.DOWN_MUTED, 3.5f, 0.9f),
            )
        ),
    )

    // ============================================================
    //  CHORD PROGRESSION UNTUK LATIHAN
    // ============================================================
    data class ChordProgression(
        val name: String,
        val description: String,
        val chords: List<String>,
        val beatsPerChord: Int = 4,   // 4 beat per chord
        val suggestedPattern: String = "Down-Up Dasar"
    )

    val ALL_PROGRESSIONS = listOf(
        // Pop progression (paling umum)
        ChordProgression(
            name = "Pop Classic",
            description = "Progression paling umum di lagu pop: I-V-vi-IV",
            chords = listOf("C", "G", "Am", "F"),
            beatsPerChord = 4,
            suggestedPattern = "Pop-Rock"
        ),
        ChordProgression(
            name = "Pop 1-5-6-4 (G)",
            description = "Pop progression dalam nada dasar G",
            chords = listOf("G", "D", "Em", "C"),
            beatsPerChord = 4,
            suggestedPattern = "Down-Up Dasar"
        ),
        ChordProgression(
            name = "Folk 1-4-5",
            description = "Progression folk klasik: I-IV-V",
            chords = listOf("G", "C", "D", "G"),
            beatsPerChord = 4,
            suggestedPattern = "Folk"
        ),
        ChordProgression(
            name = "Ballad 1-6-4-5",
            description = "Progression ballad pop Indonesia",
            chords = listOf("C", "Am", "F", "G"),
            beatsPerChord = 4,
            suggestedPattern = "Ballad"
        ),
        ChordProgression(
            name = "Minor Sad",
            description = "Progression sedih: vi-IV-I-V",
            chords = listOf("Am", "F", "C", "G"),
            beatsPerChord = 4,
            suggestedPattern = "Ballad"
        ),
        ChordProgression(
            name = "Punk 1-4-5",
            description = "Progression punk cepat",
            chords = listOf("C", "F", "G", "C"),
            beatsPerChord = 2,
            suggestedPattern = "Metal"
        ),
    )

    // ============================================================
    //  HELPER
    // ============================================================
    fun findPattern(name: String): Pattern? {
        return ALL_PATTERNS.find { it.name.equals(name, ignoreCase = true) }
    }

    fun findProgression(name: String): ChordProgression? {
        return ALL_PROGRESSIONS.find { it.name.equals(name, ignoreCase = true) }
    }

    fun getPatternNames(): List<String> = ALL_PATTERNS.map { it.name }

    fun getProgressionNames(): List<String> = ALL_PROGRESSIONS.map { it.name }
}
