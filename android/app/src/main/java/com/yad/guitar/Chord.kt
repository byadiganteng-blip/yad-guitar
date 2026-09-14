package com.yad.guitar

/**
 * Data chord gitar.
 * frets: posisi fret tiap senar (0 = open, -1 = tidak dipetik)
 * fingers: nomor jari (0 = tidak ada, 1=telunjuk, 2=tengah, 3=manis, 4=kelingking)
 */
data class Chord(
    val name: String,
    val frets: IntArray,
    val fingers: IntArray
) {
    companion object {
        val ALL_CHORDS = listOf(
            // C major: x32010
            Chord("C",  intArrayOf(-1, 3, 2, 0, 1, 0), intArrayOf(0, 3, 2, 0, 1, 0)),
            // G major: 320003
            Chord("G",  intArrayOf(3, 2, 0, 0, 0, 3),  intArrayOf(2, 1, 0, 0, 0, 3)),
            // Am: x02210
            Chord("Am", intArrayOf(-1, 0, 2, 2, 1, 0), intArrayOf(0, 0, 2, 3, 1, 0)),
            // F: 133211
            Chord("F",  intArrayOf(1, 3, 3, 2, 1, 1),  intArrayOf(1, 3, 4, 2, 1, 1)),
            // D: xx0232
            Chord("D",  intArrayOf(-1, -1, 0, 2, 3, 2), intArrayOf(0, 0, 0, 1, 3, 2)),
            // Dm: xx0231
            Chord("Dm", intArrayOf(-1, -1, 0, 2, 3, 1), intArrayOf(0, 0, 0, 2, 3, 1)),
            // Em: 022000
            Chord("Em", intArrayOf(0, 2, 2, 0, 0, 0),  intArrayOf(0, 2, 3, 0, 0, 0)),
            // E: 022100
            Chord("E",  intArrayOf(0, 2, 2, 1, 0, 0),  intArrayOf(0, 2, 3, 1, 0, 0)),
            // A: x02220
            Chord("A",  intArrayOf(-1, 0, 2, 2, 2, 0), intArrayOf(0, 0, 1, 2, 3, 0)),
            // Bm: x24432
            Chord("Bm", intArrayOf(-1, 2, 4, 4, 3, 2), intArrayOf(0, 1, 3, 4, 2, 1)),
            // B: x24442
            Chord("B",  intArrayOf(-1, 2, 4, 4, 4, 2), intArrayOf(0, 1, 2, 3, 4, 1)),
            // A7: x02020
            Chord("A7", intArrayOf(-1, 0, 2, 0, 2, 0), intArrayOf(0, 0, 1, 0, 2, 0)),
            // C7: x32310
            Chord("C7", intArrayOf(-1, 3, 2, 3, 1, 0), intArrayOf(0, 3, 2, 4, 1, 0)),
            // G7: 320001
            Chord("G7", intArrayOf(3, 2, 0, 0, 0, 1),  intArrayOf(3, 2, 0, 0, 0, 1)),
            // E7: 020100
            Chord("E7", intArrayOf(0, 2, 0, 1, 0, 0),  intArrayOf(0, 2, 0, 1, 0, 0)),
            // D7: xx0212
            Chord("D7", intArrayOf(-1, -1, 0, 2, 1, 2), intArrayOf(0, 0, 0, 2, 1, 3)),
            // Fmaj7: xx3210
            Chord("Fmaj7", intArrayOf(-1, -1, 3, 2, 1, 0), intArrayOf(0, 0, 3, 2, 1, 0)),
            // Cmaj7: x32000
            Chord("Cmaj7", intArrayOf(-1, 3, 2, 0, 0, 0), intArrayOf(0, 3, 2, 0, 0, 0)),
            // Am7: x02010
            Chord("Am7", intArrayOf(-1, 0, 2, 0, 1, 0), intArrayOf(0, 0, 2, 0, 1, 0)),
            // Dm7: xx0211
            Chord("Dm7", intArrayOf(-1, -1, 0, 2, 1, 1), intArrayOf(0, 0, 0, 2, 1, 1)),
        )
    }
}
