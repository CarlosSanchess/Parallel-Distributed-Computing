import java.awt.*;
import java.awt.event.*;
import java.net.*;
import java.security.KeyStore;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;

import java.io.*;

import Model.Package;

public class TimeClient {
    private Frame frame;
    private TextArea outputArea;
    private TextField inputField;
    private SSLSocket socket;
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
    
    private Panel roomInfoPanel;
    private String currentRoomId = null;
    private Label roomNameLabel;
    private Label roomMembersLabel;
    private java.awt.List participantsList;
    private boolean inRoom = false;
    private ScrollPane participantsScrollPane;
    
    private static final int MIN_WIDTH = 400;
    private static final int MIN_HEIGHT = 300;
    
    private static final int DEFAULT_WIDTH = 650;
    private static final int DEFAULT_HEIGHT = 500;
    
    private Font defaultFont;
    private Font headerFont;
    private Font inputFont;
    private Font roomNameFont;
    
    private Color backgroundColor = new Color(245, 245, 245);
    private Color headerColor = new Color(230, 230, 230);
    private Color outputBackgroundColor = new Color(255, 255, 255);
    private Color roomInfoBackgroundColor = new Color(240, 248, 255);

    private static final String PROTOCOL = "TLSv1.3";
    private static final String TRUSTSTORE_PATH_ENV = "TIMECLIENT_TRUSTSTORE_PATH";
    private static final String TRUSTSTORE_PASSWORD_ENV = "TIMECLIENT_TRUSTSTORE_PASSWORD";
    
    private static final Color MESSAGE_TEXT_COLOR = new Color(50, 50, 50);
    private static final Color TIMESTAMP_COLOR = new Color(100, 100, 100);
    private static final int SEPARATOR_PADDING = 3;

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
        roomNameFont = new Font(Font.SANS_SERIF, Font.BOLD, Math.round(14 * scaleFactor));
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

        initializeRoomInfoPanel();

        Panel centerPanel = new Panel(new BorderLayout());
        centerPanel.add(roomInfoPanel, BorderLayout.NORTH);

        outputArea = new TextArea() {
            @Override
            public void paint(Graphics g) {
                Graphics2D g2d = (Graphics2D)g;
                g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, 
                                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                super.paint(g2d);
            }
        };
        outputArea.setEditable(false);
        outputArea.setFont(defaultFont);
        outputArea.setBackground(outputBackgroundColor);

        Panel outputPanel = new Panel(new BorderLayout());
        outputPanel.add(outputArea, BorderLayout.CENTER);

        Panel topPad = new Panel();
        topPad.setPreferredSize(new Dimension(0, 10));
        Panel bottomPad = new Panel();
        bottomPad.setPreferredSize(new Dimension(0, 10));
        Panel leftPad = new Panel();
        leftPad.setPreferredSize(new Dimension(15, 0));
        Panel rightPad = new Panel();
        rightPad.setPreferredSize(new Dimension(15, 0));

        outputPanel.add(topPad, BorderLayout.NORTH);
        outputPanel.add(bottomPad, BorderLayout.SOUTH);
        outputPanel.add(leftPad, BorderLayout.WEST);
        outputPanel.add(rightPad, BorderLayout.EAST);

        centerPanel.add(outputPanel, BorderLayout.CENTER);
        frame.add(centerPanel, BorderLayout.CENTER);

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

        roomInfoPanel.setVisible(false);

