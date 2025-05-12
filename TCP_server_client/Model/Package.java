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
        return token + "|" + message;
    }

    public static Package deserialize(String serialized) {
        String[] parts = serialized.split("\\|", 2);
        if (parts.length == 2) {
            return new Package(parts[1], parts[0]);
        }
        return new Package(serialized); 
    }

    @Override
    public String toString() {
        return "Package{" +
                "message='" + message + '\'' +
                ", token='" + token + '\'' +
                '}';
    }
}