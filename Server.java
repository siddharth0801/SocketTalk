import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.*;

public class Server {
    private ServerSocket serverSocket;
    private volatile boolean isRunning = true;
    private final ConcurrentHashMap<DataOutputStream, String> clientNames = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<DataOutputStream> clientOutputs = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<DataOutputStream>> rooms = new ConcurrentHashMap<>();
    private final AtomicInteger counter = new AtomicInteger(0);
    private final ExecutorService executor = Executors.newFixedThreadPool(10);

    public Server(int port) {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Server started on port " + port);

            Runtime.getRuntime().addShutdownHook(new Thread(this::stop));

            while (isRunning) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("Client connected: " + clientSocket.getRemoteSocketAddress());
                    executor.submit(() -> handleClient(clientSocket));
                } catch (SocketException e) {
                    if (isRunning) {
                        System.out.println("Server socket error: " + e.getMessage());
                    } else {
                        System.out.println("Server stopped accepting new connections.");
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("Server error: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private void stop() {
        System.out.println("\nInitiating graceful shutdown...");
        isRunning = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.out.println("Error closing server socket: " + e.getMessage());
        }
        cleanup();
    }

    private void cleanup() {
        executor.shutdown();
        System.out.println("Executor shutdown initiated.");
    }

    private void handleClient(Socket socket) {
        DataOutputStream out = null;
        try {
            DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            out = new DataOutputStream(socket.getOutputStream());

            String preferredName = in.readUTF();
            String uniqueName = generateUniqueName(preferredName);

            clientNames.put(out, uniqueName);
            clientOutputs.add(out);
            out.writeUTF("Your unique name: " + uniqueName);
            System.out.println("Client " + uniqueName + " connected from " + socket.getRemoteSocketAddress());

            while (true) {
                String message = in.readUTF();
                if ("OVER".equalsIgnoreCase(message)) {
                    break;
                } else if ("INCREMENT".equalsIgnoreCase(message)) {
                    int newValue = counter.incrementAndGet();
                    broadcast(uniqueName + " incremented counter to " + newValue);
                } else if ("GET".equalsIgnoreCase(message)) {
                    out.writeUTF("Counter Value: " + counter.get());
                } else if ("LIST".equalsIgnoreCase(message)) {
                    listClients(out);
                } else if (message.startsWith("DM")) {
                    handleMessage(message, uniqueName, out);
                } else if (message.startsWith("CREATE")) {
                    handleRoomCreation(message, uniqueName, out);
                } else if (message.startsWith("JOIN")) {
                    String roomName = message.substring(5).trim();
                    if (rooms.containsKey(roomName)) {
                        rooms.get(roomName).add(out);
                        out.writeUTF("Joined room: " + roomName);
                    } else {
                        out.writeUTF("Room '" + roomName + "' does not exist.");
                    }
                } else if (message.startsWith("LEAVE")) {
                    String roomName = message.substring(6).trim();
                    if (rooms.containsKey(roomName) && rooms.get(roomName).remove(out)) {
                        out.writeUTF("Left room: " + roomName);
                    } else {
                        out.writeUTF("You are not in room '" + roomName + "' or it does not exist.");
                    }
                } else if (message.startsWith("SEND")) {
                    handleRoomMessage(message, uniqueName, out);
                } else {
                    out.writeUTF("Available commands:\n" +
                            "INCREMENT\nGET\nLIST\nDM <recipient> <message>\n" +
                            "CREATE <room_name>\nJOIN <room_name>\nLEAVE <room_name>\nSEND <room_name> <message>\nOVER");
                }
            }
        } catch (IOException e) {
            System.out.println("Communication error: " + e.getMessage());
        } finally {
            cleanupClient(out);
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void handleRoomMessage(String message, String uniqueName, DataOutputStream out) {
        String rest = message.substring(5).trim();
        String[] parts = rest.split(" ", 2);
        if (parts.length < 2) {
            try {
                out.writeUTF("Invalid message format. Use: SEND <room_name> <message>");
            } catch (IOException e) {
                System.out.println("Error sending response: " + e.getMessage());
            }
            return;
        }
        String roomName = parts[0].trim();
        String msg = parts[1].trim();
        if (!rooms.containsKey(roomName)) {
            try {
                out.writeUTF("Room '" + roomName + "' does not exist.");
            } catch (IOException e) {
                System.out.println("Error sending response: " + e.getMessage());
            }
            return;
        }
        CopyOnWriteArrayList<DataOutputStream> roomClients = rooms.get(roomName);
        for (DataOutputStream client : roomClients) {
            try {
                if (client == out) {
                    continue;
                }
                client.writeUTF("[Room: " + roomName + "] " + uniqueName + ": " + msg);
            } catch (IOException e) {
                System.out.println("Error sending message to client in room: " + e.getMessage());
                cleanupClient(client);
            }
        }
    }

    private void handleRoomCreation(String message, String uniqueName, DataOutputStream out) throws IOException {
        String room_name = message.substring(6).trim();
        System.out.println("Room creation requested by " + uniqueName + " for room: " + room_name);
        if (room_name.isEmpty()) {
            try {
                out.writeUTF("Room name cannot be empty.");
            } catch (IOException e) {
                System.out.println("Error sending response: " + e.getMessage());
            }
            return;
        }
        room_name = generateUniqueName(room_name);
        rooms.put(room_name, new CopyOnWriteArrayList<>());
        rooms.get(room_name).add(out);
        out.writeUTF("Room '" + room_name + "' created successfully. You are now in this room.");
    }

    private String generateUniqueName(String preferredName) {
        String name = preferredName;
        int suffix = 1;
        while (clientNames.containsValue(name)) {
            name = preferredName + "#" + suffix++;
        }
        return name;
    }

    private void broadcast(String message) {
        for (DataOutputStream client : clientOutputs) {
            try {
                client.writeUTF(message);
            } catch (IOException e) {
                cleanupClient(client);
            }
        }
    }

    private void listClients(DataOutputStream out) throws IOException {
        StringBuilder sb = new StringBuilder("Connected Clients:\n");
        for (String clientName : clientNames.values()) {
            sb.append(" - ").append(clientName).append("\n");
        }
        out.writeUTF(sb.toString().trim());
    }

    private void handleMessage(String message, String senderName, DataOutputStream out) throws IOException {
        String rest = message.substring(7).trim();
        String[] parts = rest.split(" ", 2);
        if (parts.length < 2) {
            out.writeUTF("Invalid message format. Use: DM <recipient> <message>");
            return;
        }
        String recipient = parts[0].trim();
        String msg = parts[1].trim();
        boolean found = false;
        for (DataOutputStream client : clientOutputs) {
            if (clientNames.get(client).equals(recipient)) {
                client.writeUTF(senderName + ": " + msg);
                found = true;
                break;
            }
        }
        if (!found) {
            out.writeUTF("No client with name " + recipient + " found.");
        }
    }

    private void cleanupClient(DataOutputStream out) {
        if (out != null) {
            clientOutputs.remove(out);
            clientNames.remove(out);
        }
    }

    public static void main(String[] args) {
        new Server(5000);
    }
}
