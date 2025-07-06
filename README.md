# SocketTalk
# 🗨️ Java Multi-Client Chat Application

A simple multi-client chat system using **Java Socket Programming** with features like direct messaging, chat rooms, and a global counter.

---

### ✅ Features

- Unique client names with automatic conflict resolution
- Direct Messaging (`DM`)
- Room-based chat (`CREATE`, `JOIN`, `LEAVE`, `SEND`)
- Shared global counter (`INCREMENT`, `GET`)
- List connected clients (`LIST`)
- Graceful disconnection (`OVER`)
- Multi-threaded server with safe concurrent handling

---

### 💻 Commands

| Command                           | Description                                          |
|-----------------------------------|------------------------------------------------------|
| `INCREMENT`                       | Increment shared counter                             |
| `GET`                             | Show current counter value                           |
| `LIST`                            | List connected clients                               |
| `DM <recipient> <message>`        | Send private message                                 |
| `CREATE <room_name>`              | Create chat room                                     |
| `JOIN <room_name>`                | Join chat room                                       |
| `LEAVE <room_name>`               | Leave chat room                                      |
| `SEND <room_name> <message>`      | Send message to room                                 |
| `OVER`                            | Disconnect                                           |

### ⚙️ How to Run

```bash
# Compile
javac Server.java Client.java

# Run Server
java Server

# Run Client (in separate terminals)
java Client
