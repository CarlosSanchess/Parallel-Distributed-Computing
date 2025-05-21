import java.awt.*;
import java.awt.event.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.regex.*;

/**
 * ChatRoomUI - A pure AWT implementation for displaying chat room data
 * with improved visual representation
 */
public class ChatRoomUI {
    private Frame frame;
    private Panel mainPanel;
    private Panel roomInfoPanel;
    private TextArea messagesArea;
    private Panel membersPanel;
    private List<Label> memberLabels = new ArrayList<>();
    
    private String roomName;
    private int roomId;
    private boolean isAi;
    private int maxMembers;
    private List<String> members;
    private List<ChatMessage> messages;
    
    /**
     * Integration with your existing TimeClient
     */
    public static void enhanceTimeClient(TimeClient client) {
        // This method would be called to integrate the ChatRoomUI with your TimeClient
        // Implementation depends on how you want to integrate it
    }
    
    /**
     * Creates a new ChatRoomUI window
     */
    public ChatRoomUI() {
        initializeUI();
    }
    
    private void initializeUI() {
        // Create the main frame
        frame = new Frame("Chat Room");
        frame.setSize(800, 600);
        
        // Create the main panel with BorderLayout
        mainPanel = new Panel(new BorderLayout(5, 5));
        mainPanel.setBackground(new Color(245, 245, 245));
        
        // Create room info panel (top)
        roomInfoPanel = new Panel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        roomInfoPanel.setBackground(new Color(230, 240, 250));
        
        // Create message area (center)
        messagesArea = new TextArea();
        messagesArea.setEditable(false);
        messagesArea.setFont(new Font("Arial", Font.PLAIN, 12));
        messagesArea.setBackground(Color.WHITE);
        
        // Create members panel (right)
        membersPanel = new Panel();
        membersPanel.setLayout(new BorderLayout(5, 5));
        membersPanel.setBackground(new Color(240, 240, 240));
        
        Panel membersHeaderPanel = new Panel(new FlowLayout(FlowLayout.LEFT));
        Label membersHeaderLabel = new Label("Members", Label.LEFT);
        membersHeaderLabel.setFont(new Font("Arial", Font.BOLD, 14));
        membersHeaderPanel.add(membersHeaderLabel);
        
        Panel membersListPanel = new Panel();
        membersListPanel.setLayout(new GridLayout(0, 1, 2, 2));
        
        membersPanel.add(membersHeaderPanel, BorderLayout.NORTH);
        membersPanel.add(membersListPanel, BorderLayout.CENTER);
        
        // Add components to main panel
        mainPanel.add(roomInfoPanel, BorderLayout.NORTH);
        mainPanel.add(messagesArea, BorderLayout.CENTER);
        mainPanel.add(membersPanel, BorderLayout.EAST);
        
        // Add padding
        mainPanel.add(new Panel(), BorderLayout.WEST);
        mainPanel.add(new Panel(), BorderLayout.SOUTH);
        
        // Add main panel to frame
        frame.add(mainPanel);
        
        // Add window closing handler
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                frame.dispose();
            }
        });
        
        // Default empty room state
        roomName = "No Room Selected";
        roomId = 0;
        isAi = false;
        maxMembers = 0;
        members = new ArrayList<>();
        messages = new ArrayList<>();
        
        // Update the UI with default values
        updateUI();
    }
    
    /**
     * Parse and display room information from text input
     */
    public void parseAndDisplayRoom(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        
        // Check if the text matches the expected room format
        if (!text.trim().startsWith("Room{") || !text.trim().endsWith("}")) {
            return;
        }
        
        try {
            // Extract room properties
            Pattern isAiPattern = Pattern.compile("IsAi\\s*=\\s*(\\d+)");
            Pattern idPattern = Pattern.compile("Id\\s*=\\s*(\\d+)");
            Pattern namePattern = Pattern.compile("Name\\s*=\\s*\"([^\"]*)\"");
            Pattern maxMembersPattern = Pattern.compile("MaxNumberOfMembers\\s*=\\s*(\\d+)");
            
            // Match the patterns
            Matcher isAiMatcher = isAiPattern.matcher(text);
            Matcher idMatcher = idPattern.matcher(text);
            Matcher nameMatcher = namePattern.matcher(text);
            Matcher maxMembersMatcher = maxMembersPattern.matcher(text);
            
            // Extract the values if matches are found
            if (isAiMatcher.find()) {
                isAi = Integer.parseInt(isAiMatcher.group(1)) == 1;
            }
            
            if (idMatcher.find()) {
                roomId = Integer.parseInt(idMatcher.group(1));
            }
            
            if (nameMatcher.find()) {
                roomName = nameMatcher.group(1);
            }
            
            if (maxMembersMatcher.find()) {
                maxMembers = Integer.parseInt(maxMembersMatcher.group(1));
            }
            
            // Extract members list
            Pattern membersPattern = Pattern.compile("Members\\s*=\\s*\\[(.*?)\\]", Pattern.DOTALL);
            Matcher membersMatcher = membersPattern.matcher(text);
            
            members = new ArrayList<>();
            if (membersMatcher.find()) {
                String membersText = membersMatcher.group(1);
                String[] memberLines = membersText.trim().split(",\\s*");
                
                for (String memberLine : memberLines) {
                    if (!memberLine.trim().isEmpty()) {
                        // Extract member name or ID from the line
                        // Format is typically: Model.Client@bb09ddd
                        String memberId = memberLine.trim();
                        members.add(memberId);
                    }
                }
            }
            
            // Extract messages list
            Pattern messagesPattern = Pattern.compile("Messages\\s*=\\s*\\[(.*?)\\]", Pattern.DOTALL);
            Matcher messagesMatcher = messagesPattern.matcher(text);
            
            messages = new ArrayList<>();
            if (messagesMatcher.find()) {
                String messagesText = messagesMatcher.group(1);
                String[] messageLines = messagesText.trim().split(",\\s*");
                
                for (String messageLine : messageLines) {
                    messageLine = messageLine.trim();
                    if (!messageLine.isEmpty()) {
                        // Format is typically: [15:52] yy: asdsad
                        Pattern messageDetailPattern = Pattern.compile("\\[(\\d+:\\d+)\\]\\s+(.*?):\\s+(.*)");
                        Matcher messageDetailMatcher = messageDetailPattern.matcher(messageLine);
                        
                        if (messageDetailMatcher.find()) {
                            String time = messageDetailMatcher.group(1);
                            String sender = messageDetailMatcher.group(2);
                            String content = messageDetailMatcher.group(3);
                            
                            messages.add(new ChatMessage(time, sender, content));
                        }
                    }
                }
            }
            
            // Update the UI with the parsed information
            updateUI();
            
            // Make the frame visible if it's not already
            if (!frame.isVisible()) {
                frame.setVisible(true);
            }
            
        } catch (Exception e) {
            System.err.println("Error parsing room data: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Update the UI with the current room information
     */
    private void updateUI() {
        // Update room info panel
        roomInfoPanel.removeAll();
        
        Label roomLabel = new Label(roomName);
        roomLabel.setFont(new Font("Arial", Font.BOLD, 16));
        roomInfoPanel.add(roomLabel);
        
        Label idLabel = new Label(" (ID: " + roomId + ")");
        idLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        idLabel.setForeground(Color.GRAY);
        roomInfoPanel.add(idLabel);
        
        Label memberCountLabel = new Label(" - Members: " + members.size() + "/" + maxMembers);
        memberCountLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        roomInfoPanel.add(memberCountLabel);
        
        if (isAi) {
            Label aiLabel = new Label(" [AI Room]");
            aiLabel.setFont(new Font("Arial", Font.ITALIC, 12));
            aiLabel.setForeground(new Color(0, 128, 0));
            roomInfoPanel.add(aiLabel);
        }
        
        // Update members panel
        Panel membersListPanel = (Panel) membersPanel.getComponent(1); // Get the list panel from BorderLayout.CENTER
        membersListPanel.removeAll();
        
        for (String member : members) {
            String displayName = member;
            if (member.contains("@")) {
                String[] parts = member.split("@");
                if (parts.length > 0) {
                    displayName = parts[0].substring(parts[0].lastIndexOf(".") + 1);
                }
            }
            
            Label memberLabel = new Label(displayName);
            memberLabel.setFont(new Font("Arial", Font.PLAIN, 12));
            memberLabel.setBackground(new Color(230, 230, 230));
            membersListPanel.add(memberLabel);
        }
        
        // Update message area
        StringBuilder messagesText = new StringBuilder();
        for (ChatMessage message : messages) {
            messagesText.append("[").append(message.getTime()).append("] ")
                       .append(message.getSender()).append(": ")
                       .append(message.getContent()).append("\n");
        }
        messagesArea.setText(messagesText.toString());
        
        // Repaint all components
        roomInfoPanel.validate();
        membersPanel.validate();
        frame.validate();
    }
    
    /**
     * Helper class to represent a chat message
     */
    static class ChatMessage {
        private String time;
        private String sender;
        private String content;
        
        public ChatMessage(String time, String sender, String content) {
            this.time = time;
            this.sender = sender;
            this.content = content;
        }
        
        public String getTime() {
            return time;
        }
        
        public String getSender() {
            return sender;
        }
        
        public String getContent() {
            return content;
        }
    }
    
    /**
     * Modify this method to integrate with your TimeClient
     */
    public void enhancedHandleTextMessage(String text) {
        // First, try to parse as a room message
        if (text.trim().startsWith("Room{") && text.trim().endsWith("}")) {
            parseAndDisplayRoom(text);
            return;
        }
        
        // If not a room message, continue with normal processing
    }
    
    /**
     * Inline integration method for your TimeClient's appendToOutput method
     */
    public static String handleRoomFormatting(TimeClient client, String text, TextArea outputArea) {
        // Check if this is a room format message
        if (text != null && text.trim().startsWith("Room{") && text.trim().endsWith("}")) {
            // Create and display the chat room UI
            EventQueue.invokeLater(() -> {
                ChatRoomUI roomUI = new ChatRoomUI();
                roomUI.parseAndDisplayRoom(text);
            });
            
            // Return a simplified message for the main output area
            return "Received room data. Opening room view...";
        }
        
        // If not a room message, return the original message
        return text;
    }
    
    /**
     * Sample integration method for TimeClient class
     */
    public static void modifyAppendToOutput(TimeClient client) {
        // This is a conceptual example of how you might modify the TimeClient class
        // to integrate the room parser. This would need to be adapted to your specific needs.
        
        // Example pseudo-code:
        // 
        // private void appendToOutput(String text) {
        //     EventQueue.invokeLater(() -> {
        //         if (text == null || text.trim().isEmpty()) return;
        //         
        //         // Process with room handler
        //         String displayText = ChatRoomUI.handleRoomFormatting(this, text, outputArea);
        //         
        //         // Continue with normal processing if needed
        //         handleTextMessage(displayText);
        //         outputArea.append(displayText + "\n");
        //         outputArea.setCaretPosition(outputArea.getText().length());
        //     });
        // }
    }
    
    public static void main(String[] args) {
        // Example usage
        EventQueue.invokeLater(() -> {
            ChatRoomUI chatRoomUI = new ChatRoomUI();
            
            // Test with sample data
            String sampleRoomData = "Room{\n" +
                "IsAi = 0,\n" +
                "Id = 123,\n" +
                "Name = \"Java Chat Room\",\n" +
                "MaxNumberOfMembers = 10,\n" +
                "Members = [\n" +
                "Model.Client@bb09ddd,\n" +
                "Model.Client@4dfd1551,\n" +
                "],\n" +
                "Messages = [\n" +
                "[15:52] Alice: Hello everyone!,\n" +
                "[15:53] Bob: Hi Alice, how are you?,\n" +
                "[15:54] Alice: I'm doing great, thanks for asking!,\n" +
                "[15:55] Charlie: Hey folks, what's the topic today?,\n" +
                "[15:56] Alice: We're discussing Java AWT interfaces,\n" +
                "]\n" +
                "}";
            
            chatRoomUI.parseAndDisplayRoom(sampleRoomData);
        });
    }
}