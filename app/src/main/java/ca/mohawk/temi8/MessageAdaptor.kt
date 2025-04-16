package ca.mohawk.temi8

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Adapter for displaying messages in a RecyclerView. It differentiates between user and bot messages.
 * @param messages A list of Message objects to display.
 */
class MessageAdapter(private val messages: List<Message>) : RecyclerView.Adapter<MessageAdapter.ViewHolder>() {

     /**
     * ViewHolder class to hold references to the views for each message.
     */
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val userMessage: TextView = view.findViewById(R.id.userMessage)
        val botMessage: TextView = view.findViewById(R.id.botMessage)
    }
    /**
     * Inflates the layout for individual messages within the RecyclerView.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false)
        return ViewHolder(view)
    }

    /**
     * Binds the data (Message) to the ViewHolder, showing user or bot text conditionally.
     */
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val message = messages[position]
        if (message.isUser) {
            holder.userMessage.text = message.text
            holder.userMessage.visibility = View.VISIBLE
            holder.botMessage.visibility = View.GONE
        } else {
            holder.botMessage.text = message.text
            holder.botMessage.visibility = View.VISIBLE
            holder.userMessage.visibility = View.GONE
        }
    }
    /**
     * Returns the total number of messages in the data set held by the adapter.
     */
    override fun getItemCount() = messages.size
}
