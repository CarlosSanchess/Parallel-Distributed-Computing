package utils;

import java.io.PrintWriter;
import java.util.List;

import Model.Client;
import Model.Message;
import Model.Room;

public class outputPrints {
    private static String cleanTerminal = "\033[H\033[2J\n";

    public static void cleanClientTerminal(PrintWriter clientWriter){
        clientWriter.write(cleanTerminal);
    }
    public static void printHelpPrompt(PrintWriter writer) {
        writer.println("=== XChat Help ===");
        writer.println("Here are some commands you can use:");
        writer.println();
        writer.println("  /help            Show this help message");
        writer.println("  /list            List available chat rooms");
        writer.println("  /join <room>     Join a specific chat room");
        writer.println("  /leave           Leave the current chat room");
        writer.println("  /users           List users in the current room");
        writer.println("  /create <room> <name> <maxMember>?  Creates a room with an option of maxMembers   ");
        writer.println("  /exit            Disconnect from the server");
        writer.println();
        writer.println("===================");
        writer.println("Press any Key to Leave this Menu!");
    }
    
   public static void viewRoom(Room room, PrintWriter writer) {
    writer.println("=== Room: " + room.getName() + " ===");
    writer.println("Members: " + room.getNumberOfMembers() + "/" + 
        (room.getMaxNumberOfMembers() == -1 ? "∞" : room.getMaxNumberOfMembers()));
    writer.println("----------------------------------------");
    
    // Display all messages
    for (Message msg : room.getMessages()) {
        writer.println(msg.toString());
    }
    writer.println("----------------------------------------");
    writer.println("Type your message or commands (/quit to leave):");
}
 
}
