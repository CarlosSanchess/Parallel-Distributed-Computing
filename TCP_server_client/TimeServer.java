import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.locks.*;
import javax.net.ssl.*;
import java.security.*;

import Model.*;
import Model.Package;
import Model.Client.ClientState;
import utils.*;

public class TimeServer {
    private final ArrayList<Room> rooms;
    private final ArrayList<Client> clients;
    private final ArrayList<Thread> activeThreads;
    private int port;
    private boolean isRunning = true;
    private SSLServerSocket serverSocket = null;
    private final ReentrantLock lock = new ReentrantLock();
    private final ReentrantLock threadLock = new ReentrantLock();
    private int nextClientId;

    private static final String KEY_MANAGER_ALGORITHM = KeyManagerFactory.getDefaultAlgorithm();
    private static final String TRUST_MANAGER_ALGORITHM = TrustManagerFactory.getDefaultAlgorithm();
    private static final String PROTOCOL = "TLSv1.3";

    public TimeServer(int port) {
        this.rooms = new ArrayList<>();
        this.clients = new ArrayList<>();
        this.activeThreads = new ArrayList<>();
        this.port = port;
        initializeNextClientId();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            isRunning = false;
            threadLock.lock();
            try {
                for (Thread thread : activeThreads) {
                    try {
                        thread.interrupt();
                        thread.join(5000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            } finally {
                threadLock.unlock();
            }
        }));
    }


    private void submitTask(Runnable task) {
    Thread thread = Thread.ofVirtual().start(task);
    threadLock.lock();
    try {
        activeThreads.add(thread);
    } finally {
        threadLock.unlock();
    }
}

