package com.nordling.juoksu

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nordling.juoksu.databinding.ItemSessionBinding
import com.nordling.juoksu.db.Session
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SessionAdapter(
    private val onOpen: (Session) -> Unit,
    private val onMap: (Session) -> Unit
) : ListAdapter<Session, SessionAdapter.ViewHolder>(Diff) {

    private val dateFormat = SimpleDateFormat("MMM d  HH:mm", Locale.getDefault())

    inner class ViewHolder(private val binding: ItemSessionBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(session: Session) {
            binding.textSessionName.text = session.name
            val durMs = (session.endTime ?: session.startTime) - session.startTime
            val mins = durMs / 60000
            binding.textSessionStats.text =
                "%.2f km  •  %d min  •  %s".format(
                    session.distanceMeters / 1000.0,
                    mins,
                    dateFormat.format(Date(session.startTime))
                )
            binding.root.setOnClickListener { onOpen(session) }
            binding.btnMap.setOnClickListener { onMap(session) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemSessionBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    object Diff : DiffUtil.ItemCallback<Session>() {
        override fun areItemsTheSame(a: Session, b: Session) = a.id == b.id
        override fun areContentsTheSame(a: Session, b: Session) = a == b
    }
}
