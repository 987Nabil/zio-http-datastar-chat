package chat

import zio.*
import zio.stream.*

case class ChatRoom(
    messages: Ref[List[Message.Chat]],
    hub: Hub[Message],
    typingUsers: Ref[Set[String]],
  )

object ChatRoom:

  def make: ZIO[Any, Nothing, ChatRoom] =
    for
      messages        <- Ref.make(List.empty[Message.Chat])
      hub             <- Hub.unbounded[Message]
      typingUsers     <- Ref.make(Set.empty[String])
    yield ChatRoom(messages, hub, typingUsers)

  def addMessage(msg: Message.Chat): ZIO[ChatRoom, Nothing, Unit] =
    ZIO.serviceWithZIO[ChatRoom] { room =>
      for
        _ <- room.messages.update(_ :+ msg)
        _ <- room.hub.publish(msg)
      yield ()
    }

  def getMessages: ZIO[ChatRoom, Nothing, List[Message.Chat]] =
    ZIO.serviceWithZIO[ChatRoom](_.messages.get)

  def subscribe: ZIO[ChatRoom & Scope, Nothing, UStream[Message]] =
    ZIO.serviceWithZIO[ChatRoom] { room =>
      room.hub.subscribe.map(ZStream.fromQueue(_))
    }

  def deleteMessage(messageId: String): ZIO[ChatRoom, Nothing, Unit] =
    ZIO.serviceWithZIO[ChatRoom] { room =>
      for
        _ <- room.messages.update(_.filterNot(_.id == messageId))
        _ <- room.hub.publish(Message.Deletion(messageId))
      yield ()
    }

  def setTyping(username: String): ZIO[ChatRoom, Nothing, Unit] =
    ZIO.serviceWithZIO[ChatRoom] { room =>
      for
        users <- room.typingUsers.updateAndGet(_ + username)
        _     <- room.hub.publish(Message.Typing(users))
      yield ()
    }

  def clearTyping(username: String): ZIO[ChatRoom, Nothing, Unit] =
    ZIO.serviceWithZIO[ChatRoom] { room =>
      for
        users <- room.typingUsers.updateAndGet(_ - username)
        _     <- room.hub.publish(Message.Typing(users))
      yield ()
    }

  val layer: ZLayer[Any, Nothing, ChatRoom] =
    ZLayer.fromZIO(make)
