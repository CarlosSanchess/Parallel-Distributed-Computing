import java.net.*;
import java.util.Scanner;
import java.io.*;

public class TimeClient {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java TimeClient <hostname> <port>");
            return;
        }

        String hostname = args[0];
        int port = Integer.parseInt(args[1]);
        
        try {
            Socket socket = connect(hostname, port);
            if (socket == null) return;
            
            // Start separate thread for reading server responses
            new Thread(new ServerResponseHandler(socket)).start();
            
            // Main thread handles user input
            handleUserInput(socket);
            
        } catch (IOException ex) {
            System.out.println("Connection error: " + ex.getMessage());
        }
    }

    private static Socket connect(String hostname, int port) throws IOException {
        try {
            Socket socket = new Socket(hostname, port);
            System.out.println("Connected to server at " + hostname + ":" + port);
            return socket;
        } catch (UnknownHostException ex) {
            System.out.println("Server not found: " + ex.getMessage());
        } catch (IOException ex) {
            System.out.println("I/O error: " + ex.getMessage());
        }
        return null;
    }

    private static void handleUserInput(Socket socket) {
        try (Scanner scanner = new Scanner(System.in);
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {
            
            System.out.println("Type your messages (type 'exit' to quit):");
            
            while (true) {
                String input = scanner.nextLine();
                writer.println(input);
                
                if ("exit".equalsIgnoreCase(input)) {
                    System.out.println("Disconnecting from server...");
                    break;
                }
            }
        } catch (IOException ex) {
            System.out.println("Error sending message: " + ex.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                System.out.println("Error closing socket: " + e.getMessage());
            }
        }
    }

    private static class ServerResponseHandler implements Runnable {
        private final Socket socket;

        public ServerResponseHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()))) {
                
                String serverResponse;
                while ((serverResponse = reader.readLine()) != null) {
                    System.out.println(serverResponse);
                }
                
                System.out.println("Server has disconnected");
                System.exit(0);
                
            } catch (IOException ex) {
                System.out.println("Error reading server response: " + ex.getMessage());
            }
        }
    }
}