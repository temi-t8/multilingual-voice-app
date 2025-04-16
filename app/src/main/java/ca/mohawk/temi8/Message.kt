package ca.mohawk.temi8

/**
 * A simple data class representing a message in the chat.
 * @property text The actual text of the message.
 * @property isUser Indicates whether this message was sent by the user (true) or by the assistant (false).
 */

data class Message(val text: String, val isUser: Boolean)
