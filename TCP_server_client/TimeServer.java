import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

import Model.*;
import Model.Package;
import Model.Client.ClientState;
import utils.shaHash;
import utils.AIIntegration;
import utils.outputPrints;
import utils.utils;
import utils.TokenCleanupTask;

public class TimeServer {
    private List<Room> rooms;
    private int port;
    private List<Client> clients;
    private boolean isRunning = true;
    private ServerSocket serverSocket = null;
    private final ReentrantLock lock = new ReentrantLock();
    private final ExecutorService virtualThreadExecutor = 
        Executors.newVirtualThreadPerTaskExecutor();
    private int nextClientId;

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
            
            TokenCleanupTask tokenCleanupTask = new TokenCleanupTask(lock);
            Thread.ofVirtual()
                .name("TokenCleanupThread")
                .start(tokenCleanupTask);
            
            while (isRunning) {
                Socket socket = serverSocket.accept();
                virtualThreadExecutor.submit(() -> handleRequest(socket));
            }
            
            tokenCleanupTask.stop();
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
            Client c = null;

            try {
                while(true){
                    if(c == null || c.getState() == ClientState.LOGGED_OUT) { 
                        c = performAuth(sockClient);
                    }
                    if(c.getState() == ClientState.NOT_IN_ROOM){
                        showMainHub(c, sockClient);
                    }
                    if(c.getState() == ClientState.IN_ROOM){
                        showRoom(c, sockClient, c.getRoomId(), reader, writer);
                    }
            }
            } catch (IOException e) {
                System.out.println("Client handling error: " + e.getMessage());
            } finally {
                if (c != null) {
                    handleDisconnect(c, sockClient);
                } else {
                    try {
                        System.out.println("adsa");
                        sockClient.close();
                    } catch (IOException e) {
                        System.out.println("Error closing socket: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("[ERROR] Failed to handle client request: " + e.getMessage());
        }
    }

    private Client performAuth(Socket sockClient) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(sockClient.getInputStream()));
        PrintWriter writer = new PrintWriter(sockClient.getOutputStream(), true);

        outputPrints.cleanClientTerminal(writer);
        writer.println("Choose an option:");
        writer.println("1. Register");
        writer.println("2. Login");
        writer.println("q. Quit");

        Model.Package choice = getValidChoice(reader, writer);
        if (choice == null) return null;

        if(choice.getMessage().equals("2")){
            lock.lock();
            Client c = handleLoginWithToken(sockClient, writer, choice.getToken());
            lock.unlock();
            if(c != null){
                return c;
            }
        }

        String username = getUsername(reader, writer);
        if (username == null) return null;

        String password = getPassword(reader, writer);
        if (password == null) return null;
            lock.lock();
            Client c;
            if (choice.getMessage().equals("1")) {
                c = handleRegistration(sockClient, username, password, writer, reader);
            } else {
                c =  handleLogin(sockClient, username, password, writer);
            }
            lock.unlock();
            return c;
    }

    private Model.Package getValidChoice(BufferedReader reader, PrintWriter writer) throws IOException {
        while (true) {
            Model.Package choice = readInput(reader);
            if (choice.getMessage().equalsIgnoreCase("q")) {
                writer.println("Exiting...");
                utils.safeSleep(500);
                safeExit();
                return null;
            }
            if (choice.getMessage().equals("1") || choice.getMessage().equals("2")) {
                return choice;
            }
            writer.println("Invalid choice. Please enter 1 for Register or 2 for Login.");
        }
    }

    private String getUsername(BufferedReader reader, PrintWriter writer) throws IOException {
        while (true) {
            writer.println("Enter your username (or 'q' to quit):");
            String username = readInput(reader).getMessage();
            System.out.println("User Name" + username);
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
            String password = readInput(reader).getMessage();
            System.out.println("PasssWord" + password);
            if (password.equalsIgnoreCase("q")) {
                return null;
            }
            if (!password.isEmpty()) {
                return password;
            }
            writer.println("Password cannot be empty.");
        }
    }

