package utils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class utils {
    public static void safeSleep(int ms){
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); //todo
            System.out.println("Thread sleep interrupted: " + e.getMessage());
        }
    }

     public static int readLastId() {
        String filePath = "./credentials.txt";
        int lastId = 0; 

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            String lastLine = null;

            while ((line = reader.readLine()) != null) {
                lastLine = line;
            }

            if (lastLine != null && !lastLine.isEmpty()) {
                String[] parts = lastLine.split(",");
                if (parts.length > 0) {
                    lastId = Integer.parseInt(parts[0]);
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
        } catch (NumberFormatException e) {
            System.err.println("Invalid ID format in file: " + e.getMessage());
        }
        return lastId;
    }
    public static Map<String, String[]> readTokens() {
        Map<String, String[]> tokenMap = new HashMap<>();
        
        try {
            Path path = Paths.get("tokens.txt");
            
            if (!Files.exists(path)) {
                System.out.println("[INFO] tokens.txt not found - returning empty token map");
                return tokenMap;
            }
            
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (String line : lines) {
                try {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue; 
                    }
                    
                    String[] parts = line.split(",");
                    if (parts.length >= 4) {  
                        String token = parts[2].trim();  
                        String[] tokenData = {
                            parts[0].trim(),  // userId
                            parts[1].trim(),  // name
                            parts[3].trim()   // timestamp
                        };
                        tokenMap.put(token, tokenData);
                    } else {
                        System.err.println("[WARNING] Invalid token line format: " + line);
                    }
                } catch (Exception e) {
                    System.err.println("[WARNING] Error processing token line: " + line);
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to read tokens file");
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("[ERROR] Unexpected error reading tokens");
            e.printStackTrace();
        }
        
        System.out.println("[DEBUG] Loaded " + tokenMap.size() + " valid tokens");
        return tokenMap;
    }

    public static String updateOrCreateEntry(String Token, String userId, String username) {
            Map<String, String[]> tokenMap = readTokens();
            
            tokenMap.entrySet().removeIf(entry -> {
                String[] data = entry.getValue();
                return data[0].equals(userId) && data[1].equals(username);
            });
            
            long newTimestamp = System.currentTimeMillis() / 1000L + 3600;
            String timestampStr = String.valueOf(newTimestamp);
            
            String[] newTokenData = {
                userId,
                username,
                timestampStr
            };
            
            tokenMap.put(Token, newTokenData);
            
            try {
                Path path = Paths.get("tokens.txt");
                List<String> lines = new ArrayList<>();
                
                
                for (Map.Entry<String, String[]> entry : tokenMap.entrySet()) {
                    String[] data = entry.getValue();
                    String line = String.join(",", 
                        data[0],  
                        data[1],  
                        entry.getKey(),  
                        data[2]  
                    );
                    lines.add(line);
                }
                
                Files.write(path, lines, StandardCharsets.UTF_8);
                
                return "Token updated/created successfully";
            } catch (IOException e) {
                System.err.println("[ERROR] Failed to write tokens file");
                e.printStackTrace();
                return "Error updating token";
            }
        }

        public static void removeToken(String userId, String username) {
            try {
                Map<String, String[]> tokenMap = readTokens();
                
                boolean removed = tokenMap.entrySet().removeIf(entry -> {
                    String[] data = entry.getValue();
                    return data[0].equals(userId) && data[1].equals(username);
                });
                
                if (!removed) {
                    System.out.println("[INFO] No matching token found for userId: " + userId + ", username: " + username);
                    return;
                }
                
                Path path = Paths.get("tokens.txt");
                List<String> lines = new ArrayList<>();
                
                for (Map.Entry<String, String[]> entry : tokenMap.entrySet()) {
                    String[] data = entry.getValue();
                    String line = String.join(",", 
                        data[0],  // userId
                        data[1],  // username
                        entry.getKey(),  // token
                        data[2]  // timestamp
                    );
                    lines.add(line);
                }
                
                Files.write(path, lines, StandardCharsets.UTF_8);
                System.out.println("[INFO] Successfully removed token for userId: " + userId + ", username: " + username);
            } catch (IOException e) {
                System.err.println("[ERROR] Failed to update tokens file while removing token");
                e.printStackTrace();
            } catch (Exception e) {
                System.err.println("[ERROR] Unexpected error while removing token");
                e.printStackTrace();
            }
        }
}
