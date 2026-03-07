package chat

import zio.*
import zio.http.*
import zio.http.datastar.{*, given}
import zio.http.endpoint.Endpoint
import zio.http.template2.*

import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneId}

object ChatServer extends ZIOAppDefault:

  private val $username  = Signal[String]("username")
  private val $message   = Signal[String]("message")
  private val $typing    = Signal[String]("typing")
  private val $isSending = Signal[Boolean]("isSending")

  private val chatPage: Dom = mainPage(
    headContent = Seq(
      meta(charset := "UTF-8"),
      meta(name    := "viewport", content := "width=device-width, initial-scale=1.0"),
      title("ZIO Chat - Real-time Multi-Client Chat"),
      style.inlineResource("chat.css"),
    ),
    bodyContent = Seq(
      div(
        dataInit := Endpoint(Method.GET / "chat" / "messages").out[String].datastarRequest(())
      ),
      div(`class` := "header")(
        h1("\uD83D\uDCAC ZIO Chat"),
        p("Real-time Multi-Client Chat with ZIO, ZIO HTTP & Datastar"),
      ),
      div(
        `class`                 := "container",
        dataSignals($username)  := "",
        dataSignals($message)   := "",
        dataSignals($typing)    := "",
        dataSignals($isSending) := false,
      )(
        div(`class` := "username-section")(
          label(`for` := "username")("Your Username"),
          input(
            `type`      := "text",
            id          := "username",
            placeholder := "Enter your username...",
            dataBind("username"),
          ),
        ),
        div(`class` := "chat-container")(
          div(
            `class` := "messages",
            id      := "messages",
          )(
            div(id := "message-list")
          ),
          div(`class` := "typing-indicator", id := "typing-indicator")(
            span(dataText := js"${$typing}")
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
        ),
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

    div(`class` := "message", id := s"msg-${msg.id}")(
      div(`class` := "message-header")(
        span(`class` := "message-username")(msg.username),
        span(`class` := "message-time")(time),
        span(
          `class`      := "delete-btn",
          dataShow     := js"${$username} === '${msg.username}'",
          dataOn.click := js"@post('/chat/delete/${msg.id}')",
        )("\u2715"),
      ),
      div(`class` := "message-content")(msg.content),
    )

  private def typingText(users: Set[String]): String =
    users.toList.sorted match
      case Nil        => ""
      case one :: Nil => s"$one is typing..."
      case many       => s"${many.mkString(", ")} are typing..."

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
                                    s"""{"typing": "${typingText(users)}"}"""
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
  ).sandbox @@ ErrorResponseConfig.debug @@ Middleware.debug

  override def run: ZIO[Any, Throwable, Unit] =
    Server
      .serve(routes)
      .provide(
        Server.default,
        ChatRoom.layer,
      )
