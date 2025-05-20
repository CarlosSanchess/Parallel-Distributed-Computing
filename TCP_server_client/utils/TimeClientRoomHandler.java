package utils;

import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TimeClientRoomHandler {
    private TextArea outputArea;
    private Frame parentFrame;
    private RoomData currentRoom;
    private List<RoomMember> members = new ArrayList<>();
    private List<RoomMessage> messages = new ArrayList<>();
    
    public TimeClientRoomHandler(TextArea outputArea, Frame parentFrame) {
        this.outputArea = outputArea;
        this.parentFrame = parentFrame;
    }
    
    /**
     * Handles incoming room data string
     * @param roomData The raw room data string
     */
    public void handleRoomData(String roomData) {
        RoomData room = parseRoomData(roomData);
        if (room != null) {
            this.currentRoom = room;
            displayRoomInfo();
        }
    }
    
    /**
     * Parses the room data string into structured data
     * @param roomData The raw room data string
     * @return The parsed RoomData object
     */
    private RoomData parseRoomData(String roomData) {
        // Extract room properties using regex
        Pattern pattern = Pattern.compile(
            "Room\\{\\s*IsAi\\s*=\\s*(\\d+),\\s*" +
            "Id\\s*=\\s*(\\d+),\\s*" +
            "Name\\s*=\\s*\"([^\"]*)\",\\s*" +
            "MaxNumberOfMembers\\s*=\\s*(\\d+),\\s*" +
            "Members\\s*=\\s*\\[([^\\]]*)\\],\\s*" +
            "Messages\\s*=\\s*\\[([^\\]]*)\\]\\s*\\}"
        );
        
        Matcher matcher = pattern.matcher(roomData);
        if (matcher.find()) {
            int isAi = Integer.parseInt(matcher.group(1));
            int id = Integer.parseInt(matcher.group(2));
            String name = matcher.group(3);
            int maxMembers = Integer.parseInt(matcher.group(4));
            String membersStr = matcher.group(5);
            String messagesStr = matcher.group(6);
            
            // Parse members
            members.clear();
            Pattern memberPattern = Pattern.compile("Model\\.Client@([a-f0-9]+)");
            Matcher memberMatcher = memberPattern.matcher(membersStr);
            while (memberMatcher.find()) {
                members.add(new RoomMember(memberMatcher.group(1)));
            }
            
            // Parse messages
            messages.clear();
            Pattern messagePattern = Pattern.compile("\\[(\\d+:\\d+)\\]\\s+([^:]+):\\s+([^,\\]]+)");
            Matcher messageMatcher = messagePattern.matcher(messagesStr);
            while (messageMatcher.find()) {
                String time = messageMatcher.group(1);
                String sender = messageMatcher.group(2);
                String content = messageMatcher.group(3).trim();
                messages.add(new RoomMessage(time, sender, content));
            }
            
            return new RoomData(isAi, id, name, maxMembers, members, messages);
        }
        
        return null;
    }
    
    /**
     * Displays the room information in a user-friendly AWT interface
     */
    private void displayRoomInfo() {
        if (currentRoom == null) return;
        
        // Create dialog with room info
        Dialog roomDialog = new Dialog(parentFrame, "Room: " + currentRoom.getName(), true);
        roomDialog.setLayout(new BorderLayout(10, 10));
        roomDialog.setSize(500, 400);
        roomDialog.setBackground(new Color(240, 240, 240));
        
        // Room info panel
        Panel infoPanel = new Panel(new GridLayout(0, 1, 5, 5));
        infoPanel.add(new Label("Room: " + currentRoom.getName()));
        infoPanel.add(new Label("ID: " + currentRoom.getId()));
        infoPanel.add(new Label("Members: " + currentRoom.getMembers().size() + "/" + 
                               currentRoom.getMaxMembers()));
        
        // Member list panel
        Panel memberPanel = new Panel(new BorderLayout());
        memberPanel.add(new Label("Members:"), BorderLayout.NORTH);
        
        List<RoomMember> roomMembers = currentRoom.getMembers();
        TextArea memberList = new TextArea(roomMembers.size(), 20);
        memberList.setEditable(false);
        for (RoomMember member : roomMembers) {
            memberList.append("• " + member.getId() + "\n");
        }
        memberPanel.add(memberList, BorderLayout.CENTER);
        
        // Message panel
        Panel messagePanel = new Panel(new BorderLayout());
        messagePanel.add(new Label("Messages:"), BorderLayout.NORTH);
        
        List<RoomMessage> roomMessages = currentRoom.getMessages();
        TextArea messageList = new TextArea(10, 40);
        messageList.setEditable(false);
        for (RoomMessage message : roomMessages) {
            messageList.append("[" + message.getTime() + "] " + 
                               message.getSender() + ": " + 
                               message.getContent() + "\n");
        }
        messagePanel.add(messageList, BorderLayout.CENTER);
        
        // Button panel
        Panel buttonPanel = new Panel(new FlowLayout(FlowLayout.RIGHT));
        Button closeButton = new Button("Close");
        closeButton.addActionListener(e -> roomDialog.dispose());
        buttonPanel.add(closeButton);
        
        // Add all panels to the dialog
        Panel topPanel = new Panel(new BorderLayout());
        topPanel.add(infoPanel, BorderLayout.NORTH);
        topPanel.add(memberPanel, BorderLayout.CENTER);
        
        roomDialog.add(topPanel, BorderLayout.NORTH);
        roomDialog.add(messagePanel, BorderLayout.CENTER);
        roomDialog.add(buttonPanel, BorderLayout.SOUTH);
        
        // Center the dialog on the parent frame
        roomDialog.setLocationRelativeTo(parentFrame);
        
        // Display the dialog
        roomDialog.setVisible(true);
    }
    
    /**
     * Inner class representing a room
     */
    public static class RoomData {
        private int isAi;
        private int id;
        private String name;
        private int maxMembers;
        private List<RoomMember> members;
        private List<RoomMessage> messages;
        
        public RoomData(int isAi, int id, String name, int maxMembers, 
                       List<RoomMember> members, List<RoomMessage> messages) {
            this.isAi = isAi;
            this.id = id;
            this.name = name;
            this.maxMembers = maxMembers;
            this.members = members;
            this.messages = messages;
        }
        
        // Getters
        public int getIsAi() { return isAi; }
        public int getId() { return id; }
        public String getName() { return name; }
        public int getMaxMembers() { return maxMembers; }
        public List<RoomMember> getMembers() { return members; }
        public List<RoomMessage> getMessages() { return messages; }
    }
    
    /**
     * Inner class representing a room member
     */
    public static class RoomMember {
        private String id;
        
        public RoomMember(String id) {
            this.id = id;
        }
        
        public String getId() { return id; }
    }
    
    /**
     * Inner class representing a message in the room
     */
    public static class RoomMessage {
        private String time;
        private String sender;
        private String content;
        
        public RoomMessage(String time, String sender, String content) {
            this.time = time;
            this.sender = sender;
            this.content = content;
        }
        
        // Getters
        public String getTime() { return time; }
        public String getSender() { return sender; }
        public String getContent() { return content; }
    }
}