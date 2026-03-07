package chat

import zio.*
import zio.stream.*

case class ChatRoom(
    messages: Ref[List[ChatMessage]],
    subscribers: Hub[ChatMessage],
    typingUsers: Ref[Set[String]],
    typingSubscribers: Hub[Set[String]],
    deletionSubscribers: Hub[String],
    activeUsers: Ref[Set[String]],
  )

object ChatRoom:

  def make: ZIO[Any, Nothing, ChatRoom] =
    for
      messages            <- Ref.make(List.empty[ChatMessage])
      hub                 <- Hub.unbounded[ChatMessage]
      typingUsers         <- Ref.make(Set.empty[String])
      typingSubscribers   <- Hub.unbounded[Set[String]]
      deletionSubscribers <- Hub.unbounded[String]
      activeUsers         <- Ref.make(Set.empty[String])
    yield ChatRoom(messages, hub, typingUsers, typingSubscribers, deletionSubscribers, activeUsers)

  def addMessage(message: ChatMessage): ZIO[ChatRoom, Nothing, Unit] =
    ZIO.serviceWithZIO[ChatRoom] { room =>
      for
        _ <- room.messages.update(_ :+ message)
        _ <- room.subscribers.publish(message)
      yield ()
    }

  def getMessages: ZIO[ChatRoom, Nothing, List[ChatMessage]] =
    ZIO.serviceWithZIO[ChatRoom](_.messages.get)

  def subscribe: ZIO[ChatRoom & Scope, Nothing, UStream[ChatMessage]] =
    ZIO.serviceWithZIO[ChatRoom] { room =>
      room.subscribers.subscribe.map(ZStream.fromQueue(_))
    }

  def deleteMessage(messageId: String): ZIO[ChatRoom, Nothing, Unit] =
    ZIO.serviceWithZIO[ChatRoom] { room =>
      for
        _ <- room.messages.update(_.filterNot(_.id == messageId))
        _ <- room.deletionSubscribers.publish(messageId)
      yield ()
    }

  def subscribeDeletions: ZIO[ChatRoom & Scope, Nothing, UStream[String]] =
    ZIO.serviceWithZIO[ChatRoom] { room =>
      room.deletionSubscribers.subscribe.map(ZStream.fromQueue(_))
    }

  def setTyping(username: String): ZIO[ChatRoom, Nothing, Unit] =
    ZIO.serviceWithZIO[ChatRoom] { room =>
      for
        users <- room.typingUsers.updateAndGet(_ + username)
        _     <- room.typingSubscribers.publish(users)
      yield ()
    }

  def clearTyping(username: String): ZIO[ChatRoom, Nothing, Unit] =
    ZIO.serviceWithZIO[ChatRoom] { room =>
      for
        users <- room.typingUsers.updateAndGet(_ - username)
        _     <- room.typingSubscribers.publish(users)
      yield ()
    }

  def getTypingUsers: ZIO[ChatRoom, Nothing, Set[String]] =
    ZIO.serviceWithZIO[ChatRoom](_.typingUsers.get)

  def subscribeTyping: ZIO[ChatRoom & Scope, Nothing, UStream[Set[String]]] =
    ZIO.serviceWithZIO[ChatRoom] { room =>
      room.typingSubscribers.subscribe.map(ZStream.fromQueue(_))
    }

  def isUsernameTaken(username: String): ZIO[ChatRoom, Nothing, Boolean] =
    ZIO.serviceWithZIO[ChatRoom](_.activeUsers.get.map(_.contains(username)))

  def registerUser(username: String): ZIO[ChatRoom, Nothing, Boolean] =
    ZIO.serviceWithZIO[ChatRoom] { room =>
      room.activeUsers.modify { users =>
        if users.contains(username) then (false, users)
        else (true, users + username)
      }
    }

  def unregisterUser(username: String): ZIO[ChatRoom, Nothing, Unit] =
    ZIO.serviceWithZIO[ChatRoom](_.activeUsers.update(_ - username))

  val layer: ZLayer[Any, Nothing, ChatRoom] =
    ZLayer.fromZIO(make)