    private Client handleRegistration(Socket sockClient, String username, String password, PrintWriter writer, BufferedReader reader) {
        try { 

            if (isUsernameTaken(username)) {
                writer.println("Username '" + username + "' is already taken. Enter a new username or 'q' to quit.");
                while (true) {
                    String newUsername = reader.readLine().trim(); 
                    if (newUsername.equalsIgnoreCase("q")) {
                        writer.println("Registration cancelled.");
                        return null;
                    }
                    if (!isUsernameTaken(newUsername)) {
                        username = newUsername; 
                        break; 
                    }
                    writer.println("Username '" + newUsername + "' is taken. Try again or enter 'q' to quit.");
                }
            }
            
            String hashedPassword = shaHash.toHexString(shaHash.getSHA(password));
            Client c = new Client(nextClientId, sockClient.getInetAddress(), username, hashedPassword, false);
            clients.add(c);
            nextClientId++;
            String token = UUID.randomUUID().toString();

            storingCredentials(c, token);

            Model.Package p = new Package("Registration successful. Welcome " + username, token);

            writer.println(p.serialize());
            System.out.println("[INFO] User " + username + " successfully registered.");
            utils.safeSleep(500);

            return c;
        } catch (Exception e) {
            writer.println("Error during registration.");
            return null;
        }
    }

    private Client handleLogin(Socket sockClient, String username, String password, PrintWriter writer) {
        try {  
            Map<String, String[]> credentials = readCredentials();
            if (!credentials.containsKey(username)) {
                writer.println("Username not found");
                utils.safeSleep(500);
                return null;
            }
            
            String[] creds = credentials.get(username);
            String storedHash = creds[3];
            String inputHash = shaHash.toHexString(shaHash.getSHA(password));
            
            String token = UUID.randomUUID().toString();

            if (storedHash.equals(inputHash)) {

                for (Client c : clients) {
                    if (c.getName().equals(username)) {
                        utils.updateOrCreateEntry(token,creds[0], username);
                        writer.println("User already logged in");
                        return c;
                    }
                }
            }
            
            if (storedHash.equals(inputHash)) {
                Client c = new Client(
                    Integer.parseInt(creds[0]),
                    InetAddress.getByName(creds[1]),
                    username,
                    storedHash,
                    false
                );
                clients.add(c);

                utils.updateOrCreateEntry(token,creds[0], username);
                Model.Package p = new Package("Login successful. Welcome back " + username, token);
                writer.println(p.serialize());

                System.out.println("[INFO] User " + username + " successfully logged in.");
                return c;
            } else {
                System.out.println("Invalid Password");
                writer.println("Invalid password");
                utils.safeSleep(500);
                return null;
            }
        } catch (Exception e) {
            System.out.println(e);
            writer.println("Error during login.");
            return null;
        }
    }
    
    private  Client handleLoginWithToken(Socket sockClient,PrintWriter writer,String Token){
        if(Token != null && !Token.isEmpty()){
            Map<String, String[]> tokenRecords = utils.readTokens();
            if (tokenRecords.containsKey(Token)) {
                String[] tokenData = tokenRecords.get(Token);
                String userId = tokenData[0];  
                String name = tokenData[1];
                String timestamp = tokenData[2];  
                
                long currentTime = System.currentTimeMillis() / 1000L;
                if (currentTime > Long.parseLong(timestamp)) { 
                    System.out.println("Current Time :"+ currentTime);
                    System.out.println("Time stamp: " + timestamp);
                    writer.println("Token has expired");
                    System.out.println("[INFO]TOKEN EXPRIED");
                    return null;
                }
                
                // for (Client c : clients) {
                //     if (c.getId() == Integer.parseInt(userId)) {
                //         writer.println("User already logged in");
                //         System.out.println("ALREADY LOGGED IN");

                //         return c;
                //     }
                // }
                
                Client c = new Client(
                    Integer.parseInt(userId),
                    sockClient.getInetAddress(),
                    name, 
                    "", 
                    false
                );
                clients.add(c);
                writer.println("Login successful with token. Welcome back " + name);
                System.out.println("[INFO] User ID " + userId + " successfully logged in with token.");
                
                return c;
            } else {
                writer.println("Invalid token");
                return null;
            }
        }
        return null;
    }
    private boolean isUsernameTaken(String username) throws IOException {
        Map<String, String[]> credentials = readCredentials();
        for (String[] userData : credentials.values()) {
            if (username.equals(userData[2])) {
                return true;
            }
        }
        return false;
    }

