package chat

import zio.*
import zio.http.*
import zio.http.datastar.{*, given}
import zio.http.endpoint.Endpoint
import zio.http.template2.*

import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneId}

object ChatServer extends ZIOAppDefault:

  private val $username          = Signal[String]("username")
  private val $message           = Signal[String]("message")
  private val $typing            = Signal[String]("typing")
  private val $isSending         = Signal[Boolean]("isSending")
  private val $connected         = Signal[Boolean]("connected")
  private val $usernameInput     = Signal[String]("usernameInput")
  private val $usernameAvailable = Signal[Boolean]("usernameAvailable")
  private val $joined            = Signal[Boolean]("joined")

  private val chatPage: Dom = html(
    head(
      datastarScript,
      meta(charset := "UTF-8"),
      meta(name    := "viewport", content := "width=device-width, initial-scale=1.0"),
      title("ZIO Chat - Real-time Multi-Client Chat"),
      style.inlineResource("chat.css"),
    ),
    body(
      div(
        dataInit := Endpoint(Method.GET / "chat" / "messages").out[String].datastarRequest(()),
        dataOn.window(
          "datastar-fetch"
        )        := js"(evt.detail.type === 'error' || evt.detail.type === 'retrying' || evt.detail.type === 'retries-failed') ? ${$connected} = false : undefined",
      ),
      div(
        `class`  := "username-overlay",
        dataShow := js"!${$joined}",
      )(
        div(`class` := "username-modal")(
          h2("Welcome to ZIO Chat"),
          p(`class` := "modal-subtitle")("Choose a unique username to join"),
          div(`class` := "username-input-group")(
            input(
              `type`      := "text",
              placeholder := "Enter username...",
              dataBind("usernameInput"),
              dataOn.input.debounce(
                java.time.Duration.ofMillis(300)
              )           := js"${$usernameInput}.length > 0 && @post('/chat/check-username')",
            ),
            span(
              `class`  := "username-status available",
              dataShow := js"${$usernameInput}.length > 0 && ${$usernameAvailable}",
            )("\u2713 Available"),
            span(
              `class`  := "username-status taken",
              dataShow := js"${$usernameInput}.length > 0 && !${$usernameAvailable}",
            )("\u2717 Already taken"),
          ),
          button(
            `class`              := "join-btn",
            dataAttr("disabled") := js"${$usernameInput}.length === 0 || !${$usernameAvailable}",
            dataOn.click         := js"${$username} = ${$usernameInput}; @post('/chat/join')",
          )("Join Chat"),
        )
      ),
      div(`class` := "header")(
        h1("\uD83D\uDCAC ZIO Chat"),
        p(
          "Real-time Multi-Client Chat with ZIO, ZIO HTTP & Datastar",
          span(
            `class`  := "connection-status",
            dataShow := js"${$connected}",
          )("\u25cf CONNECTED"),
          span(
            `class`  := "connection-status disconnected",
            dataShow := js"!${$connected}",
          )("\u25cf DISCONNECTED"),
        ),
      ),
      div(
        `class`                         := "container",
        dataSignals($username)          := "",
        dataSignals($message)           := "",
        dataSignals($typing)            := "",
        dataSignals($isSending)         := false,
        dataSignals($connected)         := false,
        dataSignals($usernameInput)     := "",
        dataSignals($usernameAvailable) := false,
        dataSignals($joined)            := false,
      )(
        div(`class` := "chat-container")(
          div(
            `class` := "messages",
            id      := "messages",
          )(
            div(id := "message-list")
          ),
          div(`class` := "typing-indicator", id := "typing-indicator")(
            span(
              dataText := js"(() => { const others = ${$typing}.split(',').filter(u => u !== '' && u !== ${$username}); if (others.length === 0) return ''; if (others.length === 1) return others[0] + ' is typing...'; return others.join(', ') + ' are typing...'; })()"
            )
          ),
          div(`class` := "input-area")(
            input(
              `type`                                                  := "text",
              id                                                      := "message",
              placeholder                                             := "Type your message...",
              dataBind("message"),
              required,
              dataOn.keydown                                          := js"evt.code === 'Enter' && @post('/chat/send')",
              dataOn.input.debounce(java.time.Duration.ofMillis(300)) := js"@post('/chat/typing')",
            ),
            button(
              `type`               := "submit",
              dataAttr("disabled") := js"(${$username} === '' || ${$message} === '')",
              dataOn.click         := js"@post('/chat/send')",
              dataIndicator($isSending),
            )("Send"),
          ),
        )
      ),
      script(js"""
        // Auto-scroll to bottom when new messages arrive
        const messagesContainer = document.getElementById('messages');
        const observer = new MutationObserver(() => {
          messagesContainer.scrollTop = messagesContainer.scrollHeight;
        });
        observer.observe(messagesContainer, { childList: true, subtree: true });
      """),
    ),
  )

  private def messageTemplate(msg: ChatMessage): Dom =
    val time = Instant
      .ofEpochMilli(msg.timestamp)
      .atZone(ZoneId.systemDefault())
      .format(DateTimeFormatter.ofPattern("HH:mm:ss"))

    div(
      `class`                  := "message",
      id                       := s"msg-${msg.id}",
      dataClass("own-message") := js"${$username} === '${msg.username}'",
    )(
      div(`class` := "message-header")(
        span(`class` := "message-username")(msg.username),
        span(
          `class`      := "delete-btn",
          dataShow     := js"${$username} === '${msg.username}'",
          dataOn.click := js"@post('/chat/delete/${msg.id}')",
        )("✕"),
        span(`class` := "message-time")(time),
      ),
      div(`class` := "message-content")(msg.content),
    )

  private val routes = Routes(
    Method.GET / "chat"                            -> handler {
      Response.text(chatPage.render).addHeader("Content-Type", "text/html")
    },
    Method.GET / "chat" / "messages"               -> events {
      handler {
        for
          messages       <- ChatRoom.getMessages
          _              <- ServerSentEventGenerator.patchElements(
                              messages.map(messageTemplate),
                              PatchElementOptions(
                                selector = Some(id("message-list")),
                                mode = ElementPatchMode.Inner,
                              ),
                            )
          _              <- ServerSentEventGenerator.patchSignals("""{"connected": true}""")
          msgStream      <- ChatRoom.subscribe
          typingStream   <- ChatRoom.subscribeTyping
          deletionStream <- ChatRoom.subscribeDeletions
          _              <- msgStream
                              .mapZIO { message =>
                                ServerSentEventGenerator.patchElements(
                                  messageTemplate(message),
                                  PatchElementOptions(
                                    selector = Some(id("message-list")),
                                    mode = ElementPatchMode.Append,
                                  ),
                                )
                              }
                              .merge(
                                typingStream.mapZIO { users =>
                                  ServerSentEventGenerator.patchSignals(
                                    s"""{"typing": "${users.toList.sorted.mkString(",")}"}"""
                                  )
                                }
                              )
                              .merge(
                                deletionStream.mapZIO { messageId =>
                                  ServerSentEventGenerator.patchElements(
                                    Dom.empty,
                                    PatchElementOptions(
                                      selector = Some(id(s"msg-$messageId")),
                                      mode = ElementPatchMode.Remove,
                                    ),
                                  )
                                }
                              )
                              .runDrain
        yield ()
      }
    },
    Method.POST / "chat" / "send"                  ->
      handler { (req: Request) =>
        for
          rq <- req.readSignals[MessageRequest]
          msg = ChatMessage(username = rq.username, content = rq.message)
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
    Method.POST / "chat" / "check-username"        ->
      handler { (req: Request) =>
        for
          rq        <- req.readSignals[UsernameCheckRequest]
          available <- ChatRoom.isUsernameTaken(rq.username).map(!_)
        yield Response.json(s"""{"usernameAvailable": $available}""")
      },
    Method.POST / "chat" / "join"                  ->
      handler { (req: Request) =>
        for
          rq         <- req.readSignals[UsernameCheckRequest]
          registered <- ChatRoom.registerUser(rq.username)
        yield
          if registered then Response.json("""{"joined": true}""")
          else Response.json("""{"joined": false, "usernameAvailable": false}""")
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
                Server.Config.CompressionOptions.brotli(quality = 8, lgwin = 25),
                Server.Config.CompressionOptions.gzip(),
                Server.Config.CompressionOptions.deflate(),
              ),
            )
          )
        ),
        ChatRoom.layer,
      )
