import java.io.*;
import java.net.*;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

import javax.print.DocFlavor.READER;

import java.util.concurrent.locks.Condition;
import Model.*;
import Model.Client.ClientState;
import utils.shaHash;
import utils.outputPrints;

public class TimeServer {
    private List<Room> rooms;
    private int port;
    private List<Client> clients;
    
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition available = lock.newCondition();
    private final ExecutorService virtualThreadExecutor = 
        Executors.newVirtualThreadPerTaskExecutor();

    public TimeServer(int port) {
        this.rooms = new ArrayList<>();
        this.port = port;
        this.clients = new ArrayList<>();
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

    private void handleRequest(Socket sockClient)  {
        try{
        BufferedReader reader = new BufferedReader(new InputStreamReader(sockClient.getInputStream()));
        PrintWriter writer = new PrintWriter(sockClient.getOutputStream(), true);
            try {
                Client c = null;
                while (c == null) {
                    c = performAuth(sockClient);
                }
                if(c.getState() == ClientState.NOT_IN_ROOM){
                    showMainHub(c, sockClient);
                }
                if(c.getState() == ClientState.IN_ROOM){
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

        }catch(IOException e){System.out.println("[ERROR]:Failed to create I/O streams for the client.");}
       
    }

    private Client performAuth(Socket sockClient) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(sockClient.getInputStream()));
        PrintWriter writer = new PrintWriter(sockClient.getOutputStream(), true);

        outputPrints.cleanClientTerminal(writer);
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
        try {
            password = shaHash.toHexString(shaHash.getSHA(password));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error hashing password", e);
        }

            if (choice.equals("1")) { // Register
                lock.lock();
                    boolean usernameTaken = false; //TODO

                    if (usernameTaken) {
                        writer.println("Username already taken");
                        return null;
                    }
                    
                    Client c = new Client(clients.size(),sockClient.getInetAddress(), username, password);

                    clients.add(c); 
                    outputPrints.cleanClientTerminal(writer);
                    safeSleep(1000);
                    writer.println("Registration successful. Welcome " + username);
                    System.out.println("[INFO]: User " + username + "sucessfully registered.");
                lock.unlock();
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

            Client newClient = new Client(clients.size(),sockClient.getInetAddress(), username, password);
            clients.add(newClient);
            storingAndHashingCredentials(newClient);

            return newClient;
    }

    


    private int storingAndHashingCredentials(Client c){

        try{
        String data = c.getId() + "," + c.getName() + "," + c.getPassword() + ";" + "\n";
        System.out.println(data);
        FileWriter writer = new FileWriter("credentials.txt", true); // Append mode
        writer.write(data);
        writer.close();

        return 0; 
        } catch (IOException  e) {
            e.printStackTrace();
            return -1; 
        }
            
    }

    private void showMainHub(Client c, Socket sockClient) throws IOException {
        Room testRoom = new Room(0,"TestRoom", 5, false); // name: TestRoom, max 5 members, not AI
        rooms.add(testRoom);
        BufferedReader reader = new BufferedReader(new InputStreamReader(sockClient.getInputStream()));
        PrintWriter writer = new PrintWriter(sockClient.getOutputStream(), true);
    
    

        while (true) {
            safeSleep(500);
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
                    Room selectedRoom = rooms.get(choice);

                    if (selectedRoom.getMembers().size() >= selectedRoom.getMaxNumberOfMembers()) {
                        writer.println("That room is full. Please choose another one:");
                        lock.unlock();
                        continue;
                    }
                    
                    // selectedRoom.addMember(c); // TODO: actually add the client
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
                
                
                String response = checkInputWithDelay(reader,1000);
               
                
                if (response != null) {
                    if (response.equals("/quit")) {
                        lock.lock();
                        try {
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

    private void safeSleep(int ms){
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); //todo
            System.out.println("Thread sleep interrupted: " + e.getMessage());
        }
    }

    private String readLineForFlags(BufferedReader reader, PrintWriter writer){
        try {
            String input = reader.readLine().trim();

            if(input.equals("/help")){
                outputPrints.printHelpPrompt(writer);
                 reader.readLine(); //Waiting for Key
                 outputPrints.cleanClientTerminal(writer);
                 safeSleep(500);
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
               // writer.flush();
                name = reader.readLine().trim();
    
                if (name.equalsIgnoreCase("q")) {
                    writer.println("❌ Room creation cancelled.");
                    safeSleep(500);
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
                String aiResponse = reader.readLine().trim().toLowerCase();
    
                if (aiResponse.equals("q")) {
                    writer.println("❌ Room creation cancelled.");
                    safeSleep(500);
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
            safeSleep(500);
            return;
        } catch (IOException e) {
            writer.println("❌ An error occurred during room creation: " + e.getMessage());
            e.printStackTrace();
        
        }
    }


    private String checkInputWithDelay(BufferedReader reader, int delay){ //Provides a way to refresh the client and not just wait for the input
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
            safeSleep(100);
        }
        return response;
    }
}