        appendToOutput("Welcome to Time Client!");
        appendToOutput("Connecting to server at " + hostname + ":" + port + "...");
        appendToOutput("Type /help to see available commands");
    }

    
    private void initializeRoomInfoPanel() {
        roomInfoPanel = new Panel(new BorderLayout());
        roomInfoPanel.setBackground(roomInfoBackgroundColor);
        
        Panel roomHeaderPanel = new Panel(new BorderLayout());
        roomHeaderPanel.setBackground(roomInfoBackgroundColor);
        
        roomNameLabel = new Label("No Room Selected", Label.LEFT);
        roomNameLabel.setFont(roomNameFont);
        
        roomMembersLabel = new Label("Members: 0/0", Label.RIGHT);
        roomMembersLabel.setFont(defaultFont);
        
        roomHeaderPanel.add(roomNameLabel, BorderLayout.WEST);
        roomHeaderPanel.add(roomMembersLabel, BorderLayout.EAST);
        
        Panel participantsPanel = new Panel(new BorderLayout());
        participantsPanel.setBackground(roomInfoBackgroundColor);
        
        Label participantsHeaderLabel = new Label("Participants:", Label.LEFT);
        participantsHeaderLabel.setFont(defaultFont);
        
        participantsList = new java.awt.List(5, false); 
        participantsList.setFont(defaultFont);
        participantsList.setBackground(Color.WHITE);
        
        participantsScrollPane = new ScrollPane(ScrollPane.SCROLLBARS_AS_NEEDED); 
        participantsScrollPane.add(participantsList);
        
        ScrollPane scrollPane = new ScrollPane(ScrollPane.SCROLLBARS_AS_NEEDED);
        scrollPane.add(participantsList);
        
        participantsPanel.add(participantsHeaderLabel, BorderLayout.NORTH);
        participantsPanel.add(scrollPane, BorderLayout.CENTER); 
        
        Panel mainContent = new Panel(new BorderLayout(10, 5));
        mainContent.setBackground(roomInfoBackgroundColor);
        mainContent.add(roomHeaderPanel, BorderLayout.NORTH);
        mainContent.add(participantsPanel, BorderLayout.CENTER);
        
        roomInfoPanel.add(new Panel(), BorderLayout.NORTH);
        roomInfoPanel.add(new Panel(), BorderLayout.WEST);
        roomInfoPanel.add(new Panel(), BorderLayout.EAST);
        roomInfoPanel.add(new Panel(), BorderLayout.SOUTH);
        roomInfoPanel.add(mainContent, BorderLayout.CENTER);
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
        
        outputArea.repaint();
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
        helpText.append("/create - Create a new Room\n");
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
                    statusLabel.setText("Status: Establishing secure connection...");
                });
                
                SSLContext sslContext = createSSLContext();
                SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
                
                // Create SSL socket with improved timeout handling
                SSLSocket sslSocket = (SSLSocket) sslSocketFactory.createSocket();
                sslSocket.connect(new InetSocketAddress(hostname, port), 5000);
                
                // Configure SSL parameters
                sslSocket.setEnabledProtocols(new String[] {"TLSv1.3"});
                sslSocket.setEnabledCipherSuites(getStrongCipherSuites(sslSocket.getSupportedCipherSuites()));
                
                // Enable session reuse
                sslSocket.setEnableSessionCreation(true);
                sslSocket.setUseClientMode(true);
                
                try {
                    // Start handshake with timeout
                    sslSocket.setSoTimeout(5000);
                    sslSocket.startHandshake();
                    sslSocket.setSoTimeout(0); // Reset timeout after handshake
                    
                    this.socket = sslSocket;
                    writer = new PrintWriter(new BufferedWriter(
                        new OutputStreamWriter(sslSocket.getOutputStream(), StandardCharsets.UTF_8)), true);
                    
                    new Thread(new ServerResponseHandler(sslSocket)).start();
                    
                    EventQueue.invokeLater(() -> {
                        statusLabel.setText("Status: Connected (Secure)");
                        disconnectButton.setEnabled(true);
                        appendToOutput("Secure connection established (TLS 1.3)");
                        appendToOutput("Using cipher: " + sslSocket.getSession().getCipherSuite());
                    });
                    break;
                    
                } catch (SocketTimeoutException e) {
                    throw new IOException("SSL handshake timed out", e);
                }
                
            } catch (Exception ex) {
                final String errorMsg = ex.getMessage();
                EventQueue.invokeLater(() -> {
                    statusLabel.setText("Status: Connection failed");
                    connectButton.setEnabled(true);
                    disconnectButton.setEnabled(false);
                    appendToOutput("Secure connection failed: " + errorMsg);
                    appendToOutput("Retrying in 5 seconds... (Press Disconnect to cancel)");
                });
                
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
                
                if (!shouldReconnect) {
                    EventQueue.invokeLater(() -> {
                        statusLabel.setText("Status: Disconnected");
                        connectButton.setEnabled(true);
                    });
                    break;
                }
            }
        }
    }).start();
}

