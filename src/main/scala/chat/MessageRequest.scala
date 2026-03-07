package chat

import zio.schema.*

case class MessageRequest(username: String, message: String) derives Schema

case class TypingRequest(username: String) derives Schema

case class UsernameCheckRequest(username: String) derives Schema