    private Map<String, String[]> readCredentials() throws IOException {
        Map<String, String[]> credentials = new HashMap<>();
        File file = new File("credentials.txt");
        
        if (!file.exists()) {
            return credentials;
        }
        
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            Package pkg;
            while ((pkg = readInput(reader)) != null) { 
                line = pkg.getMessage();               
                if (line != null) {                     
                    String[] parts = line.split(",");
                    if (parts.length >= 4) {
                        credentials.put(parts[2], parts);
                    }
                }
            }
        }
        return credentials;
    }

    private void storingCredentials(Client c, String token) throws IOException {
        String data = String.join(",",
            String.valueOf(c.getId()),
            c.getInetaddr().getHostAddress(),
            c.getName(),
            c.getPassword()
        );
        
        String cookie = String.join(",", 
        String.valueOf(c.getId()),
        c.getName(),
        token,
        String.valueOf(System.currentTimeMillis() / 1000L + 3600)
        );

        try (FileWriter writer = new FileWriter("credentials.txt", true)) {
            writer.write(data + "\n");
        }
        try (FileWriter writer = new FileWriter("tokens.txt", true)) {
            writer.write(cookie + "\n");
        }
    }
    
    private void showMainHub(Client c, Socket sockClient) throws IOException {
        Room testRoom = new Room(rooms.size(),"TestRoom", 5, false); // name: TestRoom, max 5 members, not AI
        rooms.add(testRoom);
        BufferedReader reader = new BufferedReader(new InputStreamReader(sockClient.getInputStream()));
        PrintWriter writer = new PrintWriter(sockClient.getOutputStream(), true);
    
    

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
            String input = checkInputWithDelay(reader, 1000);
            if(input == null){continue;}

            if (handleMainHubCommand(input, c, reader, writer, sockClient)) {
                return;
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
                    Room selectedRoom = rooms.get(choice);

                    if (selectedRoom.getMembers().size() >= selectedRoom.getMaxNumberOfMembers()) {
                        writer.println("That room is full. Please choose another one:");
                        lock.unlock();
                        continue;
                    }
                    
                    selectedRoom.addMember(c);
                    c.setRoom(selectedRoom.getId());

                    writer.println("✅ Joined room: " + selectedRoom.getName());
                    System.out.println("[INFO]: " + c.getName() + " joined " + selectedRoom.getName());
                    c.setState(ClientState.IN_ROOM);

                lock.unlock();
    
            break;
        }
    }
    private boolean handleMainHubCommand(String input, Client c, BufferedReader reader, PrintWriter writer, Socket sockClient) throws IOException {
        switch (input) {
            case "/quit", "/exit":
                    lock.lock();
                    try{
                        c.setRoom(-1); // To make sure
                        clients.remove(c);
                    } finally{
                        lock.unlock();
                    }
            return true; 
    
            case "/logout":
                lock.lock();
                try {
                    c.setState(ClientState.LOGGED_OUT);
                    clients.remove(c);
                    utils.removeToken(String.valueOf(c.getId()), c.getName());
                } finally {
                    lock.unlock();
                }
                return true; 
    
            case "/create":
                handleRoomCreation(reader, writer, c);
                return false; 
    
            case "/disconnect":
                handleDisconnect(c, sockClient);
                return true; 
    
            default:
                writer.println("Invalid command. Use: /join <room number> or /create to create a room.");
                return false; 
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
                    if (response.equals("/quit") || response.equals("/exit")) {
                        lock.lock();
                        try {
                            c.setRoom(-1);
                            c.setState(ClientState.NOT_IN_ROOM); 
                            room.removeMember(c);
                        } finally {
                            lock.unlock();
                        }
                        writer.println("You have left the room.");
                        break;
                    } else {

                            if(response.equals("/disconnect"))
                            {
                                handleDisconnect(c, sockClient);
                                break;
                            }
                        lock.lock();
                        try {
                            Message userMessage = new Message(c.getName(), response);
                            room.addMessage(userMessage);
                            System.out.println("[INFO]:" + c.getName() + " sent message in room " + roomId);
                            
                            if(room.getIsAi()) {
                                processAIResponseAsync(room, response);
                            }
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

    private void processAIResponseAsync(Room room, String userMessage) {
        System.out.println("[INFO]: Processing asynchronous AI response for message: " + userMessage);
        AIIntegration.processMessageAsync(userMessage, room.getMessages(), new AIIntegration.AIResponseCallback() {
            @Override
            public void onResponseReceived(String response, String originalMessage) {
                lock.lock();
                try {
                    room.addMessage(new Message("AI Bot", response));
                    System.out.println("[INFO]: Async AI response added to room " + room.getId());
                } finally {
                    lock.unlock();
                }
            }
            
            @Override
            public void onError(String errorMessage, String originalMessage) {
                lock.lock();
                try {
                    room.addMessage(new Message("AI Bot", "Sorry, I couldn't process that request: " + errorMessage));
                    System.err.println("[ERROR]: Async AI response failed: " + errorMessage);
                } finally {
                    lock.unlock();
                }
            }
        });
    }

    private String readLineForFlags(BufferedReader reader, PrintWriter writer){
            String input = readInput(reader).getMessage();

            if(input.equals("/help")){
                outputPrints.printHelpPrompt(writer);
                readInput(reader).getMessage(); 
                 outputPrints.cleanClientTerminal(writer);
                 utils.safeSleep(500);
                return null;
            } else {
                return input;
            }
    }

    private void handleRoomCreation(BufferedReader reader, PrintWriter writer, Client c) {
        outputPrints.cleanClientTerminal(writer);
        writer.println("=== Create a New Room ===");
        writer.println("Press 'q' at any time to cancel.\n");
    
            String name = null;
            while (true) {
                writer.println("Enter the room name: ");
               // writer.flush();
                name = readInput(reader).getMessage();
    
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
    
            // 2. Is AI room? (y/n)
            boolean isAiRoom = false;
            while (true) {
                writer.println("Is this an AI room? (y/n): ");
                String aiResponse = readInput(reader).getMessage().toLowerCase();
    
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
    
            // 3. Max members
            int maxMembers;
            while (true) {
                writer.println("Max number of members (-1 for infinite): ");
                String input = readInput(reader).getMessage();
    
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
    
            // 4. Create room
            lock.lock();
            try{
                int roomId = rooms.size();
                Room r = new Room(roomId, name, maxMembers, isAiRoom);
                rooms.add(r);
                System.out.println("[INFO]: " + c.getName() + " created " + r.getName());

            }finally{
                lock.unlock();
            }
           
            writer.println("\n✅ Room created successfully!");
            utils.safeSleep(500);
            return;
      
    }


    private String checkInputWithDelay(BufferedReader reader, int delay){ //Provides a way to refresh the client and not just wait for the input
        long startTime = System.currentTimeMillis();
        String response = null;
        
        while (System.currentTimeMillis() - startTime < delay) {  
            try {
                if (reader.ready()) {
                    response = readInput(reader).getMessage();
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
    private void handleDisconnect(Client c, Socket sockClient) {
        try {
            lock.lock();
            if (c.getState() == ClientState.IN_ROOM) {
                    for (Room room : rooms) {
                        if (room.getId() == c.getRoomId()) {
                            room.removeMember(c);
                            System.out.println("[INFO] " + c.getName() + " left room " + room.getName());
                            break;
                        }
                    }
                    c.leaveRoom();
                
            }
    
            clients.remove(c);
            System.out.println("[INFO] " + c.getName() + " disconnected");
    
           
            sockClient.close();
            
        } catch (IOException e) {
            System.err.println("[ERROR] Error disconnecting client " + c.getName() + ": " + e.getMessage());
            lock.unlock();
        } finally {
            if (clients.contains(c)) {
                clients.remove(c);
            }
            lock.unlock();
        }
    }
    private Model.Package readInput(BufferedReader reader){
        Model.Package p = Model.Package.readInput(reader);
    
         return p;
    }

   
}