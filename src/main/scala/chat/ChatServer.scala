package chat

import chat.Util.*
import zio.*
import zio.http.*
import zio.http.datastar.{*, given}
import zio.http.template2.*

import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneId}

object ChatServer extends ZIOAppDefault:

  private val $username = Signal[String]("username")
  private val $message  = Signal[String]("message")

  private val chatPage: Dom = html(
    head(
      meta(charset := "UTF-8"),
      meta(name    := "viewport", content := "width=device-width, initial-scale=1.0"),
      title("ZIO Chat - Real-time Multi-Client Chat"),
      script(
        `type`     := "module",
        defer,
        src        := "https://cdn.jsdelivr.net/gh/starfederation/datastar@1.0.0-RC.5/bundles/datastar.js",
      ),
      style.inlineResource("chat.css"),
    ),
    body(
      dataInit := js"@get('/chat/messages')",
      div(`class` := "header")(
        h1("💬 ZIO Chat"),
        p(
          "Real-time Multi-Client Chat with ZIO, ZIO HTTP & Datastar",
          span(`class` := "connection-status")("● CONNECTED"),
        ),
      ),
      div(
        `class`                := "container",
        dataSignals($username) := "",
        dataSignals($message)  := "",
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
          div(`class` := "input-area")(
            input(
              `type`         := "text",
              id             := "message",
              placeholder    := "Type your message...",
              dataBind("message"),
              required,
              dataOn.keydown := js"evt.code === 'Enter' && @post('/chat/send')",
            ),
            button(
              `type`               := "submit",
              dataAttr("disabled") := js"(${$username} === '' || ${$message} === '')",
              dataOn.click         := js"@post('/chat/send')",
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

    div(`class` := "message")(
      div(`class` := "message-header")(
        span(`class` := "message-username")(msg.username),
        span(`class` := "message-time")(time),
      ),
      div(`class` := "message-content")(msg.content),
    )

  private val routes = Routes(
    Method.GET / "chat"              -> handler {
      Response.text(chatPage.render).addHeader("Content-Type", "text/html")
    },
    Method.GET / "chat" / "messages" -> events {
      handler {
        for
          messages <- ChatRoom.getMessages
          _        <- ServerSentEventGenerator.patchElements(
                        messages.map(messageTemplate),
                        PatchElementOptions(
                          selector = Some(id("message-list")),
                          mode = ElementPatchMode.Inner,
                        ),
                      )
          messages <- ChatRoom.subscribe
          _        <- messages.mapZIO { message =>
                        ServerSentEventGenerator.patchElements(
                          messageTemplate(message),
                          PatchElementOptions(
                            selector = Some(id("message-list")),
                            mode = ElementPatchMode.Append,
                          ),
                        )
                      }.runDrain
        yield ()
      }
    },
    Method.POST / "chat" / "send"    ->
      handler { (req: Request) =>
        for
          rq <- req.body.as[MessageRequest]
          msg = ChatMessage(username = rq.username, content = rq.message)
          _  <- ChatRoom.addMessage(msg)
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
