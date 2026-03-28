package com.jarvismini

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Adapter for displaying chat messages in RecyclerView
 */
class ChatAdapter(
    private val messages: List<ChatMessage>
) : RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {

    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_ASSISTANT = 2
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isUser) VIEW_TYPE_USER else VIEW_TYPE_ASSISTANT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layoutId = if (viewType == VIEW_TYPE_USER) {
            android.R.layout.simple_list_item_1
        } else {
            android.R.layout.simple_list_item_2
        }
        
        val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun getItemCount(): Int = messages.size

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView.findViewById(android.R.id.text1)

        fun bind(message: ChatMessage) {
            textView.text = message.text
            
            // Style differently for user vs assistant
            if (message.isUser) {
                textView.setPadding(48, 16, 16, 16)
                textView.setBackgroundColor(0xFF1E3A8A.toInt()) // Blue background for user
                textView.setTextColor(0xFFFFFFFF.toInt()) // White text
            } else {
                textView.setPadding(16, 16, 48, 16)
                textView.setBackgroundColor(0xFF1F2937.toInt()) // Dark gray for assistant
                textView.setTextColor(0xFF06B6D4.toInt()) // Cyan text (matches your theme)
            }
        }
    }
}
