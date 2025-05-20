import java.awt.*;
import java.awt.event.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.io.*;

import Model.Package;

public class TimeClient {
    private Frame frame;
    private TextArea outputArea;
    private TextField inputField;
    private Socket socket;
    private PrintWriter writer;
    private boolean shouldReconnect = true;
    private String hostname;
    private int port;
    private Button connectButton;
    private Button disconnectButton;
    private Label statusLabel;
    private boolean isLoggedIn = false;
    private String currentUsername = null;
    private String userToken = "";
    
    private static final int MIN_WIDTH = 400;
    private static final int MIN_HEIGHT = 300;
    
    private static final int DEFAULT_WIDTH = 650;
    private static final int DEFAULT_HEIGHT = 500;
    
    private Font defaultFont;
    private Font headerFont;
    private Font inputFont;
    
    private Color backgroundColor = new Color(245, 245, 245);
    private Color headerColor = new Color(230, 230, 230);
    private Color outputBackgroundColor = new Color(255, 255, 255);

    public TimeClient(String hostname, int port) {
        this.hostname = hostname;
        this.port = port;
        
        initializeFonts();
        initializeGUI();
        connectToServer();
    }
    
    private void initializeFonts() {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice gd = ge.getDefaultScreenDevice();
        
        DisplayMode displayMode = gd.getDisplayMode();
        int screenHeight = displayMode.getHeight();
        
        float scaleFactor = (float) screenHeight / 1080f;
        
        defaultFont = new Font(Font.SANS_SERIF, Font.PLAIN, Math.round(12 * scaleFactor));
        headerFont = new Font(Font.SANS_SERIF, Font.BOLD, Math.round(13 * scaleFactor));
        inputFont = new Font(Font.SANS_SERIF, Font.PLAIN, Math.round(13 * scaleFactor));
    }

