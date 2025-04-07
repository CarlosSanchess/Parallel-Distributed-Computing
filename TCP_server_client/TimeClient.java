import java.net.*;
import java.util.Scanner;

import utils.shaHash;

import java.io.*;
/**
 * This program demonstrates a simple TCP/IP socket client.
 *
 * @author www.codejava.net
 */
public class TimeClient {
 
    public static void main(String[] args) {
        if (args.length < 2) return;
 
        String hostname = args[0];
        int port = Integer.parseInt(args[1]);
        String userName = registerUser();
        Socket server = connect(hostname, port, userName);
        if(server == null){return;}

    }

    /*User Can Login/Register, store hashed password in file. */
    private static String registerUser() {
        Scanner scanner = new Scanner(System.in);

        System.out.println("=== User Registration ===");

        System.out.print("Enter username: ");
        String username = scanner.nextLine();
        

        Console console = System.console();

        if (console == null) {
            System.out.println("No console available");
            return null;
        }

        char[] passwordArray = console.readPassword("Enter password: ");
        String password = new String(passwordArray);
       // String hashedPassword = toHexString(getSHA(password));

        if (username.isEmpty() || password.isEmpty()) {
            return "Error: Username and password cannot be empty!";
        }

        
        return username;
    }
    private static Socket connect(String hostname, int Port, String userName){
        try (Socket socket = new Socket(hostname, Port)) {
 
            OutputStream output = socket.getOutputStream();
            PrintWriter writer = new PrintWriter(output, true);
            writer.println(("["+ userName + "] entered the room.").toString());
            InputStream input = socket.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(input));
 
            String time = reader.readLine();
 
            System.out.println(time);

            return socket;
 
        } catch (UnknownHostException ex) {
 
            System.out.println("Server not found: " + ex.getMessage());
 
        } catch (IOException ex) {
 
            System.out.println("I/O error: " + ex.getMessage());
        }
        return null;
    }
}