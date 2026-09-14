package com.yad.guitar

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.yad.guitar.LyricParser.LyricLine
import com.yad.guitar.LyricParser.ChordPosition

/**
 * LyricAdapter — tampilkan lirik + chord.
 */
class LyricAdapter : RecyclerView.Adapter<LyricAdapter.VH>() {

    private var lines: List<LyricLine> = emptyList()
    private var activeIndex = -1

    fun setData(newLines: List<LyricLine>) {
        lines = newLines
        notifyDataSetChanged()
    }

    fun setActiveIndex(index: Int) {
        val old = activeIndex
        activeIndex = index
        if (old >= 0) notifyItemChanged(old)
        if (index >= 0) notifyItemChanged(index)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_lyric_line, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(lines[position], position == activeIndex)
    }

    override fun getItemCount(): Int = lines.size

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val layoutChords: LinearLayout = itemView.findViewById(R.id.layoutChords)
        val tvLyric: TextView = itemView.findViewById(R.id.tvLyricText)
        val tvSection: TextView = itemView.findViewById(R.id.tvSection)
        val container: LinearLayout = itemView.findViewById(R.id.lyricContainer)

        fun bind(line: LyricLine, isActive: Boolean) {
            // Background
            container.setBackgroundColor(
                if (isActive) Color.parseColor("#2A2A4E")
                else Color.TRANSPARENT
            )

            // Section
            if (line.isSection) {
                tvSection.visibility = View.VISIBLE
                tvSection.text = line.text
                tvLyric.visibility = View.GONE
                layoutChords.visibility = View.GONE
            } else {
                tvSection.visibility = View.GONE

                // Chords
                layoutChords.removeAllViews()
                if (line.chords.isNotEmpty()) {
                    layoutChords.visibility = View.VISIBLE
                    val ctx = itemView.context
                    for (cp in line.chords) {
                        val tv = TextView(ctx).apply {
                            text = cp.chord
                            setTextColor(Color.parseColor("#8B5CF6"))
                            textSize = 14f
                            setTypeface(typeface, android.graphics.Typeface.BOLD)
                            setPadding(0, 0, 24, 0)
                        }
                        layoutChords.addView(tv)
                    }
                } else {
                    layoutChords.visibility = View.GONE
                }

                // Lyric
                tvLyric.visibility = View.VISIBLE
                tvLyric.text = line.text
                tvLyric.setTextColor(
                    if (isActive) Color.parseColor("#FFFFFF")
                    else Color.parseColor("#CBD5E1")
                )
            }
        }
    }
}
