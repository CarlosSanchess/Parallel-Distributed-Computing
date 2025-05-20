import java.awt.*;
import java.awt.event.*;
import java.net.*;
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

    public TimeClient(String hostname, int port) {
        this.hostname = hostname;
        this.port = port;
        initializeGUI();
        connectToServer();
    }

    private void initializeGUI() {
        frame = new Frame("Time Client (AWT)");
        frame.setSize(500, 400);
        frame.setLayout(new BorderLayout());

        Panel topPanel = new Panel(new BorderLayout());
        statusLabel = new Label("Status: Disconnected", Label.LEFT);
        
        Panel buttonPanel = new Panel(new FlowLayout(FlowLayout.RIGHT));
        connectButton = new Button("Connect");
        disconnectButton = new Button("Disconnect");
        
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
        frame.add(topPanel, BorderLayout.NORTH);

        outputArea = new TextArea();
        outputArea.setEditable(false);
        frame.add(outputArea, BorderLayout.CENTER);

        Panel inputPanel = new Panel(new BorderLayout());
        inputField = new TextField();
        inputField.addActionListener(this::handleInput);
        
        Panel sendButtonPanel = new Panel(new FlowLayout(FlowLayout.RIGHT));
        Button sendButton = new Button("Send");
        Button clearButton = new Button("Clear");
        Button exitButton = new Button("Exit");
        
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
        frame.add(inputPanel, BorderLayout.SOUTH);

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
        
        appendToOutput("Welcome to Time Client!");
        appendToOutput("Connecting to server at " + hostname + ":" + port + "...");
        appendToOutput("Type /help to see available commands");
    }

    private void showHelpDialog() {
        Dialog helpDialog = new Dialog(frame, "Available Commands", true);
        helpDialog.setLayout(new BorderLayout());
        helpDialog.setSize(400, 300);
        helpDialog.setLocationRelativeTo(frame);
        
        TextArea helpText = new TextArea();
        helpText.setEditable(false);
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
        closeButton.addActionListener(e -> helpDialog.dispose());
        
        helpDialog.add(helpText, BorderLayout.CENTER);
        helpDialog.add(closeButton, BorderLayout.SOUTH);
        
        helpDialog.setVisible(true);
    }

    private void connectToServer() {
        connectButton.setEnabled(false);
        
        new Thread(() -> {
            while (shouldReconnect) {
                try {
                    statusLabel.setText("Status: Connecting...");
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
            handleTextMessage(text);
            
            outputArea.append(text + "\n");
            outputArea.setCaretPosition(outputArea.getText().length());
        });
    }
    
    private void handleTextMessage(String text) {
        if (text.contains("\u001b[H\u001b[2J") || 
            text.contains("\u001b[2J") || 
            text.contains("\u001b[3J") ||
            text.contains("\u001bc")) {
            outputArea.setText("");
            return;
        }
    
        if (text.startsWith("Login successful for user: ")) {
            isLoggedIn = true;
            currentUsername = text.substring("Login successful for user: ".length());
            outputArea.append(text + "\n");
            return;
        }
    
        if (text.equals("Logged out successfully")) {
            isLoggedIn = false;
            currentUsername = null;
            outputArea.append(text + "\n");
            return;
        }
    
        if (text.startsWith("REGISTRATION_SUCCESS")) {
            isLoggedIn = true;
            currentUsername = text.substring("REGISTRATION_SUCCESS ".length());
            shouldReconnect = true;
            outputArea.append("Registration successful! Welcome " + currentUsername + "\n");
            return;
        }
    
        if (text.startsWith("REGISTRATION_ERROR")) {
            shouldReconnect = false;
            String errorMsg = text.substring("REGISTRATION_ERROR ".length());
            outputArea.append("Registration failed: " + errorMsg + "\n");
            return;
        }
    
        if (text.startsWith("Registration successful for user: ")) {
            isLoggedIn = true;
            currentUsername = text.substring("Registration successful for user: ".length());
            shouldReconnect = true;
            outputArea.append("Registration successful! Welcome " + currentUsername + "\n");
            return;
        }
    
        if (text.equals("Username already exists") || text.equals("Username already taken")) {
            shouldReconnect = false;
            outputArea.append("Registration failed: Username already exists\n");
            return;
        }
    
        if (text.startsWith("Error during registration")) {
            shouldReconnect = false;
            outputArea.append("Registration failed: " + text.substring("Error during registration".length()) + "\n");
            return;
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

        outputArea.setText("");
        appendToOutput("Disconnecting from server...");
        if (writer != null) {
            System.out.println("Mnadou disconnect");
            Package p = new Package("/disconnect", userToken);
            writer.println(p.serialize());
        }
        closeConnection();
        statusLabel.setText("Status: Disconnected");
        connectButton.setEnabled(true);
        disconnectButton.setEnabled(false);
        isLoggedIn = false;
        currentUsername = null;
        return;
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
            Frame dialogFrame = new Frame("Connection Settings");
            dialogFrame.setSize(300, 150);
            dialogFrame.setLayout(new GridLayout(3, 2, 10, 10));
            
            dialogFrame.add(new Label("Hostname:"));
            TextField hostnameField = new TextField(hostname);
            dialogFrame.add(hostnameField);
            
            dialogFrame.add(new Label("Port:"));
            TextField portField = new TextField(String.valueOf(port));
            dialogFrame.add(portField);
            
            Button okButton = new Button("Connect");
            Button cancelButton = new Button("Cancel");
            
            Panel buttonPanel = new Panel(new FlowLayout());
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
                    errorDialog.setLayout(new BorderLayout());
                    errorDialog.add(new Label("Invalid port number. Please enter a valid integer."), BorderLayout.CENTER);
                    Button closeButton = new Button("OK");
                    closeButton.addActionListener(evt -> errorDialog.dispose());
                    errorDialog.add(closeButton, BorderLayout.SOUTH);
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