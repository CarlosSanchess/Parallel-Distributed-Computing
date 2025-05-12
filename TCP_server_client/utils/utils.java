package utils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

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
}
