package chat

import zio.schema.*

case class ChatMessage(
    id: String,
    username: String,
    content: String,
    timestamp: Long,
  )

object ChatMessage:
  given Schema[ChatMessage] = DeriveSchema.gen[ChatMessage]
