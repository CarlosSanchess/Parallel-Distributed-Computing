import java.awt.*;
import java.awt.event.*;
import java.net.*;
import java.io.*;

public class TimeClient {
    private Frame frame;
    private TextArea outputArea;
    private TextField inputField;
    private Socket socket;
    private PrintWriter writer;
    private String sessionToken;
    private boolean shouldReconnect = true;
    private String hostname;
    private int port;

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

        outputArea = new TextArea();
        outputArea.setEditable(false);
        frame.add(outputArea, BorderLayout.CENTER);

        Panel inputPanel = new Panel(new BorderLayout());
        inputField = new TextField();
        inputField.addActionListener(this::handleInput);
        Button sendButton = new Button("Send");
        sendButton.addActionListener(this::handleInput);

        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        frame.add(inputPanel, BorderLayout.SOUTH);

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shouldReconnect = false;
                closeConnection();
            }
        });

        frame.setVisible(true);
        
        // Add a message to let the user know the client is starting
        appendToOutput("Connecting to server at " + hostname + ":" + port + "...");
    }

    private void connectToServer() {
        while (shouldReconnect) {
            try {
                socket = new Socket(hostname, port);
                writer = new PrintWriter(socket.getOutputStream(), true);
                
                // Send an empty line to trigger server response if needed
                if (sessionToken != null) {
                    writer.println("/reconnect " + sessionToken);
                } else {
                    // Send a dummy input to trigger the server's initial response
                    writer.println("");
                }
                
                new Thread(new ServerResponseHandler(socket)).start();
                appendToOutput("Connected to server at " + hostname + ":" + port);
                break;
            } catch (IOException ex) {
                appendToOutput("Connection failed: " + ex.getMessage());
                appendToOutput("Retrying in 5 seconds...");
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void handleInput(ActionEvent e) {
        String input = inputField.getText();
        inputField.setText("");
        
        if (writer != null) {
            writer.println(input);
            
            if ("/exit".equalsIgnoreCase(input)) {
                shouldReconnect = false;
                appendToOutput("Disconnecting from server...");
                closeConnection();
            }
        }
    }

    private void appendToOutput(String text) {
        EventQueue.invokeLater(() -> {
            if (text.contains("\u001b[H\u001b[2J") || 
                text.contains("\u001b[2J") || 
                text.contains("\u001b[3J") ||
                text.contains("\u001bc")) {
                outputArea.setText("");
                return;
            }            
            
            if (text.trim().isEmpty()) {
                return;
            }
            
            if (text.startsWith("/token ")) {
                sessionToken = text.substring(7);
                return;
            }
            
            outputArea.append(text + "\n");
            outputArea.setCaretPosition(outputArea.getText().length());  
        });
    }

    private void closeConnection() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            frame.dispose();
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
                
                String serverResponse;
                while ((serverResponse = reader.readLine()) != null) {
                    appendToOutput(serverResponse);
                }
                
                appendToOutput("Server has disconnected");
                if (shouldReconnect) {
                    appendToOutput("Attempting to reconnect...");
                    connectToServer();
                } else {
                    EventQueue.invokeLater(() -> {
                        frame.dispose();
                    });
                }
                
            } catch (IOException ex) {
                if (shouldReconnect) {
                    appendToOutput("Connection lost. Attempting to reconnect...");
                    connectToServer();
                } else {
                    appendToOutput("Error reading server response: " + ex.getMessage());
                }
            }
        }
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java TimeClient <hostname> <port>");
            return;
        }

        String hostname = args[0];
        int port = Integer.parseInt(args[1]);
        
        EventQueue.invokeLater(() -> new TimeClient(hostname, port));
    }
}