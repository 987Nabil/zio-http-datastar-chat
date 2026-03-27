package chat

import zio.*
import zio.stream.*

final case class ChatRoom(
    messages: Ref[List[Message]],
    hub: Hub[Unit],
    typingUsers: Ref[Set[String]],
  )

object ChatRoom:

  def make: ZIO[Any, Nothing, ChatRoom] =
    for
      messages    <- Ref.make(List.empty[Message])
      hub         <- Hub.unbounded[Unit]
      typingUsers <- Ref.make(Set.empty[String])
    yield ChatRoom(messages, hub, typingUsers)

  private def changed: ZIO[ChatRoom, Nothing, Unit] =
    ZIO.serviceWithZIO[ChatRoom](_.hub.publish(()).unit)

  def addMessage(msg: Message): ZIO[ChatRoom, Nothing, Unit] =
    ZIO.serviceWithZIO[ChatRoom] { room =>
      room.messages.update(_ :+ msg) *> changed
    }

  def getMessages: ZIO[ChatRoom, Nothing, List[Message]] =
    ZIO.serviceWithZIO[ChatRoom](_.messages.get)

  def subscribe: ZIO[ChatRoom & Scope, Nothing, UStream[Unit]] =
    ZIO.serviceWithZIO[ChatRoom] { room =>
      room.hub.subscribe.map(ZStream.fromQueue(_))
    }

  def deleteMessage(messageId: String): ZIO[ChatRoom, Nothing, Unit] =
    ZIO.serviceWithZIO[ChatRoom] { room =>
      room.messages.update(_.filterNot(_.id == messageId)) *> changed
    }

  def setTyping(username: String): ZIO[ChatRoom, Nothing, Unit] =
    ZIO.serviceWithZIO[ChatRoom] { room =>
      room.typingUsers.update(_ + username) *> changed
    }

  def clearTyping(username: String): ZIO[ChatRoom, Nothing, Unit] =
    ZIO.serviceWithZIO[ChatRoom] { room =>
      room.typingUsers.update(_ - username) *> changed
    }

  def getTypingUsers: ZIO[ChatRoom, Nothing, Set[String]] =
    ZIO.serviceWithZIO[ChatRoom](_.typingUsers.get)

  val layer: ZLayer[Any, Nothing, ChatRoom] =
    ZLayer.fromZIO(make)
