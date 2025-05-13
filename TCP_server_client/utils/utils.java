package utils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
                    if (parts.length >= 3) {
                        String token = parts[1].trim();
                        String[] tokenData = {
                            parts[0].trim(),  // userId
                            parts[2].trim()  // timestamp
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
        return tokenMap;
    }
}
