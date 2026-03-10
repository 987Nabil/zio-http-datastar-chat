package chat

enum Message:

  case Chat(
      id: String,
      username: String,
      content: String,
      timestamp: Long,
    ) extends Message

  case Typing(users: Set[String])

  case Deletion(messageId: String)


object Message:

  def chat(username: String, content: String): Message.Chat =
    Chat(
      java.util.UUID.randomUUID().toString,
      username,
      content,
      System.currentTimeMillis(),
    )
