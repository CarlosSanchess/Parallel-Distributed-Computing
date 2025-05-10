import java.awt.*;
import java.awt.event.*;
import java.net.*;
import java.io.*;
import javax.swing.*;

public class TimeClient {
    private JFrame frame;
    private JTextArea outputArea;
    private JTextField inputField;
    private Socket socket;
    private PrintWriter writer;

    public TimeClient(String hostname, int port) {
        initializeGUI();
        connectToServer(hostname, port);
    }

    private void initializeGUI() {
        frame = new JFrame("Time Client");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(500, 400);
        frame.setLayout(new BorderLayout());

        outputArea = new JTextArea();
        outputArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(outputArea);
        frame.add(scrollPane, BorderLayout.CENTER);

        JPanel inputPanel = new JPanel(new BorderLayout());
        inputField = new JTextField();
        inputField.addActionListener(this::handleInput);
        JButton sendButton = new JButton("Send");
        sendButton.addActionListener(this::handleInput);

        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        frame.add(inputPanel, BorderLayout.SOUTH);

        frame.setVisible(true);
    }

    private void connectToServer(String hostname, int port) {
        try {
            socket = new Socket(hostname, port);
            writer = new PrintWriter(socket.getOutputStream(), true);
            
            new Thread(new ServerResponseHandler(socket)).start();
            
            // Removed the "Connected to server" message from GUI
            System.out.println("Connected to server at " + hostname + ":" + port); // Console only
        } catch (UnknownHostException ex) {
            appendToOutput("Server not found: " + ex.getMessage());
        } catch (IOException ex) {
            appendToOutput("I/O error: " + ex.getMessage());
        }
    }

    private void handleInput(ActionEvent e) {
        String input = inputField.getText();
        inputField.setText("");
        
        if (writer != null) {
            writer.println(input);
            
            if ("exit".equalsIgnoreCase(input)) {
                appendToOutput("Disconnecting from server...");
                closeConnection();
            }
        }
    }

    private void appendToOutput(String text) {
        SwingUtilities.invokeLater(() -> {
            // Filter out ANSI escape sequences
            String cleanedText = text.replaceAll("\u001B\\[[;\\d]*[ -/]*[@-~]", "");
            
            if (cleanedText.trim().isEmpty()) {
                return;
            }
            
            outputArea.append(cleanedText + "\n");
            outputArea.setCaretPosition(outputArea.getDocument().getLength());
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
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(frame, 
                        "Server has disconnected", 
                        "Connection closed", 
                        JOptionPane.INFORMATION_MESSAGE);
                    frame.dispose();
                });
                
            } catch (IOException ex) {
                appendToOutput("Error reading server response: " + ex.getMessage());
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
        
        SwingUtilities.invokeLater(() -> new TimeClient(hostname, port));
    }
}