    private void initializeNextClientId() {
        nextClientId = utils.readLastId() + 1;
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java SecureTimeServer <port>");
            return;
        }
        int port = Integer.parseInt(args[0]);
        new TimeServer(port).start();
    }

    private boolean validateTokenForClient(String token, Client client) {
        if (token == null || token.isEmpty() || client == null) {
            return false;
        }
        
        Map<String, String[]> tokenRecords = utils.readTokens();
        if (!tokenRecords.containsKey(token)) {
            return false;
        }
        
        String[] tokenData = tokenRecords.get(token);
        String userId = tokenData[0];
        String name = tokenData[1];
        String timestamp = tokenData[2];
        
        if (client.getId() != Integer.parseInt(userId) || !client.getName().equals(name)) {
            return false;
        }
        
        long currentTime = System.currentTimeMillis() / 1000L;
        if (currentTime > Long.parseLong(timestamp)) {
            System.out.println("[INFO] Token expired for user: " + name);
            utils.removeToken(userId, name); 
            return false;
        }
        
        return true;
    }

    private void sendTokenError(PrintWriter writer, String message) {
        Model.Package errorPackage = new Package("TOKEN_ERROR: " + message, "");
        writer.println(errorPackage.serialize());
    }

    private void safeExit() {
    this.isRunning = false;
    
    // Close server socket
    try {
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }
    } catch (IOException e) {
        System.err.println("Error closing server socket: " + e.getMessage());
    }
    
    // Stop all threads
    threadLock.lock();
    try {
        for (Thread thread : activeThreads) {
            try {
                thread.interrupt();
                thread.join(5000); // Wait up to 5 seconds for each thread
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        activeThreads.clear();
    } finally {
        threadLock.unlock();
    }
    
    System.out.println("Server has shut down gracefully");
}

    private String getKeystorePath() {
        String path = System.getenv("TIMESERVER_KEYSTORE_PATH");
        return (path != null) ? path : "server.keystore"; 
    }

    private String getKeystorePassword() {
        String password = System.getenv("TIMESERVER_KEYSTORE_PASSWORD");
        if (password == null) {
            System.err.println("WARNING: Keystore password not set in environment variables");
            return "serverpass";
        }
        return password;
    }

    private SSLServerSocket createSSLServerSocket() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("JKS");
        try (FileInputStream fis = new FileInputStream(getKeystorePath())) {
            keyStore.load(fis, getKeystorePassword().toCharArray());
        }

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KEY_MANAGER_ALGORITHM);
        keyManagerFactory.init(keyStore, getKeystorePassword().toCharArray());

        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TRUST_MANAGER_ALGORITHM);
        trustManagerFactory.init(keyStore);

        SSLContext sslContext = SSLContext.getInstance(PROTOCOL);
        sslContext.init(
            keyManagerFactory.getKeyManagers(),
            trustManagerFactory.getTrustManagers(),
            null
        );

        SSLServerSocketFactory sslServerSocketFactory = sslContext.getServerSocketFactory();
        SSLServerSocket sslServerSocket = (SSLServerSocket) sslServerSocketFactory.createServerSocket(this.port);
        
        sslServerSocket.setEnabledCipherSuites(getStrongCipherSuites(sslServerSocket.getSupportedCipherSuites()));
        
        sslServerSocket.setNeedClientAuth(false);

        System.out.println("Using keystore: " + getKeystorePath());
        System.out.println("Secure server is listening on port " + this.port);
        System.out.println("Using protocol: " + PROTOCOL);
        System.out.println("Enabled cipher suites: " + String.join(", ", sslServerSocket.getEnabledCipherSuites()));
        
        return sslServerSocket;
    }
    
    private String[] getStrongCipherSuites(String[] supportedCipherSuites) {
        List<String> strongCiphers = new ArrayList<>();
        for (String cipher : supportedCipherSuites) {
            if (!cipher.contains("NULL") && 
                !cipher.contains("anon") && 
                !cipher.contains("DES") &&
                !cipher.contains("RC4")) {
                strongCiphers.add(cipher);
            }
        }
        return strongCiphers.toArray(new String[0]);
    }

    public void start() {
        try {
            serverSocket = createSSLServerSocket();
            System.out.println("Secure server is listening on port " + this.port);
            System.out.println("Using protocol: " + PROTOCOL);
            
            // Start token cleanup thread
            TokenCleanupTask tokenCleanupTask = new TokenCleanupTask(lock);
            Thread cleanupThread = Thread.ofVirtual()
                .name("TokenCleanupThread")
                .start(tokenCleanupTask);
            
            while (isRunning) {
                SSLSocket socket = (SSLSocket) serverSocket.accept();
                socket.setEnabledCipherSuites(getStrongCipherSuites(socket.getSupportedCipherSuites()));
                
                submitTask(() -> {
                    try {
                        socket.startHandshake();
                        System.out.println("SSL handshake completed with client: " + socket.getInetAddress());
                        handleRequest(socket);
                    } catch (IOException e) {
                        System.out.println("Handshake failed: " + e.getMessage());
                        try {
                            socket.close();
                        } catch (IOException ex) {
                            System.out.println("Error closing socket: " + ex.getMessage());
                        }
                    }
                });
            }
            
            tokenCleanupTask.stop();
            cleanupThread.join();
            
        } catch (Exception ex) {
            System.out.println("Server exception: " + ex.getMessage());
            ex.printStackTrace();
        } finally {
            safeExit();
        }
    }

    private void handleRequest(SSLSocket sockClient) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(sockClient.getInputStream()));
            PrintWriter writer = new PrintWriter(sockClient.getOutputStream(), true);
            Client c = null;

            try {
                while(true){
                    if(c == null || c.getState() == ClientState.LOGGED_OUT) { 
                        c = performAuth(sockClient);
                    }
                    if(c != null && c.getState() == ClientState.NOT_IN_ROOM){
                        showMainHub(c, sockClient);
                    }
                    if(c != null && c.getState() == ClientState.IN_ROOM){
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

    private Client performAuth(SSLSocket sockClient) throws IOException {
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
            
            Model.Package inputPackage = readInput(reader);
            if (inputPackage == null) continue;
            
            if (!validateTokenForClient(inputPackage.getToken(), c)) {
                sendTokenError(writer, "Invalid or expired token. Please re-authenticate.");
                c.setState(ClientState.LOGGED_OUT);
                return;
            }
            
            String input = inputPackage.getMessage();
            if(input == null) continue;

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

                    if (selectedRoom.getMembers().size() >= selectedRoom.getMaxNumberOfMembers() && selectedRoom.getMaxNumberOfMembers() != -1) {
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
                
                Model.Package inputPackage = readInputWithDelay(reader, 1000);
                
                if (inputPackage != null) {
                    if (!validateTokenForClient(inputPackage.getToken(), c)) {
                        sendTokenError(writer, "Invalid or expired token. Please re-authenticate.");
                        c.setState(ClientState.LOGGED_OUT);
                        break;
                    }
                    
                    String response = inputPackage.getMessage();
                    
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
                        if(response.equals("/disconnect")) {
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

    private void handleRoomCreation(BufferedReader reader, PrintWriter writer, Client c) {
        outputPrints.cleanClientTerminal(writer);
        writer.println("=== Create a New Room ===");
        writer.println("Press 'q' at any time to cancel.\n");
    
        String name = null;
        while (true) {
            writer.println("Enter the room name: ");
            Model.Package inputPackage = readInput(reader);
            if (inputPackage == null) continue;
            
            if (!validateTokenForClient(inputPackage.getToken(), c)) {
                sendTokenError(writer, "Invalid or expired token. Please re-authenticate.");
                return;
            }
            
            name = inputPackage.getMessage();

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
            Model.Package inputPackage = readInput(reader);
            if (inputPackage == null) continue;
            
            if (!validateTokenForClient(inputPackage.getToken(), c)) {
                sendTokenError(writer, "Invalid or expired token. Please re-authenticate.");
                return;
            }
            
            String aiResponse = inputPackage.getMessage().toLowerCase();

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
            Model.Package inputPackage = readInput(reader);
            if (inputPackage == null) continue;
            
            if (!validateTokenForClient(inputPackage.getToken(), c)) {
                sendTokenError(writer, "Invalid or expired token. Please re-authenticate.");
                return;
            }
            
            String input = inputPackage.getMessage();

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

    private Model.Package readInputWithDelay(BufferedReader reader, int delay) {
        long startTime = System.currentTimeMillis(); 
        Model.Package response = null;
        
        while (System.currentTimeMillis() - startTime < delay) {  
            try {
                if (reader.ready()) {
                    response = readInput(reader);
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
            System.out.println("User Name: " + username);
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
            System.out.println("Password received");
            if (password.equalsIgnoreCase("q")) {
                return null;
            }
            if (!password.isEmpty()) {
                return password;
            }
            writer.println("Password cannot be empty.");
        }
    }

    private Client handleRegistration(SSLSocket sockClient, String username, String password, PrintWriter writer, BufferedReader reader) {
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

    private Client handleLogin(SSLSocket sockClient, String username, String password, PrintWriter writer) {
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
    
    private Client handleLoginWithToken(SSLSocket sockClient, PrintWriter writer, String Token){
        if(Token != null && !Token.isEmpty()){
            Map<String, String[]> tokenRecords = utils.readTokens();
            if (tokenRecords.containsKey(Token)) {
                String[] tokenData = tokenRecords.get(Token);
                String userId = tokenData[0];  
                String name = tokenData[1];
                String timestamp = tokenData[2];  
                
                long currentTime = System.currentTimeMillis() / 1000L;
                if (currentTime > Long.parseLong(timestamp)) { 
                    System.out.println("Current Time: "+ currentTime);
                    System.out.println("Time stamp: " + timestamp);
                    writer.println("Token has expired");
                    System.out.println("[INFO] TOKEN EXPIRED");
                    return null;
                }
                
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
    
    private void broadcastMainHubUpdate() {
    lock.lock();
    try {
        for (Client client : clients) {
            if (client.getState() == ClientState.NOT_IN_ROOM && 
                client.getSocket() != null && 
                !client.getSocket().isClosed()) {
                try {
                    PrintWriter writer = new PrintWriter(client.getSocket().getOutputStream(), true);
                    outputPrints.cleanClientTerminal(writer);
                    writer.println("Welcome to xchat! (Secured with TLS)");
                    writer.println("\nRooms Available:");
                
                    for (int i = 0; i < rooms.size(); i++) {
                        Room r = rooms.get(i);
                        String roomInfo = (i + 1) + ". " + r.getName() + " [" + 
                            r.getMembers().size() + "/" + r.getMaxNumberOfMembers() + "]";
                        writer.println(roomInfo);
                    }
                    
                    writer.println("\nTo join a room, type: /join <room number> or /create to create a room.");
                } catch (IOException e) {
                    System.err.println("[ERROR] Failed to update main hub for " + client.getName());
                }
            }
        }
    } finally {
        lock.unlock();
    }
}

    private void showMainHub(Client c, SSLSocket sockClient) throws IOException {
    BufferedReader reader = new BufferedReader(new InputStreamReader(sockClient.getInputStream()));
    final PrintWriter writer = new PrintWriter(sockClient.getOutputStream(), true);

    // Set socket for broadcasting updates
    c.setSocket(sockClient);
    c.setState(ClientState.NOT_IN_ROOM);

    // Create and start the update thread
    Thread updateThread = new Thread(() -> {
        while (!Thread.interrupted() && c.getState() == ClientState.NOT_IN_ROOM) {
            if (c.getSocket() != null && 
                !c.getSocket().isClosed() &&
                c.getState() != ClientState.WAITING) {
                try {
                    lock.lock();
                    try {
                        outputPrints.cleanClientTerminal(writer);
                        writer.println("Welcome to xchat! (Secured with TLS)");
                        writer.println("\nRooms Available:");
                    
                        for (int i = 0; i < rooms.size(); i++) {
                            Room r = rooms.get(i);
                            String roomInfo = (i + 1) + ". " + r.getName() + " [" + 
                                r.getMembers().size() + "/" + 
                                (r.getMaxNumberOfMembers() == -1 ? "∞" : r.getMaxNumberOfMembers()) + "]";
                            writer.println(roomInfo);
                        }
                        
                        writer.println("\nTo join a room, type: /join <room number> or /create to create a room.");
                    } finally {
                        lock.unlock();
                    }
                    Thread.sleep(2000); // Update every 2 seconds
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.err.println("[ERROR] Failed to send periodic main hub update to " + 
                        c.getName() + ": " + e.getMessage());
                }
            }
        }
    });
    
    submitTask(updateThread);

    try {
        while (true) {
            Package pkg = Package.readInput(reader);
            if (pkg == null) continue;
            
            String input = pkg.getMessage();
            if (input == null) continue;

            if (input.startsWith("/join")) {
                String[] parts = input.split(" ");
                if (parts.length != 2) {
                    writer.println("Invalid command format. Use: /join <room number>");
                    continue;
                }

                try {
                    int roomIndex = Integer.parseInt(parts[1]) - 1;
                    lock.lock();
                    try {
                        if (roomIndex >= 0 && roomIndex < rooms.size()) {
                            Room selectedRoom = rooms.get(roomIndex);
                            if (selectedRoom.addMember(c)) {
                                c.setRoom(selectedRoom.getId());
                                c.setState(ClientState.IN_ROOM);
                                broadcastMainHubUpdate(); // Update main hub for other users
                                updateThread.interrupt(); // Stop main hub updates for this client
                                return;
                            } else {
                                writer.println("Cannot join room - room might be full");
                            }
                        } else {
                            writer.println("Invalid room number");
                        }
                    } finally {
                        lock.unlock();
                    }
                } catch (NumberFormatException e) {
                    writer.println("Invalid room number format");
                }
            } else if (handleMainHubCommand(input, c, reader, writer, sockClient)) {
                updateThread.interrupt();
                return;
            }
        }
    } catch (IOException e) {
        System.err.println("[ERROR] Main hub error for " + c.getName() + ": " + e.getMessage());
    } finally {
        updateThread.interrupt();
    }
}

    private boolean handleMainHubCommand(String input, Client c, BufferedReader reader, PrintWriter writer, SSLSocket sockClient) throws IOException {
        switch (input) {
            case "/quit", "/exit":
                lock.lock();
                try {
                    c.setRoom(-1); 
                    clients.remove(c);
                } finally {
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

    private void broadcastRoomUpdate(Room room) {
    lock.lock();
    try {
        System.out.println("[DEBUG] Broadcasting room update to " + room.getMembers().size() + " members");
        for (Client member : room.getMembers()) {
            if (member.getSocket() != null && !member.getSocket().isClosed()) {
                try {
                    PrintWriter memberWriter = new PrintWriter(member.getSocket().getOutputStream(), true);
                    outputPrints.cleanClientTerminal(memberWriter);
                    displayRoomState(room, memberWriter);
                } catch (IOException e) {
                    System.err.println("[ERROR] Failed to send update to member " + 
                        member.getName() + ": " + e.getMessage());
                }
            }
        }
        // Broadcast main hub update when room state changes
        broadcastMainHubUpdate();
    } finally {
        lock.unlock();
    }
}
    
    private void showRoom(Client c, SSLSocket sockClient, int roomId, BufferedReader reader, PrintWriter writer) {
        final Room finalRoom;
        lock.lock();
        try {
            Room room = null;
            for (Room r : rooms) {
                if (r.getId() == roomId) {
                    room = r;
                    break;
                }
            }
            finalRoom = room;
        } finally {
            lock.unlock();
        }

        if (finalRoom == null) {
            writer.println("Error: Room not found.");
            c.setState(ClientState.NOT_IN_ROOM);
            return;
        }

        c.setSocket(sockClient);
        UpdateThread updateThread = new UpdateThread(finalRoom, c, writer);
        submitTask(updateThread);

        boolean running = true;
        while (running && isRunning) {
            try {
                Package pkg = Package.readInput(reader);
                if (pkg == null) continue;
                
                String message = pkg.getMessage();
                if (message == null || message.trim().isEmpty()) continue;

                if (message.equals("/quit") || message.equals("/exit")) {
                    lock.lock();
                    try {
                        finalRoom.removeMember(c);
                        c.leaveRoom();
                        c.setState(ClientState.NOT_IN_ROOM);
                        writer.println("You have left the room.");
                        broadcastRoomUpdate(finalRoom);
                    } finally {
                        lock.unlock();
                    }
                    running = false;
                } else {
                    lock.lock();
                    try {
                        Message newMessage = new Message(c.getName(), message);
                        finalRoom.addMessage(newMessage);
                        broadcastRoomUpdate(finalRoom);

                        if (finalRoom.getIsAi()) {
                            processAIResponseSync(finalRoom, message);
                        }
                    } finally {
                        lock.unlock();
                    }
                }
            } catch (IOException e) {
                System.err.println("[ERROR] Room error: " + e.getMessage());
                lock.lock();
                try {
                    finalRoom.removeMember(c);
                    broadcastRoomUpdate(finalRoom);
                } finally {
                    lock.unlock();
                }
                running = false;
            }
        }

        updateThread.stopThread();
        c.setSocket(null);
    }

private void displayRoomState(Room room, PrintWriter writer) {
    writer.println("=== Room: " + room.getName() + " ===");
    writer.println("Members: " + room.getNumberOfMembers() + "/" + 
        (room.getMaxNumberOfMembers() == -1 ? "∞" : room.getMaxNumberOfMembers()));
    writer.println("Type your message or commands (/quit to leave):");
    writer.println("----------------------------------------");
    
    lock.lock();
    try {
        for (Message msg : room.getMessages()) {
            writer.println(msg.toString());
        }
    } finally {
        lock.unlock();
    }
    writer.println("----------------------------------------");
    writer.flush(); // Ensure all content is sent immediately
}

    private void processAIResponseSync(Room room, String message) {
    ReentrantLock lock = new ReentrantLock();
    Condition done = lock.newCondition();
    boolean[] completed = {false};

    Thread responseThread = Thread.ofVirtual().unstarted(() -> {
        try {
            AIIntegration.processMessageAsync(message, room.getMessages(), new AIIntegration.AIResponseCallback() {
                @Override
                public void onResponseReceived(String response, String originalMessage) {
                    lock.lock();
                    try {
                        room.addMessage(new Message("AI Assistant", response));
                        broadcastRoomUpdate(room);
                        completed[0] = true;
                        done.signal();
                    } finally {
                        lock.unlock();
                    }
                }

                @Override
                public void onError(String errorMessage, String originalMessage) {
                    lock.lock();
                    try {
                        room.addMessage(new Message("System", "Error: " + errorMessage));
                        broadcastRoomUpdate(room);
                        completed[0] = true;
                        done.signal();
                    } finally {
                        lock.unlock();
                    }
                }
            });
        } catch (Exception e) {
            lock.lock();
            try {
                completed[0] = true;
                done.signal();
            } finally {
                lock.unlock();
            }
        }
    });

    responseThread.start();

    lock.lock();
    try {
        if (!completed[0]) {
            done.awaitNanos(5_000_000_000L); // Wait up to 5 seconds
        }
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    } finally {
        lock.unlock();
    }
}
    

    private void handleRoomCreation(BufferedReader reader, PrintWriter writer, Client c) {
    // Set client state to WAITING to pause main hub updates
    c.setState(ClientState.WAITING);
    
    outputPrints.cleanClientTerminal(writer);
    writer.println("=== Create a New Room ===");
    writer.println("Press 'q' at any time to cancel.\n");
    writer.flush();

    String name = null;
    while (true) {
        writer.println("Enter the room name: ");
        writer.flush();
        name = readInput(reader).getMessage();

        if (name.equalsIgnoreCase("q")) {
            writer.println("❌ Room creation cancelled.");
            utils.safeSleep(500);
            c.setState(ClientState.NOT_IN_ROOM); // Reset state
            return;
        }

        if (!name.isEmpty()) {
            break;
        }

        writer.println("⚠️ Room name cannot be empty.");
    }

    // Clear screen between inputs
    outputPrints.cleanClientTerminal(writer);
    writer.println("=== Create a New Room ===");
    writer.println("Room name: " + name + "\n");

    // 2. Is AI room? (y/n)
    boolean isAiRoom = false;
    while (true) {
        writer.println("Is this an AI room? (y/n): ");
        writer.flush();
        String aiResponse = readInput(reader).getMessage().toLowerCase();

        if (aiResponse.equals("q")) {
            writer.println("❌ Room creation cancelled.");
            utils.safeSleep(500);
            c.setState(ClientState.NOT_IN_ROOM); // Reset state
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

    // Clear screen between inputs
    outputPrints.cleanClientTerminal(writer);
    writer.println("=== Create a New Room ===");
    writer.println("Room name: " + name);
    writer.println("AI Room: " + (isAiRoom ? "Yes" : "No") + "\n");

    // 3. Max members
    int maxMembers;
    while (true) {
        writer.println("Max number of members (-1 for infinite): ");
        writer.flush();
        String input = readInput(reader).getMessage();

        if (input.equalsIgnoreCase("q")) {
            writer.println("❌ Room creation cancelled.");
            c.setState(ClientState.NOT_IN_ROOM); // Reset state
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
    try {
        int roomId = rooms.size();
        Room r = new Room(roomId, name, maxMembers, isAiRoom);
        rooms.add(r);
        System.out.println("[INFO]: " + c.getName() + " created " + r.getName());
        
        // Add these lines to broadcast the update immediately
        writer.println("\n✅ Room created successfully!");
        utils.safeSleep(1000); // Delay for visibility
        c.setState(ClientState.NOT_IN_ROOM);
        broadcastMainHubUpdate(); // Broadcast update to all clients
        return; // Add return to exit the method
    } finally {
        lock.unlock();
    }
}

    private String checkInputWithDelay(BufferedReader reader, int delay) { //Provides a way to refresh the client and not just wait for the input
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

    private void handleDisconnect(Client c, SSLSocket sockClient) {
        try {
            lock.lock();
            try {
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
            } finally {
                lock.unlock();
            }
           
            sockClient.close();
        } catch (IOException e) {
            System.err.println("[ERROR] Error disconnecting client " + c.getName() + ": " + e.getMessage());
        }
    }

    private Model.Package readInput(BufferedReader reader) {
    try {
        return Model.Package.readInput(reader);
    } catch (IOException e) {
        System.err.println("[ERROR] Failed to read input: " + e.getMessage());
        return null;
    }
}

 private class UpdateThread extends Thread {
        private volatile boolean running = true;
        private final Room room;
        private final Client client;
        private final PrintWriter writer;
        
        public UpdateThread(Room room, Client client, PrintWriter writer) {
            this.room = room;
            this.client = client;
            this.writer = writer;
        }
        
        @Override
        public void run() {
            while (running && !Thread.interrupted()) {
                try {
                    if (client.getState() == ClientState.IN_ROOM && 
                        client.getSocket() != null && 
                        !client.getSocket().isClosed()) {
                        
                        lock.lock();
                        try {
                            outputPrints.cleanClientTerminal(writer);
                            displayRoomState(room, writer);
                        } finally {
                            lock.unlock();
                        }
                    } else {
                        break;
                    }
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.err.println("[ERROR] Update thread error: " + e.getMessage());
                }
            }
        }
        
        public void stopThread() {
            running = false;
            interrupt();
        }
    }
}
