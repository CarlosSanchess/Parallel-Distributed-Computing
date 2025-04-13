package utils;

import java.io.PrintWriter;
import java.util.List;

import Model.Client;
import Model.Message;
import Model.Room;

public class outputPrints {
    private static String cleanTerminal = "\033[H\033[2J";

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
    final int totalWidth = 80;        // Total width of the layout
    final int chatWidth = 50;         // Left side: chat
    final int membersWidth = totalWidth - chatWidth; // Right side: members

    List<Message> messages = room.getMessages();  
    List<Client> members = room.getMembers();    
    writer.println("Room: " + room.getName());
    writer.println("=".repeat(totalWidth));

    int maxLines = Math.max(messages.size(), members.size() + 1);

    for (int i = 0; i < maxLines; i++) {
        String msg = i < messages.size() ? messages.get(i).toString() : "";
        msg = truncateOrPad(msg, chatWidth);
        String member;
        if(i == 0){
            member = "Members List:";
        } else {
            member = (i-1) < members.size() ? members.get(i-1).getName() : "";
        }

        member = truncateOrPad(member, membersWidth);
        writer.println(msg + " |      " + member);
    }
    
    writer.println("=".repeat(totalWidth));
    writer.println("[ Write your message below ]");
    writer.print("> ");
    writer.flush();
}

    private static String truncateOrPad(String str, int width) {
        if (str.length() > width) {
            return str.substring(0, width - 3) + "...";
        } else {
            return String.format("%-" + width + "s", str);
        }
    }
}
