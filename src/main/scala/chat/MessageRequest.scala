package chat

import zio.schema.*
import zio.json.*

case class MessageRequest(username: String, message: String)
  derives Schema,
  JsonDecoder
