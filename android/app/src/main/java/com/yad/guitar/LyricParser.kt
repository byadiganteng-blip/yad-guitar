package com.yad.guitar

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * LyricParser — parse file TXT lirik + chord.
 *
 * Format yang didukung:
 *   intro  :  G# . . 
 *   reff  :
 *              C#     G#
 *    wes tak lilakke . .  lungamu . . 
 *          A#m
 *    ora butuh wong sing seneng
 */
object LyricParser {

    private const val TAG = "LyricParser"

    // Regex untuk chord: C, C#, C#m, Cm, C7, Cmaj7, Csus4, dll
    private val CHORD_REGEX = Regex(
        "\\b([A-G][#b]?(?:m|maj|min|dim|aug|sus|add)?[0-9]?(?:/[A-G][#b]?)?)\\b"
    )

    /**
     * Data class untuk satu baris lirik.
     */
    data class LyricLine(
        val chords: List<ChordPosition>,   // Chord + posisi
        val text: String,                   // Teks lirik
        val isSection: Boolean = false,     // "reff :", "intro :" dll
        val sectionName: String = ""        // Nama section
    )

    data class ChordPosition(
        val chord: String,
        val columnIndex: Int
    )

    /**
     * Parse file TXT jadi list LyricLine.
     */
    fun parseFile(file: File): List<LyricLine> {
        return try {
            val content = file.readText()
            parseString(content)
        } catch (e: Exception) {
            Logger.trackError("LyricParser", "parseFile", e)
            emptyList()
        }
    }

    /**
     * Parse string lirik.
     */
    fun parseString(content: String): List<LyricLine> {
        val lines = content.split("\n")
        val result = mutableListOf<LyricLine>()
        var pendingChords = mutableListOf<ChordPosition>()
        var pendingSection = ""
        var pendingIsSection = false

        for (rawLine in lines) {
            val line = rawLine.trimEnd()
            if (line.isBlank()) {
                continue
            }

            // Cek apakah baris ini section header ("reff :", "intro :", "[ featuring ]")
            if (isSectionHeader(line)) {
                // Simpan section sebagai baris sendiri
                if (pendingChords.isNotEmpty()) {
                    result.add(LyricLine(pendingChords, "", pendingIsSection, pendingSection))
                    pendingChords = mutableListOf()
                }
                result.add(LyricLine(emptyList(), line.trim(), true, line.trim().trimEnd(':')))
                continue
            }

            // Cek apakah baris ini chord saja (tidak ada lirik)
            val chordMatches = CHORD_REGEX.findAll(line).toList()
            val cleanedLine = line.replace(CHORD_REGEX, "  ").trim()

            if (cleanedLine.isEmpty() && chordMatches.isNotEmpty()) {
                // Baris chord saja, simpan untuk baris berikutnya
                for (match in chordMatches) {
                    pendingChords.add(ChordPosition(match.value, match.range.first))
                }
                continue
            }

            // Baris ini punya lirik (mungkin juga ada chord inline)
            val chords = mutableListOf<ChordPosition>()

            // Chord dari baris sebelumnya
            chords.addAll(pendingChords)
            pendingChords = mutableListOf()

            // Chord inline (di baris yang sama dengan lirik)
            for (match in chordMatches) {
                chords.add(ChordPosition(match.value, match.range.first))
            }

            result.add(LyricLine(chords, cleanedLine, false, ""))

            // Reset section
            pendingSection = ""
            pendingIsSection = false
        }

        // Sisa chord tanpa lirik
        if (pendingChords.isNotEmpty()) {
            result.add(LyricLine(pendingChords, "", false, ""))
        }

        Logger.i(TAG, "Parsed ${result.size} lines from ${content.length} chars")
        return result
    }

    /**
     * Cek apakah baris ini section header.
     */
    private fun isSectionHeader(line: String): Boolean {
        val lower = line.lowercase().trim()
        return lower.startsWith("intro") ||
               lower.startsWith("reff") ||
               lower.startsWith("refrain") ||
               lower.startsWith("chorus") ||
               lower.startsWith("verse") ||
               lower.startsWith("bridge") ||
               lower.startsWith("outro") ||
               lower.startsWith("interlude") ||
               lower.startsWith("featuring") ||
               lower.startsWith("[") ||
               (lower.endsWith(":") && lower.length < 20)
    }

    /**
     * Baca file dari Uri (hasil file picker).
     */
    fun parseUri(context: Context, uri: Uri): List<LyricLine> {
        return try {
            val content = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: return emptyList()

            // Simpan ke cache
            val cacheFile = File(context.cacheDir, "lyric_${System.currentTimeMillis()}.txt")
            cacheFile.writeText(content)

            parseString(content)
        } catch (e: Exception) {
            Logger.trackError("LyricParser", "parseUri", e)
            emptyList()
        }
    }

    /**
     * Transpose chord (naik/turun nada).
     * @param chord Chord asli, misal "C#" atau "A#m"
     * @param semitones Jumlah semitone (+1 = naik 1 nada)
     */
    fun transpose(chord: String, semitones: Int): String {
        val notes = arrayOf(
            "C", "C#", "D", "D#", "E", "F",
            "F#", "G", "G#", "A", "A#", "B"
        )

        // Ekstrak root note + suffix
        val regex = Regex("^([A-G][#b]?)(.*)$")
        val match = regex.find(chord) ?: return chord
        var root = match.groupValues[1]
        val suffix = match.groupValues[2]

        // Normalize flat → sharp
        root = when (root) {
            "Db" -> "C#"
            "Eb" -> "D#"
            "Gb" -> "F#"
            "Ab" -> "G#"
            "Bb" -> "A#"
            else -> root
        }

        val idx = notes.indexOf(root)
        if (idx < 0) return chord

        val newIdx = (idx + semitones).mod(notes.size)
        return notes[newIdx] + suffix
    }
}
