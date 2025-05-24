# Java TCP Chat System with AI Rooms

## Overview

This project is a Java-based client-server chat application built over TCP, supporting multiple authenticated users and chat rooms. It also integrates AI-powered rooms using a local LLM (e.g., Ollama). The system is designed with concurrency, fault tolerance, and secure communication.
### TimeServer

The `TimeServer` class is the core of the backend infrastructure. It handles:

- **Client Connections**: Listens for incoming socket connections and handles each client using Java virtual threads for scalability.
- **Authentication**: Manages user registration, login, and token-based session handling.
- **Room Management**:
  - Users can join, create, or leave rooms.
  - Each room has configurable properties like name, max members, and whether it is AI-enabled.
- **AI Integration**:
  - For AI-enabled rooms, messages are asynchronously processed using the `AIIntegration` utility.
  - Bot responses are added to the room timeline after retrieving output from the local LLM.
- **Token Management**: Utilizes a background `TokenCleanupTask` to automatically remove expired session tokens.
- **Concurrency Control**: Uses `ReentrantLock` to ensure thread-safe access to shared resources (e.g., rooms, clients).
- **Graceful Shutdown**: Ensures sockets and threads are cleanly closed upon termination.

### TimeClient

The `TimeClient` class (not fully shown here, but assumed to exist based on the server) would be responsible for:

- Connecting to the `TimeServer` using TCP sockets.
- Handling user input via command-line or GUI.
- Managing client-side authentication and token storage.
- Sending and receiving serialized `Package` objects to/from the server.
- Rendering room views and handling chat input/output, including interactions with AI bots in AI rooms.

---
## Features

### 1. Authentication and User Management
- Users must authenticate using a username and password to access the chat system.
- Passwords are **hashed** before storage and verification, ensuring secure credential management.
- User registration data is persisted in a file.
- After successful login, the server issues a **session token** that the client stores in memory.
- On reconnection, clients use this token to restore the session without needing to re-enter credentials.

### 2. Token-Based Session Management
- A custom `Package` class is used for sending messages between the clients and the server, it includes the message and the token.
- The Package is transmitted as an **encoded string** and parsed by the server or client.
- This allows the server to recognize returning users and restore their sessions, including current room state.
- Tokens last 1 hour, since there is a thread in the server, checking if the tokens are valid.

### 3. Chat Rooms
- Authenticated users can view, join, and create chat rooms.
- Room messages appear with a time stamp associated
- Room entry and exit events are displayed to all users in the room.

### 4. AI Rooms

- Users can create **AI-enabled chat rooms** by providing a custom prompt during room creation.
- These rooms interact with a **local Large Language Model (LLM)**, such as **Ollama running LLaMA 3**, via HTTP.
- When a user sends a message in an AI room:
  - The full **context** (including the prompt and previous messages) is associated.
  - A request is sent to the AI model, and its response is posted back to the room under the username **`Bot`**.

- AI interaction is handled through a  `AIIntegration` class with the following features:

  - **Asynchronous execution** there is a dedicated Java **virtual threads**.
  -  **Automatic retries** (up to 2 times) in case of temporary failures.
  -  **Polling mechanism** to monitor Ollama availability and automatically resume AI operations when available.
  -  **Caching** of AI responses for 10 minutes to reduce redundant queries and improve response times.
  -  **Structured logging** to a file (`ai_integration.log`) for debugging and auditing AI interactions.

- Requests to the LLM are structured as JSON:
  ```json
  {
    "model": "llama3",
    "prompt": "Full conversation context here...",
    "stream": false
  }

### 5. Concurrency
- Thread-safe access is implemented using `java.util.concurrent.locks`, avoiding pre-built thread-safe collections.
- The system uses **Java Virtual Threads** (Java SE 21+) to handle many concurrent clients with minimal resource overhead.
- Proper synchronization ensures **no race conditions** occur on shared data structures.

### 6. Fault Tolerance
- The client automatically attempts to **reconnect** if the TCP connection is lost.
- Upon reconnecting, the client sends the stored **session token** to the server.
- The server uses this token to restore the user’s previous session, including active chat room, without requiring re-authentication.
- **User credentials are not cached** by the client; only session tokens are reused securely.

### 7. Secure Communication
- The system supports optional **SSL encryption** using the `javax.net.ssl` package.
- This ensures secure communication between the client and server, protecting user data in transit.

## Setup

### Requirements
- Java SE 21 or higher
- Ollama or another local LLM service (for AI room support)

### Running the Server
```bash
java ChatServer