package chat

import zio.*
import zio.http.*
import zio.http.datastar.{*, given}
import zio.http.template2.*

import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneId}

object ChatServer extends ZIOAppDefault:

  private val $name      = Signal[String]("username")
  private val $message   = Signal[String]("message")
  private val $connected = Signal[Boolean]("connected")
  private val $isSending = Signal[Boolean]("isSending")

  private def chatPage(
      messages:    List[Message],
      typingUsers: Set[String],
    ): Dom = html(
    head(
      datastarScript,
      meta(charset := "UTF-8"),
      meta(name    := "viewport", content := "width=device-width, initial-scale=1.0"),
      title("ZIO Chat - Real-time Multi-Client Chat"),
      style.inlineResource("chat.css"),
    ),
    body(
      dialog(
        `class`               := "username-dialog",
        `open`,
        dataShow              := js"!${$connected}",
        h2("Welcome to ZIO Chat"),
        p(`class`              := "modal-subtitle", "Choose a username to join"),
        input(
          `type`               := "text",
          placeholder          := "Enter username...",
          dataBind("username"),
          dataOn.keydown       := 
            js"evt.code === 'Enter' && ${$name}.length > 0 && @get('/chat/messages')",
          dataIndicator($connected),
        ),
        button(
          `class`              := "join-btn",
          dataAttr("disabled") := js"${$name}.length === 0",
          dataOn.click         := js"@get('/chat/messages')",
          dataIndicator($connected),
          "Join Chat",
        ),
      ),
      div(
        `class`               := "header",
        h1("\uD83D\uDCAC ZIO Chat"),
        p(
          "Real-time Multi-Client Chat with ZIO, ZIO HTTP & Datastar",
          span(
            `class`                   := "connection-status",
            dataClass("disconnected") := js"!${$connected}",
            dataText                  := js"${$connected} ? '● CONNECTED' : '● DISCONNECTED'",
          ),
        ),
      ),
      div(
        `class`               := "container",
        dataSignals($name)    := "",
        dataSignals($message) := "",
        div(
          `class` := "chat-container",
          div(`class` := "messages", messages.map(messageTemplate)),
          div(`class` := "typing-indicator", typingText(typingUsers)),
          div(
            `class`   := "input-area",
            input(
              `type`                            := "text",
              placeholder                       := "Type your message...",
              dataBind("message"),
              dataOn.input.throttle(150.millis) := js"@post('/chat/typing')",
              dataOn.keydown                    :=
                js"""if(evt.code === 'Enter' && ${$message}.trim() !== '') {
                     @post('/chat/send')
                     ${$message} = ''
                   }""",
            ),
            button(
              `type`                            := "button",
              dataAttr("disabled")              :=
                js"!${$connected} || ${$name} === '' || ${$message} === ''",
              dataOn.click                      :=
                js"""@post('/chat/send')
                   ${$message} = ''
                """,
              dataIndicator($isSending),
              "Send",
            ),
          ),
        ),
      ),
    ),
  )

  private def typingText(users: Set[String]): Dom =
    users.toList.sorted match
      case Nil        => Dom.empty
      case one :: Nil => span(s"$one is typing...")
      case many       => span(s"${many.mkString(", ")} are typing...")

  private def messageTemplate(msg: Message): Dom =
    val time = Instant
      .ofEpochMilli(msg.timestamp)
      .atZone(ZoneId.systemDefault())
      .format(DateTimeFormatter.ofPattern("HH:mm:ss"))

    div(
      `class`                  := "message",
      id                       := s"msg-${msg.id}",
      dataClass("own-message") := js"${$name} === '${msg.username}'",
      dataInit                 := js"el.scrollIntoView()",
      div(
        `class`   := "message-header",
        span(`class`   := "message-username", msg.username),
        span(
          `class`      := "delete-btn",
          dataShow     := js"${$name} === '${msg.username}'",
          dataOn.click := js"@post('/chat/delete/${msg.id}')",
          "✕",
        ),
        span(`class`   := "message-time", time),
      ),
      div(`class` := "message-content", msg.content),
    )

  private def render(currentUser: String) =
    for
      messages    <- ChatRoom.getMessages
      typingUsers <- ChatRoom.getTypingUsers
    yield chatPage(messages, typingUsers - currentUser)

  private val routes = Routes(
    Method.GET / "chat"                            -> handler {
      render("").map(Response.html)
    },
    Method.GET / "chat" / "messages"               -> events {
      handler { (req: Request) =>
        for
          rq     <- req.readSignals[JoinRequest].orElseSucceed(JoinRequest(""))
          page   <- render(rq.username)
          _      <- ServerSentEventGenerator.patchElements(page)
          stream <- ChatRoom.subscribe
          _      <- stream.mapZIO { _ =>
                      render(rq.username).flatMap(ServerSentEventGenerator.patchElements)
                    }.runDrain
        yield ()
      }
    },
    Method.POST / "chat" / "send"                  ->
      handler { (req: Request) =>
        for
          rq <- req.readSignals[MessageRequest]
          _  <- ZIO.when(rq.message.trim.nonEmpty) {
                  ChatRoom.addMessage(Message(rq.username, rq.message))
                }
          _  <- ChatRoom.clearTyping(rq.username)
        yield Response.ok
      },
    Method.POST / "chat" / "typing"                ->
      handler { (req: Request) =>
        for
          rq <- req.readSignals[TypingRequest]
          _  <- ChatRoom.setTyping(rq.username)
          _  <- ChatRoom.clearTyping(rq.username).delay(3.seconds).forkDaemon
        yield Response.ok
      },
    Method.POST / "chat" / "delete" / string("id") ->
      handler { (messageId: String, _: Request) =>
        for _ <- ChatRoom.deleteMessage(messageId)
        yield Response.ok
      },
  ).sandbox @@ ErrorResponseConfig.debug @@ Middleware.debug

  override def run: ZIO[Any, Throwable, Unit] =
    Server
      .serve(routes)
      .provide(
        Server.defaultWith(
          _.responseCompression(
            Server.Config.ResponseCompressionConfig(
              contentThreshold = 0,
              options = IndexedSeq(
                Server.Config.CompressionOptions.brotli(quality = 8, lgwin = 24),
                Server.Config.CompressionOptions.gzip(),
                Server.Config.CompressionOptions.deflate(),
              ),
            )
          )
        ),
        ChatRoom.layer,
      )
