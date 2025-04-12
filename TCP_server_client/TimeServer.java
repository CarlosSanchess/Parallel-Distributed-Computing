import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;
import Model.*;

public class TimeServer {
    private List<Room> rooms;
    private int port;
    private List<Client> notInRoom;
    private int noClients;
    
    private final ReentrantLock roomLock = new ReentrantLock();
    private final Condition roomAvailable = roomLock.newCondition();
    private final ExecutorService virtualThreadExecutor = 
        Executors.newVirtualThreadPerTaskExecutor();

    public TimeServer(int port) {
        this.rooms = new ArrayList<>();
        this.port = port;
        this.notInRoom = new ArrayList<>();
        this.noClients = 0;
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java TimeServer <port>");
            return;
        }

        int port = Integer.parseInt(args[0]);
        TimeServer server = new TimeServer(port);
        server.start();
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(this.port)) {
            System.out.println("Server is listening on port " + this.port);

            while (true) {
                Socket socket = serverSocket.accept();
                virtualThreadExecutor.submit(() -> handleRequest(socket));
            }
        } catch (IOException ex) {
            System.out.println("Server exception: " + ex.getMessage());
            ex.printStackTrace();
        } finally {
            virtualThreadExecutor.shutdown();
        }
    }

    private void handleRequest(Socket sockClient) {
        try {
            Client c = performAuth(sockClient);
            if (c != null) {
                return;
            }
        } catch (IOException e) {
            System.out.println("Client handling error: " + e.getMessage());
        } finally {
            try {
                sockClient.close();
            } catch (IOException e) {
                System.out.println("Error closing client socket: " + e.getMessage());
            }
        }
    }

    private Client performAuth(Socket sockClient) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(sockClient.getInputStream()));
        PrintWriter writer = new PrintWriter(sockClient.getOutputStream(), true);

       

        writer.println("Choose an option:");
        writer.println("1. Register");
        writer.println("2. Login");
        String choice = reader.readLine();

        if (choice == null || (!choice.equals("1") && !choice.equals("2"))) {
            writer.println("Invalid choice");
            return null;
        }

        writer.println("Enter your username:");
        String username = reader.readLine();

        if (username == null || username.trim().isEmpty()) {
            writer.println("Invalid username");
            return null;
        }

        writer.println("Enter your password:");
        String password = reader.readLine();

        if (password == null || password.trim().isEmpty()) {
            writer.println("Invalid password");
            return null;
        }

        roomLock.lock();
        try {
            boolean usernameTaken = false; //TODO

            if (choice.equals("1")) { // Register
                if (usernameTaken) {
                    writer.println("Username already taken");
                    return null;
                }
                
                Client c = new Client(noClients,sockClient.getInetAddress(), username, password);
                noClients++;

                notInRoom.add(c);            
                writer.println("Registration successful. Welcome " + username);
                return c;
            } 
           // else { // Login
                // if (usernameTaken) {
                //     writer.println("Username already in use");
                //     return null;
                // }
                
                // String storedPassword = userCredentials.get(username);
                // if (storedPassword == null || !storedPassword.equals(password)) {
                //     writer.println("Invalid username or password");
                //     return null;
                // }
                
                // writer.println("Login successful. Welcome back " + username);
            //}

            Client newClient = new Client(noClients,sockClient.getInetAddress(), username, password);
            noClients++;
            notInRoom.add(newClient);
            return newClient;
        } finally {
            roomLock.unlock();
        }
    }

    // private void handleClientSession(Client client, Socket sockClient) throws IOException {
    //     BufferedReader reader = new BufferedReader(new InputStreamReader(sockClient.getInputStream()));
    //     PrintWriter writer = new PrintWriter(sockClient.getOutputStream(), true);

    //     try {
    //         while (true) {
    //             writer.println("Options: [1] Create room [2] Join room [3] List rooms [4] Exit");
    //             String choice = reader.readLine();

    //             if (choice == null) {
    //                 break; // Client disconnected
    //             }

    //             switch (choice) {
    //                 case "1":
    //                     createRoom(reader, writer, client.getName());
    //                     break;
    //                 case "2":
    //                     // Implement join room logic
    //                     break;
    //                 case "3":
    //                     listRooms(writer);
    //                     break;
    //                 case "4":
    //                     return;
    //                 default:
    //                     writer.println("Invalid option");
    //             }
    //         }
    //     } finally {
    //         cleanupClient(client);
    //     }
    // }

    // private void createRoom(BufferedReader reader, PrintWriter writer, String clientId) throws IOException {
    //     writer.println("Enter new room name:");
    //     String roomName = reader.readLine();
    //     if (roomName == null || roomName.trim().isEmpty()) {
    //         writer.println("Invalid room name");
    //         return;
    //     }

    //     writer.println("How many members can the room have?");
    //     int noMembers;
    //     try {
    //         noMembers = Integer.parseInt(reader.readLine());
    //         if (noMembers <= 0) {
    //             writer.println("Error: Member count must be positive");
    //             return;
    //         }
    //     } catch (NumberFormatException | IOException e) {
    //         writer.println("Error: Please enter a valid number");
    //         return;
    //     }

    //     writer.println("AI Integration in bot? [y/n]");
    //     boolean aiRoom;
    //     String aiResponse = reader.readLine().toLowerCase();
    //     if (aiResponse.equals("y")) {
    //         aiRoom = true;
    //     } else if (aiResponse.equals("n")) {
    //         aiRoom = false;
    //     } else {
    //         writer.println("Error: Please answer with 'y' or 'n'");
    //         return;
    //     }

    //     roomLock.lock();
    //     try {
    //         boolean exists = rooms.stream()
    //             .anyMatch(r -> r.getName().equals(roomName));

    //         if (exists) {
    //             writer.println("Room name already exists");
    //         } else {
    //             Room newRoom = new Room(roomName, noMembers, aiRoom);
    //             Client client = findClient(clientId);
    //             if (client != null) {
    //                 newRoom.addClient(client);
    //                 rooms.add(newRoom);
    //                 notInRoom.removeIf(c -> c.getName().equals(clientId));
    //                 writer.println("Created and joined room: " + roomName);
    //             } else {
    //                 writer.println("Error: Client not found");
    //             }
    //         }
    //     } finally {
    //         roomLock.unlock();
    //     }
    // }

}