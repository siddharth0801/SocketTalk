import java.io.*;
import java.net.*;
import java.util.Scanner;

public class Client {
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private volatile boolean isRunning = true;

    public Client(String address, int port) {
        try {
            socket = new Socket(address, port);
            in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            out = new DataOutputStream(socket.getOutputStream());

            Scanner scanner = new Scanner(System.in);
            System.out.print("Enter your preferred name: ");
            String name = scanner.nextLine();
            out.writeUTF(name);
            System.out.println(in.readUTF());

            // Thread to listen for server messages
            Thread listenerThread = new Thread(this::listenForMessages);
            listenerThread.start();

            // Main thread for sending user input
            while (isRunning) {
                String command = scanner.nextLine();
                out.writeUTF(command);
                if ("OVER".equalsIgnoreCase(command)) {
                    isRunning = false;
                    break;
                }
            }

            listenerThread.join();
        } catch (IOException | InterruptedException e) {
            System.out.println("Client error: " + e.getMessage());
        } finally {
            close();
        }
    }

    private void listenForMessages() {
        try {
            while (isRunning) {
                String response = in.readUTF();
                System.out.println(response);
            }
        } catch (IOException e) {
            if (isRunning) {
                System.out.println("Connection lost: " + e.getMessage());
            }
        } finally {
            isRunning = false;
        }
    }

    private void close() {
        try {
            if (socket != null) socket.close();
            if (in != null) in.close();
            if (out != null) out.close();
        } catch (IOException e) {
            System.out.println("Error closing client: " + e.getMessage());
        }
        System.out.println("Client disconnected.");
    }

    public static void main(String[] args) {
        new Client("localhost", 5000);
    }
}
