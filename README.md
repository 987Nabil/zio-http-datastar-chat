# ZIO HTTP Datastar Chat

A real-time multi-client chat application built with **Scala 3**, **ZIO**, **ZIO HTTP**, and **Datastar** for push-based updates.

## Features

- 🚀 **Real-time Updates**: Push-based updates using Server-Sent Events (SSE)
- 💬 **Multi-Client Support**: Multiple users can chat simultaneously
- 🎨 **Beautiful Dark UI**: Modern, responsive dark-themed interface
- 🔄 **In-Memory Storage**: Fast, lightweight chat room with ZIO Hub for message broadcasting
- ⚡ **Reactive Frontend**: Datastar.dev for declarative, reactive UI updates without JavaScript frameworks
- 🏗️ **Pure Functional**: Built with ZIO for type-safe, composable effects

## Architecture

### Backend (Scala 3 + ZIO)

- **ChatMessage**: Immutable message model with ZIO Schema for type-safe serialization
- **ChatRoom**: In-memory chat room using ZIO `Ref` for state and `Hub` for broadcasting
- **ChatServer**: ZIO HTTP server with Datastar SDK for SSE-based real-time communication
- **chat.css**: External CSS file loaded using `style.inlineResource`

### Frontend (Datastar)

- **data-star.dev**: Lightweight hypermedia framework for reactive updates
- **SSE Streaming**: Automatic reconnection and live message updates
- **No Build Step**: Pure HTML/CSS with CDN-loaded Datastar

## How It Works

1. **Initial Load**: Client requests `/chat`, receives HTML with embedded styles
2. **Message Fetching**: On page load, fetches existing messages via SSE
3. **Real-time Stream**: Background SSE connection to `/chat/stream` pushes new messages
4. **Message Sending**: Form submission POSTs to `/chat/send`, broadcasts to all clients
5. **Hub Pattern**: ZIO Hub broadcasts messages to all subscribed clients

## Tech Stack

- **Scala 3**: Modern Scala with new syntax
- **ZIO 2.1.21**: Functional effects system
- **ZIO HTTP 3.5.1-SNAPSHOT**: High-performance HTTP server with built-in Datastar SDK
- **ZIO Schema 1.5.0**: Type-safe schema derivation
- **Datastar**: Hypermedia-driven frontend framework (loaded from CDN)

## Running the Application

### Prerequisites

- Java 11 or higher
- SBT (Scala Build Tool)

### Start the Server

#### Option 1: Using sbt-revolver (Recommended for Development)

```bash
sbt ~reStart
```

This will:
- Start the server on **http://localhost:8080**
- Automatically reload when you make code changes
- Run in the background

To stop the server:
```bash
sbt reStop
```

#### Option 2: Standard Run

```bash
sbt run
```

The server will start on **http://localhost:8080**

### Access the Chat

Open your browser and navigate to:

```
http://localhost:8080/chat
```

### Test Multi-Client

Open multiple browser windows/tabs to see real-time chat updates across all clients!

## Project Structure

```
zio-http-datastar-chat/
├── build.sbt                    # Project configuration
├── src/main/scala/chat/
│   ├── ChatMessage.scala        # Message model with JSON codecs
│   ├── ChatRoom.scala           # In-memory chat room with Hub
│   └── ChatServer.scala         # HTTP server and routes
└── README.md
```

## Key Concepts

### ZIO Hub for Broadcasting

```scala
Hub.unbounded[ChatMessage]  // Create broadcast hub
hub.subscribe               // Each client gets a subscription
hub.publish(message)        // Broadcast to all subscribers
```
### ZIO Schema for Type Safety
### Server-Sent Events (SSE)

case class ChatMessage(...)

object ChatMessage:
  given Schema[ChatMessage] = DeriveSchema.gen[ChatMessage]
```

### Server-Sent Events (SSE) with Datastar SDK

```scala
ServerSentEventGenerator.patchElements(
  messages.map(messageTemplate),
  PatchElementOptions(
    selector = Some(id("message-list")),
    mode = ElementPatchMode.Append
    fragments = messages.map(messageTemplate)
  )
)
```

### Datastar Reactive Bindings
- `dataSignals("username") := js""` - Initialize reactive signals
- `dataBind("value", "username")` - Two-way data binding
- `dataOn.submit := js"@post('/chat/send')"` - Form submission with SSE
- `dataOnLoad := js"@get('/chat/messages')"` - Initial data load
- `data-on-load="$$get('/chat/messages')"` - Initial data load
- SSE auto-updates DOM with new message fragments

## API Endpoints

| Method | Path            | Description                      |
|--------|-----------------|----------------------------------|
| GET    | /chat           | Serve the chat HTML page         |
| GET    | /chat/messages  | Get all messages (SSE)           |
| POST   | /chat/send      | Send a new message               |
| GET    | /chat/stream    | SSE stream for real-time updates |

## Customization

### Change Server Port

Modify the server configuration in `ChatServer.scala`:

```scala
Server.serve(routes)
  .provide(
    Server.defaultWithPort(9000),  // Custom port
    ChatRoom.layer
  )
```

### Styling

All CSS is embedded in the HTML template in `ChatServer.scala`. Modify the `<style>` section to customize colors, layout, and animations.

### Message Persistence

Currently uses in-memory storage. To add persistence:

1. Replace `Ref[List[ChatMessage]]` with a database layer
2. Implement a service trait with ZIO
3. Use ZIO layers to provide the implementation

## License

See LICENSE file for details.

## Credits

Built with:
- [ZIO](https://zio.dev/)
- [ZIO HTTP](https://github.com/zio/zio-http)
- [Datastar](https://data-star.dev/)
- [zio-http-datastar by Kit Langton](https://github.com/kitlangton/zio-http-datastar)

