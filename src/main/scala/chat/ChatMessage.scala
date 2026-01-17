package chat

import zio.schema.*

case class ChatMessage(
    id: String,
    username: String,
    content: String,
    timestamp: Long,
  )

object ChatMessage:
  def apply(username: String, content: String): ChatMessage =
    ChatMessage(java.util.UUID.randomUUID().toString, username, content, System.currentTimeMillis())
  given Schema[ChatMessage] = DeriveSchema.gen[ChatMessage]