    private void initializeGUI() {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice gd = ge.getDefaultScreenDevice();
        DisplayMode displayMode = gd.getDisplayMode();
        
        int screenWidth = displayMode.getWidth();
        int screenHeight = displayMode.getHeight();
        
        int frameWidth = Math.max(MIN_WIDTH, Math.min(DEFAULT_WIDTH, (int)(screenWidth * 0.6)));
        int frameHeight = Math.max(MIN_HEIGHT, Math.min(DEFAULT_HEIGHT, (int)(screenHeight * 0.6)));
        
        frame = new Frame("Time Client (AWT)");
        frame.setSize(frameWidth, frameHeight);
        frame.setLocationRelativeTo(null); 
        frame.setLayout(new BorderLayout(5, 5));
        frame.setBackground(backgroundColor);
        
        frame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                handleResize();
            }
        });

        Panel topPanel = new Panel(new BorderLayout(10, 0));
        topPanel.setBackground(headerColor);
        statusLabel = new Label("Status: Disconnected", Label.LEFT);
        statusLabel.setFont(headerFont);
        
        Panel buttonPanel = new Panel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBackground(headerColor);
        
        connectButton = new Button("Connect");
        disconnectButton = new Button("Disconnect");
        
        connectButton.setFont(defaultFont);
        disconnectButton.setFont(defaultFont);
        
        connectButton.addActionListener(e -> {
            if (socket == null || socket.isClosed()) {
                shouldReconnect = true;
                connectToServer();
            }
        });
        
        disconnectButton.addActionListener(e -> {
            disconnect();
        });
        
        disconnectButton.setEnabled(false);
        
        buttonPanel.add(connectButton);
        buttonPanel.add(disconnectButton);
        
        topPanel.add(statusLabel, BorderLayout.WEST);
        topPanel.add(buttonPanel, BorderLayout.EAST);
        
        Panel topPanelContainer = new Panel(new BorderLayout());
        topPanelContainer.add(topPanel, BorderLayout.CENTER);
        topPanelContainer.add(new Panel(), BorderLayout.NORTH);
        topPanelContainer.add(new Panel(), BorderLayout.SOUTH); 
        topPanelContainer.add(new Panel(), BorderLayout.WEST); 
        topPanelContainer.add(new Panel(), BorderLayout.EAST); 
        
        frame.add(topPanelContainer, BorderLayout.NORTH);

        outputArea = new TextArea();
        outputArea.setEditable(false);
        outputArea.setFont(defaultFont);
        outputArea.setBackground(outputBackgroundColor);
        
        Panel outputPanel = new Panel(new BorderLayout(10, 10));
        outputPanel.add(outputArea, BorderLayout.CENTER);
        outputPanel.add(new Panel(), BorderLayout.NORTH);
        outputPanel.add(new Panel(), BorderLayout.SOUTH);
        outputPanel.add(new Panel(), BorderLayout.WEST);  
        outputPanel.add(new Panel(), BorderLayout.EAST); 
        
        frame.add(outputPanel, BorderLayout.CENTER);

        Panel inputPanel = new Panel(new BorderLayout(5, 0));
        inputField = new TextField();
        inputField.setFont(inputFont);
        inputField.addActionListener(this::handleInput);
        
        Panel sendButtonPanel = new Panel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        Button sendButton = new Button("Send");
        Button clearButton = new Button("Clear");
        Button exitButton = new Button("Exit");
        
        sendButton.setFont(defaultFont);
        clearButton.setFont(defaultFont);
        exitButton.setFont(defaultFont);
        
        sendButton.addActionListener(this::handleInput);
        
        clearButton.addActionListener(e -> {
            inputField.setText("");  
        });

        exitButton.addActionListener(e -> {
            shouldReconnect = false;
            if (writer != null) {
                Package p = new Package("/exit", userToken);
                writer.println(p.serialize());
            }
            closeConnection();
            frame.dispose();
            System.exit(0);
        });
        
        sendButtonPanel.add(clearButton);
        sendButtonPanel.add(sendButton);
        sendButtonPanel.add(exitButton);

        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendButtonPanel, BorderLayout.EAST);
        
        Panel inputPanelContainer = new Panel(new BorderLayout());
        inputPanelContainer.add(inputPanel, BorderLayout.CENTER);
        inputPanelContainer.add(new Panel(), BorderLayout.NORTH); 
        inputPanelContainer.add(new Panel(), BorderLayout.SOUTH);
        inputPanelContainer.add(new Panel(), BorderLayout.WEST);  
        inputPanelContainer.add(new Panel(), BorderLayout.EAST);  
        
        frame.add(inputPanelContainer, BorderLayout.SOUTH);

        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            if (e.getID() == KeyEvent.KEY_PRESSED && e.isControlDown() && e.getKeyCode() == KeyEvent.VK_Q) {
                shouldReconnect = false;
                closeConnection();
                frame.dispose();
                System.exit(0);
                return true;
            }
            if (e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                inputField.setText("");
                return true;
            }
            return false;
        });

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeConnection();
                frame.dispose();
                System.exit(0);
            }
        });

        MenuBar menuBar = new MenuBar();
        Menu helpMenu = new Menu("Help");
        MenuItem commands = new MenuItem("Commands");
        commands.addActionListener(e -> {
            showHelpDialog();
        });
        
        MenuItem exitItem = new MenuItem("Exit");
        exitItem.addActionListener(e -> {
            shouldReconnect = false;
            closeConnection();
            frame.dispose();
            System.exit(0);
        });
        
        helpMenu.add(commands);
        helpMenu.addSeparator();
        helpMenu.add(exitItem);
        menuBar.add(helpMenu);
        frame.setMenuBar(menuBar);

        frame.setVisible(true);
        
        inputField.requestFocus();
        
        appendToOutput("Welcome to Time Client!");
        appendToOutput("Connecting to server at " + hostname + ":" + port + "...");
        appendToOutput("Type /help to see available commands");
    }
    
    private void handleResize() {
        Dimension size = frame.getSize();
        
        boolean needsResize = false;
        if (size.width < MIN_WIDTH) {
            size.width = MIN_WIDTH;
            needsResize = true;
        }
        if (size.height < MIN_HEIGHT) {
            size.height = MIN_HEIGHT;
            needsResize = true;
        }
        
        if (needsResize) {
            frame.setSize(size);
        }
    }
    
    private void showHelpDialog() {
        int dialogWidth = Math.max(400, frame.getWidth() / 2);
        int dialogHeight = Math.max(300, frame.getHeight() / 2);
        
        Dialog helpDialog = new Dialog(frame, "Available Commands", true);
        helpDialog.setLayout(new BorderLayout(10, 10));
        helpDialog.setSize(dialogWidth, dialogHeight);
        helpDialog.setLocationRelativeTo(frame);
        helpDialog.setBackground(backgroundColor);
        
        TextArea helpText = new TextArea();
        helpText.setEditable(false);
        helpText.setFont(defaultFont);
        helpText.setBackground(outputBackgroundColor);
        
        helpText.append("Available Commands:\n\n");
        helpText.append("/exit - Exit the application\n");
        helpText.append("/disconnect - Disconnect from server\n");
        helpText.append("/reconnect - Reconnect to server\n");
        helpText.append("/clear - Clear the output window\n");
        helpText.append("/help - Show this help message\n");
        helpText.append("/register <username> <password> - Register a new account\n");
        helpText.append("/login <username> <password> - Login to your account\n");
        helpText.append("/logout - Logout from your account\n\n");
        helpText.append("Keyboard Shortcuts:\n");
        helpText.append("Ctrl+Q - Exit application\n");
        helpText.append("Esc - Clear input field\n");
        
        Button closeButton = new Button("Close");
        closeButton.setFont(defaultFont);
        closeButton.addActionListener(e -> helpDialog.dispose());
        
        Panel buttonPanel = new Panel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.add(closeButton);
        
        helpDialog.add(helpText, BorderLayout.CENTER);
        helpDialog.add(buttonPanel, BorderLayout.SOUTH);
        
        helpDialog.setVisible(true);
    }

    private void connectToServer() {
        connectButton.setEnabled(false);
        
        new Thread(() -> {
            while (shouldReconnect) {
                try {
                    EventQueue.invokeLater(() -> {
                        statusLabel.setText("Status: Connecting...");
                    });
                    
                    socket = new Socket(hostname, port);
                    writer = new PrintWriter(socket.getOutputStream(), true);
                    
                    new Thread(new ServerResponseHandler(socket)).start();
                    
                    EventQueue.invokeLater(() -> {
                        statusLabel.setText("Status: Connected");
                        disconnectButton.setEnabled(true);
                        appendToOutput("Connected to server at " + hostname + ":" + port);
                    });
                    break;
                } catch (IOException ex) {
                    final String errorMsg = ex.getMessage();
                    EventQueue.invokeLater(() -> {
                        statusLabel.setText("Status: Connection failed");
                        connectButton.setEnabled(true);
                        disconnectButton.setEnabled(false);
                        appendToOutput("Connection failed: " + errorMsg);
                        appendToOutput("Retrying in 5 seconds... (Press Disconnect to cancel)");
                    });
                    
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    
                    if (!shouldReconnect) {
                        EventQueue.invokeLater(() -> {
                            statusLabel.setText("Status: Disconnected");
                            connectButton.setEnabled(true);
                        });
                        return;
                    }
                }
            }
        }).start();
    }

    private void handleInput(ActionEvent e) {
        String input = inputField.getText();
        if (input.trim().isEmpty()) {
            return;
        }
        
        inputField.setText("");
        
        if (input.startsWith("/")) {
            String[] parts = input.split("\\s+", 2);
            String command = parts[0].toLowerCase();
            
            switch (command) {
                case "/exit":
                    shouldReconnect = false;
                    appendToOutput("Exiting application...");
                    if (writer != null) {
                        Package p = new Package(input, userToken);
                        writer.println(p.serialize());
                    }
                    closeConnection();
                    frame.dispose();
                    System.exit(0);
                    return;
                    
                case "/disconnect":
                    disconnect();
                    return;
                    
                case "/reconnect":
                    appendToOutput("Reconnecting to server...");
                    closeConnection();
                    shouldReconnect = true;
                    connectToServer();
                    return;
                    
                case "/clear":
                    outputArea.setText("");
                    return;
                    
                case "/help":
                    showHelpDialog();
                    return;
            }
        }
        
        if (writer != null && input != null) {
            Package p = new Package(input, userToken);
            writer.println(p.serialize());
        } else {
            appendToOutput("Not connected to server. Please connect first.");
        }
    }

    private void appendToOutput(String text) {
        EventQueue.invokeLater(() -> {
            if (text == null || text.trim().isEmpty()) return;
            
            if (handleTextMessage(text)) {
                return;  
            }
            
            if (text.startsWith("Room{")) {
                String roomInfo = parseAndFormatRoom(text);
                outputArea.append(roomInfo + "\n");
            } else {
                outputArea.append(text + "\n");
            }
            
            outputArea.setCaretPosition(outputArea.getText().length());
        });
    }
    
    private boolean handleTextMessage(String text) {
        if (text.contains("\u001b[H\u001b[2J") || 
            text.contains("\u001b[2J") || 
            text.contains("\u001b[3J") ||
            text.contains("\u001bc")) {
            outputArea.setText("");
            return true;
        }
    
        if (text.startsWith("Login successful for user: ")) {
            isLoggedIn = true;
            currentUsername = text.substring("Login successful for user: ".length());
            outputArea.append(text + "\n");
            return true;
        }
    
        if (text.equals("Logged out successfully")) {
            isLoggedIn = false;
            currentUsername = null;
            outputArea.append(text + "\n");
            return true;
        }
    
        if (text.startsWith("REGISTRATION_SUCCESS")) {
            isLoggedIn = true;
            currentUsername = text.substring("REGISTRATION_SUCCESS ".length());
            shouldReconnect = true;
            outputArea.append("Registration successful! Welcome " + currentUsername + "\n");
            return true;
        }
    
        if (text.startsWith("REGISTRATION_ERROR")) {
            shouldReconnect = false;
            String errorMsg = text.substring("REGISTRATION_ERROR ".length());
            outputArea.append("Registration failed: " + errorMsg + "\n");
            return true;
        }
    
        if (text.startsWith("Registration successful for user: ")) {
            isLoggedIn = true;
            currentUsername = text.substring("Registration successful for user: ".length());
            shouldReconnect = true;
            outputArea.append("Registration successful! Welcome " + currentUsername + "\n");
            return true;
        }
    
        if (text.equals("Username already exists") || text.equals("Username already taken")) {
            shouldReconnect = false;
            outputArea.append("Registration failed: Username already exists\n");
            return true;
        }
    
        if (text.startsWith("Error during registration")) {
            shouldReconnect = false;
            outputArea.append("Registration failed: " + text.substring("Error during registration".length()) + "\n");
            return true;
        }
        return false;
    }
    
    private String parseAndFormatRoom(String roomText) {
        try {
            String roomName = extractField(roomText, "Name = \"", "\"");
            String maxMembers = extractField(roomText, "MaxNumberOfMembers = ", ",");
            String isAi = extractField(roomText, "IsAi = ", ",");
            String roomId = extractField(roomText, "Id = ", ",");
            
            String membersSection = extractField(roomText, "Members = [", "]");
            String[] members = membersSection.isEmpty() ? new String[0] : membersSection.split(", ");
            
            String messagesSection = extractField(roomText, "Messages = [", "]");
            String[] messages = messagesSection.isEmpty() ? new String[0] : messagesSection.split(", ");
            
            int outputWidth = outputArea.getSize().width;
            int charsPerLine = Math.max(60, outputWidth / 7);
            
            StringBuilder sb = new StringBuilder();
            
            String aiTag = isAi.equals("1") ? " [AI]" : "";
            sb.append(roomName).append(aiTag).append("\n");
            sb.append("Room ID: ").append(roomId).append(" | Members: ").append(members.length)
              .append("/").append(maxMembers).append("\n");
            
            sb.append(createLine('-', charsPerLine)).append("\n");
            
            sb.append("PARTICIPANTS:\n");
            if (members.length > 0) {
                for (String member : members) {
                    sb.append("• ").append(member.trim()).append("\n");
                }
            } else {
                sb.append("(no participants)\n");
            }
            
            sb.append(createLine('-', charsPerLine)).append("\n");
            
            sb.append("RECENT MESSAGES:\n");
            if (messages.length > 0) {
                int start = Math.max(0, messages.length - 5);
                for (int i = start; i < messages.length; i++) {
                    String messageText = messages[i].trim();
                    wrapMessage(sb, messageText, charsPerLine);
                }
            } else {
                sb.append("(no messages)\n");
            }
            
            sb.append(createLine('-', charsPerLine));
            return sb.toString();
        } catch (Exception e) {
            return "Failed to parse room info: " + e.getMessage();
        }
    }

    private void wrapMessage(StringBuilder sb, String message, int maxWidth) {
        String[] words = message.split("\\s+");
        StringBuilder line = new StringBuilder();
        boolean isFirstLine = true;
        
        for (String word : words) {
            if ((line.length() + word.length() + 1) > maxWidth && line.length() > 0) {
                if (isFirstLine) {
                    sb.append("• ").append(line.toString().trim()).append("\n");
                    isFirstLine = false;
                } else {
                    sb.append("  ").append(line.toString().trim()).append("\n");
                }
                line = new StringBuilder();
            }
            
            if (line.length() > 0) {
                line.append(" ");
            }
            line.append(word);
        }
        
        if (line.length() > 0) {
            if (isFirstLine) {
                sb.append("• ").append(line.toString().trim()).append("\n");
            } else {
                sb.append("  ").append(line.toString().trim()).append("\n");
            }
        }
    }

    private String createLine(char c, int length) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < length; i++) {
            line.append(c);
        }
        return line.toString();
    }

    private String extractField(String text, String startDelim, String endDelim) {
        try {
            int start = text.indexOf(startDelim) + startDelim.length();
            int end = text.indexOf(endDelim, start);
            return text.substring(start, end).trim();
        } catch (Exception e) {
            return "";
        }
    }

    private void closeConnection() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
                socket = null;
            }
            writer = null;
        } catch (IOException e) {
            appendToOutput("Error closing socket: " + e.getMessage());
        }
    }

    private class ServerResponseHandler implements Runnable {
        private final Socket socket;

        public ServerResponseHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()))) {
                
                Package serverResponse;
                while ((serverResponse = Model.Package.readInput(reader)) != null) {
                    System.out.println(serverResponse.getMessage());
                    appendToOutput(serverResponse.getMessage());
                    if(serverResponse.getToken() != null && serverResponse.getToken().length() > 1){
                        userToken = serverResponse.getToken();
                    }
                }
                
                appendToOutput("Server has disconnected");
                
                EventQueue.invokeLater(() -> {
                    statusLabel.setText("Status: Disconnected");
                    connectButton.setEnabled(true);
                    disconnectButton.setEnabled(false);
                    isLoggedIn = false;
                    currentUsername = null;
                });
                
                if (shouldReconnect) {
                    appendToOutput("Attempting to reconnect...");
                    connectToServer();
                }
                
            } catch (IOException ex) {
                EventQueue.invokeLater(() -> {
                    statusLabel.setText("Status: Disconnected");
                    connectButton.setEnabled(true);
                    disconnectButton.setEnabled(false);
                    isLoggedIn = false;
                    currentUsername = null;
                });
                
                if (shouldReconnect) {
                    appendToOutput("Connection lost. Attempting to reconnect...");
                    connectToServer();
                } else {
                    appendToOutput("Error reading server response: " + ex.getMessage());
                }
            }
        }
    }

    private void disconnect(){
        shouldReconnect = false;
        appendToOutput("Disconnecting from server...");
        if (writer != null) {
            Package p = new Package("/disconnect", userToken);
            writer.println(p.serialize());
        }
        closeConnection();
        statusLabel.setText("Status: Disconnected");
        connectButton.setEnabled(true);
        disconnectButton.setEnabled(false);
        isLoggedIn = false;
        currentUsername = null;
    }

    public static void main(String[] args) {
        String hostname = "localhost";
        int port = 8080;
        
        if (args.length >= 2) {
            hostname = args[0];
            port = Integer.parseInt(args[1]);
            String finalHostname = hostname;
            int finalPort = port;
            EventQueue.invokeLater(() -> new TimeClient(finalHostname, finalPort));
        } else {
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsDevice gd = ge.getDefaultScreenDevice();
            DisplayMode displayMode = gd.getDisplayMode();
            
            int screenWidth = displayMode.getWidth();
            int screenHeight = displayMode.getHeight();
            
            int dialogWidth = (int)(screenWidth * 0.3);
            int dialogHeight = (int)(screenHeight * 0.2);
            
            dialogWidth = Math.max(300, Math.min(dialogWidth, 400));
            dialogHeight = Math.max(150, Math.min(dialogHeight, 200));
            
            Frame dialogFrame = new Frame("Connection Settings");
            dialogFrame.setSize(dialogWidth, dialogHeight);
            dialogFrame.setLayout(new GridLayout(3, 1, 10, 10));
            
            float scaleFactor = (float) screenHeight / 1080f;
            Font dialogFont = new Font(Font.SANS_SERIF, Font.PLAIN, Math.round(12 * scaleFactor));
            
            Panel hostPanel = new Panel(new BorderLayout(5, 0));
            Label hostLabel = new Label("Hostname:");
            hostLabel.setFont(dialogFont);
            TextField hostnameField = new TextField(hostname);
            hostnameField.setFont(dialogFont);
            hostPanel.add(hostLabel, BorderLayout.WEST);
            hostPanel.add(hostnameField, BorderLayout.CENTER);
            
            Panel portPanel = new Panel(new BorderLayout(5, 0));
            Label portLabel = new Label("Port:");
            portLabel.setFont(dialogFont);
            TextField portField = new TextField(String.valueOf(port));
            portField.setFont(dialogFont);
            portPanel.add(portLabel, BorderLayout.WEST);
            portPanel.add(portField, BorderLayout.CENTER);
            
            dialogFrame.add(hostPanel);
            dialogFrame.add(portPanel);
            
            Button okButton = new Button("Connect");
            Button cancelButton = new Button("Cancel");
            okButton.setFont(dialogFont);
            cancelButton.setFont(dialogFont);
            
            Panel buttonPanel = new Panel(new FlowLayout(FlowLayout.CENTER, 10, 0));
            buttonPanel.add(okButton);
            buttonPanel.add(cancelButton);
            
            dialogFrame.add(buttonPanel);
            
            okButton.addActionListener(e -> {
                try {
                    String inputHostname = hostnameField.getText();
                    int inputPort = Integer.parseInt(portField.getText());
                    dialogFrame.dispose();
                    EventQueue.invokeLater(() -> new TimeClient(inputHostname, inputPort));
                } catch (NumberFormatException ex) {
                    Dialog errorDialog = new Dialog(dialogFrame, "Error", true);
                    errorDialog.setLayout(new BorderLayout(10, 10));
                    
                    Label errorLabel = new Label("Invalid port number. Please enter a valid integer.", Label.CENTER);
                    errorLabel.setFont(dialogFont);
                    errorDialog.add(errorLabel, BorderLayout.CENTER);
                    
                    Button closeButton = new Button("OK");
                    closeButton.setFont(dialogFont);
                    closeButton.addActionListener(evt -> errorDialog.dispose());
                    
                    Panel errorButtonPanel = new Panel(new FlowLayout(FlowLayout.CENTER));
                    errorButtonPanel.add(closeButton);
                    errorDialog.add(errorButtonPanel, BorderLayout.SOUTH);
                    
                    errorDialog.pack();
                    errorDialog.setLocationRelativeTo(dialogFrame);
                    errorDialog.setVisible(true);
                }
            });
            
            cancelButton.addActionListener(e -> {
                dialogFrame.dispose();
                System.exit(0);
            });
            
            dialogFrame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    dialogFrame.dispose();
                    System.exit(0);
                }
            });
            
            dialogFrame.setLocationRelativeTo(null);
            dialogFrame.setVisible(true);
        }
    }
}