private String[] getStrongCipherSuites(String[] supportedCipherSuites) {
    java.util.List<String> strongCiphers = new ArrayList<>();
    for (String cipher : supportedCipherSuites) {
        // Only use strong ciphers with forward secrecy
        if (cipher.contains("TLS_AES_") || 
            cipher.contains("TLS_CHACHA20_") ||
            (cipher.contains("ECDHE") && !cipher.contains("NULL") && 
             !cipher.contains("anon") && !cipher.contains("RC4") &&
             !cipher.contains("DES"))) {
            strongCiphers.add(cipher);
        }
    }
    return strongCiphers.toArray(new String[0]);
}

private void closeConnection() {
    try {
        if (socket != null) {
            // Proper SSL session closure
            socket.close();
            SSLSession session = ((SSLSocket)socket).getSession();
            session.invalidate();
            socket = null;
        }
        writer = null;
    } catch (IOException e) {
        appendToOutput("Error closing secure connection: " + e.getMessage());
    }
}

    private SSLContext createSSLContext() throws Exception {
        String truststorePath = System.getenv(TRUSTSTORE_PATH_ENV);
        if (truststorePath == null) {
            truststorePath = "client.truststore"; 
        }
        
        String truststorePassword = System.getenv(TRUSTSTORE_PASSWORD_ENV);
        if (truststorePassword == null) {
            System.err.println("WARNING: Truststore password not set in environment variables");
            truststorePassword = "clientpass"; 
        }
        
        KeyStore trustStore = KeyStore.getInstance("JKS");
        try (FileInputStream fis = new FileInputStream(truststorePath)) {
            trustStore.load(fis, truststorePassword.toCharArray());
        }
        
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);
        
        SSLContext sslContext = SSLContext.getInstance(PROTOCOL);
        sslContext.init(null, trustManagerFactory.getTrustManagers(), new SecureRandom());
        
        return sslContext;
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
                
                case "/leave":
                    leaveRoom();
                    break;
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
                updateRoomInfoPanel(text);
                
                String messagesSection = extractField(text, "Messages = [", "]");
                if (!messagesSection.isEmpty()) {
                    String[] messages = processArrayContent(messagesSection);
                    if (messages.length > 0) {
                        displayRecentMessages(messages);
                    }
                }
                
            } else {
                displayMessage(text, false);
            }
            
        });
    }
    
    private String createSeparator(String text) {
        FontMetrics metrics = outputArea.getFontMetrics(outputArea.getFont());

        int estimatedPadding = 10; 
        int availableWidth = outputArea.getWidth() - (estimatedPadding * 2) - (SEPARATOR_PADDING * 2);

        int dashWidth = metrics.stringWidth("-");
        int maxDashes = dashWidth > 0 ? availableWidth / dashWidth : 30;

        if (text != null && !text.isEmpty()) {
            int textWidth = metrics.stringWidth(text);
            int dashesEachSide = Math.max(3, (maxDashes - (textWidth / dashWidth)) / 2);
            return "\n" + 
                repeatChar('-', dashesEachSide) + " " + text + " " + 
                repeatChar('-', dashesEachSide) + "\n\n";
        } else {
            return "\n" + repeatChar('-', maxDashes) + "\n\n";
        }
    }


    private String repeatChar(char c, int count) {
        return new String(new char[count]).replace('\0', c);
    }

    private void displayRecentMessages(String[] messages) {
        String separator = createSeparator("Recent Messages");
        outputArea.append(separator);
        
        for (String message : messages) {
            if (!message.trim().isEmpty()) {
                displayMessage(message.trim(), true);
            }
        }
        
        outputArea.append(createSeparator(null));
    }

    private void displayMessage(String message, boolean isHistory) {
        Font messageFont = new Font(defaultFont.getName(), Font.PLAIN, defaultFont.getSize());
        Font timestampFont = new Font(defaultFont.getName(), Font.ITALIC, defaultFont.getSize() - 1);
        
        if (message.matches("^\\[\\d{2}:\\d{2}:\\d{2}\\].*")) {
            int timestampEnd = message.indexOf("]") + 1;
            String timestamp = message.substring(0, timestampEnd);
            String messageContent = message.substring(timestampEnd).trim();
            
            outputArea.setFont(timestampFont);
            outputArea.setForeground(TIMESTAMP_COLOR);
            outputArea.append(timestamp + " ");
            
            outputArea.setFont(messageFont);
            outputArea.setForeground(MESSAGE_TEXT_COLOR);
            outputArea.append(messageContent);
        } else {
            outputArea.setFont(messageFont);
            outputArea.setForeground(MESSAGE_TEXT_COLOR);
            outputArea.append(message);
        }
        
        outputArea.append("\n");
        
        if (isHistory) {
            outputArea.append("\n");
        }
        
        outputArea.setFont(defaultFont);
        outputArea.setForeground(Color.BLACK);
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
            leaveRoom();
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
        
        if (text.contains("You left the room") || text.contains("You have left the room")) {
            leaveRoom();
            outputArea.append(text + "\n");
            return true;
        }
        
        return false;
    }
    
    private void updateRoomInfoPanel(String roomText) {
        try {
            String roomName = extractField(roomText, "Name = \"", "\"");
            String maxMembers = extractField(roomText, "MaxNumberOfMembers = ", ",");
            String isAi = extractField(roomText, "IsAi = ", ",");
            String roomId = extractField(roomText, "Id = ", ",");
            
            String membersSection = extractField(roomText, "Members = [", "]");
            String[] members = processArrayContent(membersSection);
            
            String aiTag = isAi.equals("1") ? " [AI]" : "";
            roomNameLabel.setText(roomName + aiTag);
            roomMembersLabel.setText("Members: " + members.length + "/" + maxMembers);
            
            int maxMembersInt;
            try {
                maxMembersInt = Integer.parseInt(maxMembers);
            } catch (NumberFormatException e) {
                maxMembersInt = 5; 
            }
            
            int itemHeight = participantsList.getFontMetrics(participantsList.getFont()).getHeight() + 4;
            int minHeight = 5 * itemHeight;  
            int maxHeight = 15 * itemHeight; 
            int preferredHeight = Math.min(maxHeight, Math.max(minHeight, maxMembersInt * itemHeight));
            
            participantsList.removeAll();
            for (String member : members) {
                participantsList.add(member);
            }
            
            Dimension currentSize = participantsScrollPane.getSize();
            participantsScrollPane.setPreferredSize(
                new Dimension(currentSize.width, preferredHeight)
            );
            
            inRoom = true;
            currentRoomId = roomId;
            roomInfoPanel.setVisible(true);
            
            roomInfoPanel.validate();
            frame.validate();
            
        } catch (Exception e) {
            appendToOutput("Failed to update room info: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void leaveRoom() {
        EventQueue.invokeLater(() -> {
            inRoom = false;
            currentRoomId = null;
            roomInfoPanel.setVisible(false);
            frame.validate();
        });
    }

    private String extractField(String text, String startDelim, String endDelim) {
        try {
            int start = text.indexOf(startDelim);
            if (start == -1) return "";
            
            start += startDelim.length();
            
            int end;
            if (startDelim.equals("Messages = [") || startDelim.equals("Members = [")) {
                end = findMatchingClosingBracket(text, start);
            } else {
                end = text.indexOf(endDelim, start);
            }
            
            if (end == -1) return "";
            return text.substring(start, end).trim();
        } catch (Exception e) {
            return "";
        }
    }

    private String[] processArrayContent(String arrayContent) {
        if (arrayContent == null || arrayContent.isEmpty()) {
            return new String[0];
        }
        
        if (arrayContent.endsWith(",")) {
            arrayContent = arrayContent.substring(0, arrayContent.length() - 1);
        }
        
        String[] items = arrayContent.split(", ");
        
        ArrayList<String> filteredItems = new ArrayList<>();
        for (String item : items) {
            if (item != null && !item.trim().isEmpty()) {
                filteredItems.add(item.trim());
            }
        }
        
        return filteredItems.toArray(new String[0]);
    }

    private int findMatchingClosingBracket(String text, int startPos) {
        int depth = 0;
        boolean inQuotes = false;
        
        for (int i = startPos; i < text.length(); i++) {
            char c = text.charAt(i);
            
            if (c == '\"') {
                inQuotes = !inQuotes;
            } else if (!inQuotes) {
                if (c == '[') {
                    depth++;
                } else if (c == ']') {
                    if (depth == 0) {
                        return i;
                    }
                    depth--;
                }
            }
        }
        
        return -1;
    }

    public void addMessage(String message) {
        if (message != null && !message.trim().isEmpty()) {
            appendToOutput(message);
        }
    }


    private void handleSSLException(SSLException e) {
    EventQueue.invokeLater(() -> {
        statusLabel.setText("Status: SSL Error");
        appendToOutput("SSL/TLS error: " + e.getMessage());
        appendToOutput("The connection is no longer secure.");
        
        // Force disconnect on SSL errors
        disconnect();
    });
}

private class ServerResponseHandler implements Runnable {
    private final SSLSocket socket;
    
    public ServerResponseHandler(SSLSocket socket) {
        this.socket = socket;
    }
    
    @Override
    public void run() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            
            Package serverResponse;
            while ((serverResponse = Package.readInput(reader)) != null) {
                String message = serverResponse.getMessage();
                appendToOutput(message);
                if (serverResponse.getToken() != null && serverResponse.getToken().length() > 1) {
                    userToken = serverResponse.getToken();
                }
            }
            
        } catch (SSLException e) {
            handleSSLException(e);
        } catch (IOException e) {
            if (shouldReconnect) {
                appendToOutput("Connection lost. Attempting to reconnect...");
                connectToServer();
            } else {
                appendToOutput("Connection closed: " + e.getMessage());
            }
        } finally {
            EventQueue.invokeLater(() -> {
                statusLabel.setText("Status: Disconnected");
                connectButton.setEnabled(true);
                disconnectButton.setEnabled(false);
                isLoggedIn = false;
                currentUsername = null;
                leaveRoom();
            });
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
        leaveRoom(); 
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
            
            Panel advancedPanel = new Panel(new FlowLayout(FlowLayout.LEFT));
            Checkbox showRoomInfoCheckbox = new Checkbox("Show Room Information Panel", true);
            showRoomInfoCheckbox.setFont(dialogFont);
            advancedPanel.add(showRoomInfoCheckbox);
            
            Panel mainContent = new Panel(new GridLayout(3, 1, 10, 10));
            mainContent.add(hostPanel);
            mainContent.add(portPanel);
            mainContent.add(advancedPanel);
            
            dialogFrame.add(mainContent);
            
            Button okButton = new Button("Connect");
            Button cancelButton = new Button("Cancel");
            okButton.setFont(dialogFont);
            cancelButton.setFont(dialogFont);
            
            Panel buttonPanel = new Panel(new FlowLayout(FlowLayout.CENTER, 10, 0));
            buttonPanel.add(okButton);
            buttonPanel.add(cancelButton);
            
            dialogFrame.add(buttonPanel, BorderLayout.SOUTH);
            
            okButton.addActionListener(e -> {
                try {
                    String inputHostname = hostnameField.getText();
                    int inputPort = Integer.parseInt(portField.getText());
                    
                    dialogFrame.dispose();
                    
                    EventQueue.invokeLater(() -> {
                        TimeClient client = new TimeClient(inputHostname, inputPort);
                        if (client.roomInfoPanel != null) {
                            if (!client.inRoom) {
                                client.roomInfoPanel.setVisible(false);
                            }
                        }
                    });
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
            
            dialogFrame.pack();
            dialogFrame.setLocationRelativeTo(null);
            dialogFrame.setVisible(true);
        }
    }
}