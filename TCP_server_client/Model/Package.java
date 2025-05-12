package Model;

import java.util.UUID;

public class Package {
    private final String message;
    private final String token;

    public Package(String message) {
        this.message = message;
        this.token = UUID.randomUUID().toString(); 
    }

    public Package(String message, String token) {
        this.message = message;
        this.token = token;
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
            String[] parts = serialized.split("\\|", 2);
            if (parts.length == 2) {
                return new Package(parts[1], parts[0]);
            }
            return new Package(serialized);
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
            return new Package(message);
        } catch (Exception e) {
            // Fallback if parsing fails
            return new Package(serialized);
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