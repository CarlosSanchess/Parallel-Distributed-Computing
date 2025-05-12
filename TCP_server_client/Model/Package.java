package Model;

import java.io.BufferedReader;
import java.io.IOException;
import java.security.PKCS12Attribute;

public class Package {
    private final String message;
    private final String token;

    public Package(String message, String Token) {
        this.message = message;
        this.token = Token; 
    }


    public String getMessage() {
        return message;
    }

    public String getToken() {
        return token;
    }

    public String serialize() {
        return toString();
    }

    public static Package deserialize(String serialized) {
        if (!serialized.startsWith("Package{")) {
           return new Package(serialized, null);
        }
        try {
            String content = serialized.substring("Package{".length(), serialized.length() - 1);
            String[] parts = content.split(", ");
            
            String message = null;
            String token = null;
            
            for (String part : parts) {
                if (part.startsWith("message='")) {
                    message = part.substring("message='".length(), part.length() - 1);
                } else if (part.startsWith("token='")) {
                    token = part.substring("token='".length(), part.length() - 1);
                }
            }
            
            if (token != null) {
                return new Package(message, token);
            }
            return new Package(message, token);
        } catch (Exception e) {
            // Fallback if parsing fails
            return new Package(serialized, null);
        }
    }

    public static Package readInput(BufferedReader br) {
        try {
            String line = br.readLine();
            if (line == null) {
            return null;
            }
            return deserialize(line.trim());
        } catch (IOException e) {
            System.err.println("Error reading input: " + e.getMessage());
            return null;
        }
    }
    @Override
    public String toString() {
        return "Package{" +
                "message='" + message + '\'' +
                ", token='" + token + '\'' +
                '}';
    }
}