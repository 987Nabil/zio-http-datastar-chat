# ZIO HTTP Datastar Chat

A real-time multi-client chat application built with **Scala 3**, **ZIO HTTP**, and **Datastar**.

Demo project for the talk *"Backend driven Frontends: Modern, Powerful and Blazingly Fast with Datastar & zio-http"*.

## Features

- **Real-time** — Push-based updates via Server-Sent Events (SSE)
- **Multi-client** — Multiple users chat simultaneously via ZIO Hub
- **Zero client-side JS** — Everything is `data-*` attributes, no `<script>` tags
- **Full-page morph** — One template renders everything, Datastar diffs the DOM
- **Brotli compression** — Up to 200:1 for repetitive HTML over SSE
- **~280 lines of Scala** — Minimal, demo-friendly code

## Tech Stack

- **Scala 3.7.4** with modern syntax
- **ZIO HTTP 3.10.0** with built-in Datastar SDK
- **Datastar** (~11 KiB) — Hypermedia + reactivity in one package

## Running

```bash
sbt run
```

Open http://localhost:8080/chat — open multiple tabs to test multi-client.

## Project Structure

```
src/main/scala/chat/
  ChatServer.scala      # Page template, routes, SSE handler
  ChatRoom.scala        # Hub[Unit] broadcasting, Ref-based state
  Message.scala         # Simple case class
  MessageRequest.scala  # Request types with ZIO Schema
src/main/resources/
  chat.css              # Dark theme styles
```

## API

| Method | Path             | Description              |
|--------|------------------|--------------------------|
| GET    | /chat            | Serve the chat page      |
| GET    | /chat/messages   | SSE stream (morph-based) |
| POST   | /chat/send       | Send a message           |
| POST   | /chat/typing     | Typing indicator         |
| POST   | /chat/delete/:id | Delete a message         |

## How It Works

1. `chatPage(messages, typingUsers)` renders the full HTML page
2. GET `/chat` returns it as HTML
3. GET `/chat/messages` opens an SSE connection — on every state change, re-renders and morphs the page
4. `Hub[Unit]` signals "something changed" — the SSE handler re-fetches state and sends the updated page

## License

See LICENSE file for details.
