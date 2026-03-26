package chat

import zio.*
import zio.http.*
import zio.http.datastar.{*, given}

import zio.http.template2.*

import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneId}

object ChatServer extends ZIOAppDefault:

  private val $name  = Signal[String]("username")
  private val $message   = Signal[String]("message")
  private val $connected = Signal[Boolean]("connected")
  private val $isSending = Signal[Boolean]("isSending")

  private val chatPage: Dom = html(
    head(
      datastarScript,
      meta(charset := "UTF-8"),
      meta(name    := "viewport", content := "width=device-width, initial-scale=1.0"),
      title("ZIO Chat - Real-time Multi-Client Chat"),
      style.inlineResource("chat.css"),
    ),
    body(
      dialog(
        `class`  := "username-dialog",
        `open`,
        dataShow := js"!${$connected}",
        h2("Welcome to ZIO Chat"),
        p(`class` := "modal-subtitle", "Choose a username to join"),
        div(
          `class` := "username-input-group",
          input(
            `type`         := "text",
            placeholder    := "Enter username...",
            dataBind("username"),
            dataOn.keydown := js"evt.code === 'Enter' && ${$name}.length > 0 && @get('/chat/messages')",
            dataIndicator($connected),
          ),
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
        `class` := "header",
        h1("\uD83D\uDCAC ZIO Chat"),
        p(
          "Real-time Multi-Client Chat with ZIO, ZIO HTTP & Datastar",
          span(
            `class`                   := "connection-status",
            dataClass("disconnected") := js"!${$connected}",
            dataText := js"${$connected} ? '● CONNECTED' : '● DISCONNECTED'",
          ),
        ),
      ),
      div(
        `class`               := "container",
        dataSignals($name)    := "",
        dataSignals($message) := "",
        div(
          `class` := "chat-container",
          div(`class` := "messages", id := "messages"),
          div(`class` := "typing-indicator", id := "typing-indicator"),
          div(
            `class` := "input-area",
            input(
              `type`                            := "text",
              placeholder                       := "Type your message...",
              dataBind("message"),
              dataOn.input.throttle(150.millis) := js"@post('/chat/typing')",
              dataOn.keydown                    :=
                js"""if(evt.code === 'Enter') {
                       @post('/chat/send')
                       ${$message} = ''
                     }""",
            ),
            button(
              `type`               := "button",
              dataAttr("disabled") := js"!${$connected} || ${$name} === '' || ${$message} === ''",
              dataOn.click         :=
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

  private def typingText(users: Set[String], exclude: String): String =
    val others = (users - exclude).toList.sorted
    others match
      case Nil        => ""
      case one :: Nil => s"$one is typing..."
      case many       => s"${many.mkString(", ")} are typing..."

  private def messageTemplate(msg: Message.Chat): Dom =
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
        `class` := "message-header",
        span(`class` := "message-username", msg.username),
        span(
          `class`      := "delete-btn",
          dataShow     := js"${$name} === '${msg.username}'",
          dataOn.click := js"@post('/chat/delete/${msg.id}')",
          "✕",
        ),
        span(`class` := "message-time", time),
      ),
      div(`class` := "message-content", msg.content),
    )

  private val routes = Routes(
    Method.GET / "chat"                            -> handler {
      Response.text(chatPage.render).addHeader("Content-Type", "text/html")
    },
    Method.GET / "chat" / "messages"               -> events {
      handler { (req: Request) =>
        for
          rq         <- req.readSignals[JoinRequest].orElseSucceed(JoinRequest(""))
          currentUser = rq.username
          messages   <- ChatRoom.getMessages
          _          <- ServerSentEventGenerator.patchElements(
                          messages.map(messageTemplate),
                          PatchElementOptions(
                            selector = Some(id("messages")),
                            mode = ElementPatchMode.Inner,
                          ),
                        )
          stream     <- ChatRoom.subscribe
          _          <- stream.mapZIO {
                          case msg: Message.Chat              =>
                            ServerSentEventGenerator.patchElements(
                              messageTemplate(msg),
                              PatchElementOptions(
                                selector = Some(id("messages")),
                                mode = ElementPatchMode.Append,
                              ),
                            )
                          case Message.Typing(users)          =>
                            val text = typingText(users, currentUser)
                            ServerSentEventGenerator.patchElements(
                              if text.isEmpty then Dom.empty
                              else span(text),
                              PatchElementOptions(
                                selector = Some(id("typing-indicator")),
                                mode = ElementPatchMode.Inner,
                              ),
                            )
                          case Message.Deletion(messageId)    =>
                            ServerSentEventGenerator.patchElements(
                              Dom.empty,
                              PatchElementOptions(
                                selector = Some(id(s"msg-$messageId")),
                                mode = ElementPatchMode.Remove,
                              ),
                            )
                        }.runDrain
        yield ()
      }
    },
    Method.POST / "chat" / "send"                  ->
      handler { (req: Request) =>
        for
          rq <- req.readSignals[MessageRequest]
          msg = Message.chat(rq.username, rq.message)
          _  <- ChatRoom.addMessage(msg)
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
