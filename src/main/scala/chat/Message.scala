package chat

case class Message(
    id: String,
    username: String,
    content: String,
    timestamp: Long,
  )

object Message:

  def apply(username: String, content: String): Message =
    Message(
      java.util.UUID.randomUUID().toString,
      username,
      content,
      System.currentTimeMillis(),
    )
