import java.io.*;
import java.net.*;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import utils.AIIntegration;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import Model.*;
import Model.Client.ClientState;
import utils.shaHash;
import utils.outputPrints;
import utils.utils;

public class TimeServer {
    private List<Room> rooms;
    private int port;
    private List<Client> clients;
    private boolean isRunning = true;
    private ServerSocket serverSocket = null;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition available = lock.newCondition();
    private final ExecutorService virtualThreadExecutor = 
        Executors.newVirtualThreadPerTaskExecutor();
    private int nextClientId;
    private final ConcurrentHashMap<String, Client> activeSessions = new ConcurrentHashMap<>();

    public TimeServer(int port) {
        this.rooms = new ArrayList<>();
        this.port = port;
        this.clients = new ArrayList<>();
        initializeNextClientId();
    }

    private void initializeNextClientId() {
        nextClientId = utils.readLastId() + 1;
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java TimeServer <port>");
            return;
        }
        int port = Integer.parseInt(args[0]);
        new TimeServer(port).start();
    }

    private void safeExit() {
        this.isRunning = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing server socket: " + e.getMessage());
        }
        virtualThreadExecutor.shutdown();
        try {
            if (!virtualThreadExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                virtualThreadExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            virtualThreadExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        System.out.println("Server has shut down gracefully");
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(this.port);
            System.out.println("Server is listening on port " + this.port);

            while (isRunning) {
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
            BufferedReader reader = new BufferedReader(new InputStreamReader(sockClient.getInputStream()));
            PrintWriter writer = new PrintWriter(sockClient.getOutputStream(), true);
            
            try {
                String firstLine = reader.readLine();
                Client c = null;
                
                if (firstLine != null && firstLine.startsWith("/reconnect")) {
                    String token = firstLine.split(" ")[1];
                    c = authenticateWithToken(token);
                    if (c != null) {
                        c.setSocket(sockClient);
                        writer.println("/token " + c.getSessionToken());
                        writer.println("Reconnected successfully. Welcome back " + c.getName());
                        continueSession(c, sockClient);
                        return;
                    }
                }
                
                c = performAuth(sockClient, reader, writer);
                if (c == null) return;
                
                if (c.getState() == ClientState.NOT_IN_ROOM) {
                    showMainHub(c, sockClient, reader, writer);
                }
                if (c.getState() == ClientState.IN_ROOM) {
                    showRoom(c, sockClient, c.getRoomId(), reader, writer);
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
        } catch (IOException e) {
            System.out.println("[ERROR] Failed to handle client request: " + e.getMessage());
        }
    }

    private Client authenticateWithToken(String token) {
        lock.lock();
        try {
            Client client = activeSessions.get(token);
            if (client != null) {
                return client;
            }
            
            try {
                Map<String, Client> credentials = readCredentials();
                for (Client c : credentials.values()) {
                    if (token.equals(c.getSessionToken())) {
                        activeSessions.put(token, c);
                        if (!clients.contains(c)) {
                            clients.add(c);
                        }
                        return c;
                    }
                }
            } catch (IOException e) {
                System.out.println("[ERROR] Failed to read credentials: " + e.getMessage());
            }
            return null;
        } finally {
            lock.unlock();
        }
    }

    private void continueSession(Client c, Socket sockClient) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(sockClient.getInputStream()));
            PrintWriter writer = new PrintWriter(sockClient.getOutputStream(), true);
            
            if (c.getState() == ClientState.NOT_IN_ROOM) {
                showMainHub(c, sockClient, reader, writer);
            } else {
                showRoom(c, sockClient, c.getRoomId(), reader, writer);
            }
        } catch (IOException e) {
            System.out.println("Error continuing session: " + e.getMessage());
        }
    }

    private Client performAuth(Socket sockClient, BufferedReader reader, PrintWriter writer) throws IOException {
        outputPrints.cleanClientTerminal(writer);
        writer.println("Choose an option:");
        writer.println("1. Register");
        writer.println("2. Login");
        writer.println("q. Quit");

        String choice = getValidChoice(reader, writer);
        if (choice == null) return null;

        String username = getUsername(reader, writer);
        if (username == null) return null;

        String password = getPassword(reader, writer);
        if (password == null) return null;

        lock.lock();
        try {
            if (choice.equals("1")) {
                return handleRegistration(sockClient, username, password, writer);
            } else {
                return handleLogin(sockClient, username, password, writer);
            }
        } finally {
            lock.unlock();
        }
    }

    private String getValidChoice(BufferedReader reader, PrintWriter writer) throws IOException {
        while (true) {
            String choice = reader.readLine().trim();
            if (choice.equalsIgnoreCase("q")) {
                writer.println("Exiting...");
                utils.safeSleep(500);
                safeExit();
                return null;
            }
            if (choice.equals("1") || choice.equals("2")) {
                return choice;
            }
            writer.println("Invalid choice. Please enter 1 for Register or 2 for Login.");
        }
    }

    private String getUsername(BufferedReader reader, PrintWriter writer) throws IOException {
        while (true) {
            writer.println("Enter your username (or 'q' to quit):");
            String username = reader.readLine().trim();
            if (username.equalsIgnoreCase("q")) {
                return null;
            }
            if (!username.isEmpty()) {
                return username;
            }
            writer.println("Username cannot be empty.");
        }
    }

    private String getPassword(BufferedReader reader, PrintWriter writer) throws IOException {
        while (true) {
            writer.println("Enter your password (or 'q' to quit):");
            String password = reader.readLine().trim();
            if (password.equalsIgnoreCase("q")) {
                return null;
            }
            if (!password.isEmpty()) {
                return password;
            }
            writer.println("Password cannot be empty.");
        }
    }

    private Client handleRegistration(Socket sockClient, String username, String password, PrintWriter writer) {
        try {
            if (isUsernameTaken(username)) {
                writer.println("Username already taken");
                return null;
            }
            
            String hashedPassword = shaHash.toHexString(shaHash.getSHA(password));
            Client c = new Client(nextClientId, sockClient.getInetAddress(), username, hashedPassword, false);
            c.generateNewToken();
            clients.add(c);
            activeSessions.put(c.getSessionToken(), c);
            nextClientId++;
            storingCredentials(c);
            writer.println("/token " + c.getSessionToken());
            writer.println("Registration successful. Welcome " + username);
            System.out.println("[INFO] User " + username + " successfully registered.");
            return c;
        } catch (Exception e) {
            writer.println("Error during registration.");
            return null;
        }
    }

    private Client handleLogin(Socket sockClient, String username, String password, PrintWriter writer) {
        try {
            Map<String, Client> credentials = readCredentials();
            if (!credentials.containsKey(username)) {
                writer.println("Username not found");
                return null;
            }
            
            Client storedClient = credentials.get(username);
            String storedHash = storedClient.getPassword();
            String inputHash = shaHash.toHexString(shaHash.getSHA(password));
            
            if (storedHash.equals(inputHash)) {
                for (Client c : clients) {
                    if (c.getName().equals(username)) {
                        writer.println("/token " + c.getSessionToken());
                        writer.println("User already logged in");
                        return c;
                    }
                }
                
                Client c = new Client(
                    storedClient.getId(),
                    sockClient.getInetAddress(),
                    username,
                    storedHash,
                    false
                );
                
                if (storedClient.getSessionToken() != null) {
                    c.setSessionToken(storedClient.getSessionToken());
                } else {
                    c.generateNewToken();
                    storingCredentials(c);
                }
                
                clients.add(c);
                activeSessions.put(c.getSessionToken(), c);
                writer.println("/token " + c.getSessionToken());
                writer.println("Login successful. Welcome back " + username);
                System.out.println("[INFO] User " + username + " successfully logged in.");
                return c;
            } else {
                writer.println("Invalid password");
                return null;
            }
        } catch (Exception e) {
            writer.println("Error during login.");
            return null;
        }
    }

    private boolean isUsernameTaken(String username) throws IOException {
        return readCredentials().containsKey(username);
    }

    private Map<String, Client> readCredentials() throws IOException {
        Map<String, Client> credentials = new HashMap<>();
        File file = new File("credentials.txt");
        
        if (!file.exists()) {
            return credentials;
        }
        
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 4) {
                    Client client = new Client(
                        Integer.parseInt(parts[0]),
                        InetAddress.getByName(parts[1]),
                        parts[2],
                        parts[3],
                        false
                    );
                    if (parts.length >= 5 && !parts[4].equals("null")) {
                        client.setSessionToken(parts[4]);
                    }
                    credentials.put(client.getName(), client);
                }
            }
        }
        return credentials;
    }

    private void storingCredentials(Client c) throws IOException {
        Map<String, Client> existing = readCredentials();
        existing.put(c.getName(), c);
        
        try (FileWriter writer = new FileWriter("credentials.txt")) {
            for (Client client : existing.values()) {
                writer.write(client.toCredentialString() + "\n");
            }
        }
    }

    private void showMainHub(Client c, Socket sockClient, BufferedReader reader, PrintWriter writer) throws IOException {
        Room testRoom = new Room(0,"TestRoom", 5, false);
        rooms.add(testRoom);

        while (true) {
            utils.safeSleep(500);
            outputPrints.cleanClientTerminal(writer);
            writer.println("Welcome to xchat!");
    
            writer.println("Rooms Available:");
        
            for (int i = 0; i < rooms.size(); i++) {
                Room r = rooms.get(i);
                String roomInfo = (i + 1) + ". " + r.getName() + " [" + r.getMembers().size() + "/" + r.getMaxNumberOfMembers() + "]";
                writer.println(roomInfo);
            }

            writer.println("To join a room, type: /join <room number> or /create to create a room.");
            String input = readLineForFlags(reader, writer);
            if(input.equals("/quit")){
            }
            if(input.equals("/logout")){
                c = null;
                return;
            }
            if(input.equals("/create")){
                handleRoomCreation(reader, writer, c);  
                continue;
            }

            if (!input.startsWith("/join")) {
                writer.println("Invalid command. Use: /join <room number> or /create to create a room");
                continue;
            }

            String[] parts = input.split("\\s+");
            if (parts.length < 2) {
                writer.println("Missing room number. Usage: /join <room number>");
                continue;
            }

            int choice;
            try {
                choice = Integer.parseInt(parts[1]) - 1;
            } catch (NumberFormatException e) {
                writer.println("Invalid room number. Please enter a valid number after /join.");
                continue;
            }

            if (choice < 0 || choice >= rooms.size()) {
                writer.println("No room with that number. Please choose a valid room.");
                continue;
            }
            
            lock.lock();
            try {
                Room selectedRoom = rooms.get(choice);

                if (selectedRoom.getMembers().size() >= selectedRoom.getMaxNumberOfMembers()) {
                    writer.println("That room is full. Please choose another one:");
                    continue;
                }
                
                selectedRoom.addMember(c);
                c.setRoom(selectedRoom.getId());
                writer.println("✅ Joined room: " + selectedRoom.getName());
                System.out.println("[INFO]: " + c.getName() + " joined " + selectedRoom.getName());
                c.setState(ClientState.IN_ROOM);
            } finally {
                lock.unlock();
            }
            break;
        }
    }

    private void showRoom(Client c, Socket sockClient, int roomId, BufferedReader reader, PrintWriter writer) {
        Room room = null;
        lock.lock();
        try {
            for (Room r : rooms) {
                if (r.getId() == roomId) {
                    room = r;
                    break;
                }
            }
        } finally {
            lock.unlock();
        }

        if (room == null) {
            writer.println("Error: Room not found.");
            System.out.println("[ERROR]: Room not found.");
            c.setState(ClientState.NOT_IN_ROOM);
            return;
        }

        try {
            while (true) {
                outputPrints.cleanClientTerminal(writer);
                outputPrints.viewRoom(room, writer);
                
                String response = checkInputWithDelay(reader, 1000);
                
                if (response != null) {
                    if (response.equals("/quit")) {
                        lock.lock();
                        try {
                            room.removeMember(c.getName());
                            c.setRoom(-1);
                            c.setState(ClientState.NOT_IN_ROOM); 
                        } finally {
                            lock.unlock();
                        }
                        writer.println("You have left the room.");
                        break;
                    } else {
                        lock.lock();
                        try {
                            room.addMessage(new Message(c.getName(), response));
                            
                            if (room.getIsAi()) {
                                String aiResponse = AIIntegration.performQuery(response, room.getMessages());
                                room.addMessage(new Message("AI Bot", aiResponse));
                            }
                            
                            System.out.println("[INFO]:" + c.getName() + " sent message in room " + roomId);
                        } finally {
                            lock.unlock();
                        }
                    }
                }
            }
        } finally {
            outputPrints.cleanClientTerminal(writer);
        }
    }

    private String readLineForFlags(BufferedReader reader, PrintWriter writer) {
        try {
            String input = reader.readLine().trim();

            if(input.equals("/help")){
                outputPrints.printHelpPrompt(writer);
                reader.readLine(); //Waiting for Key
                outputPrints.cleanClientTerminal(writer);
                utils.safeSleep(500);
                return null;
            } else {
                return input;
            }
        } catch (IOException e) {
            writer.println("An error occurred while reading input. Please try again.");
            e.printStackTrace();
            return null;
        }
    }

    private void handleRoomCreation(BufferedReader reader, PrintWriter writer, Client c) {
        outputPrints.cleanClientTerminal(writer);
        writer.println("=== Create a New Room ===");
        writer.println("Press 'q' at any time to cancel.\n");
    
        try {
            String name = null;
            while (true) {
                writer.println("Enter the room name: ");
                name = reader.readLine().trim();
    
                if (name.equalsIgnoreCase("q")) {
                    writer.println("❌ Room creation cancelled.");
                    utils.safeSleep(500);
                    return;
                }
    
                if (!name.isEmpty()) {
                    break;
                }
    
                writer.println("⚠️ Room name cannot be empty.");
            }
    
            boolean isAiRoom = false;
            while (true) {
                writer.println("Is this an AI room? (y/n): ");
                String aiResponse = reader.readLine().trim().toLowerCase();
    
                if (aiResponse.equals("q")) {
                    writer.println("❌ Room creation cancelled.");
                    utils.safeSleep(500);
                    return;
                }
    
                if (aiResponse.equals("y")) {
                    isAiRoom = true;
                    break;
                } else if (aiResponse.equals("n")) {
                    isAiRoom = false;
                    break;
                } else {
                    writer.println("⚠️ Please enter 'y' or 'n'.");
                }
            }
    
            int maxMembers;
            while (true) {
                writer.println("Max number of members (-1 for infinite): ");
                String input = reader.readLine().trim();
    
                if (input.equalsIgnoreCase("q")) {
                    writer.println("❌ Room creation cancelled.");
                    return;
                }
    
                try {
                    maxMembers = Integer.parseInt(input);
                    if (maxMembers == -1 || maxMembers > 0) {
                        break;
                    } else {
                        writer.println("⚠️ Please enter -1 or a positive number.");
                    }
                } catch (NumberFormatException e) {
                    writer.println("⚠️ Please enter a valid number.");
                }
            }
    
            lock.lock();
            try {
                int roomId = rooms.size();
                Room r = new Room(roomId, name, maxMembers, isAiRoom);
                rooms.add(r);
                System.out.println("[INFO]: " + c.getName() + " created " + r.getName());
            } finally {
                lock.unlock();
            }
           
            writer.println("\n✅ Room created successfully!");
            utils.safeSleep(500);
            return;
        } catch (IOException e) {
            writer.println("❌ An error occurred during room creation: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String checkInputWithDelay(BufferedReader reader, int delay) {
        long startTime = System.currentTimeMillis();
        String response = null;
        
        while (System.currentTimeMillis() - startTime < delay) {  
            try {
                if (reader.ready()) {
                    response = reader.readLine().trim();
                    break;
                }
            } catch (IOException e) {
                System.out.println("Error reading input: " + e.getMessage());
                e.printStackTrace();
            }
            utils.safeSleep(100);
        }
        return response;
    }
}