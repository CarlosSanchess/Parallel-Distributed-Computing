package utils;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.locks.*;
import Model.Message;

public class AIIntegration {
    private static final String OLLAMA_URL = "http://localhost:11434/api/generate";
    private static final ReentrantLock requestLock = new ReentrantLock();
    private static final Condition requestCondition = requestLock.newCondition();
    
    private static final Map<String, String> responseCache = new HashMap<>();
    private static final ReentrantLock cacheLock = new ReentrantLock();
    
    private static Thread cacheCleanerThread;

    static {
        startCacheCleaner();
    }

    private static void startCacheCleaner() {
        cacheCleanerThread = Thread.startVirtualThread(() -> {
            while (true) {
                try {
                    Thread.sleep(10 * 60 * 1000); 
                    cacheLock.lock();
                    try {
                        responseCache.clear();
                    } finally {
                        cacheLock.unlock();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    public static String performQuery(String prompt, List<Message> conversationHistory) {
        String cacheKey = buildCacheKey(prompt, conversationHistory);
        
        cacheLock.lock();
        try {
            String cachedResponse = responseCache.get(cacheKey);
            if (cachedResponse != null) {
                return cachedResponse;
            }
        } finally {
            cacheLock.unlock();
        }
        
        requestLock.lock();
        try {
            String context = buildContext(prompt, conversationHistory);
            String requestBody = buildJsonRequest(context);
            
            HttpURLConnection connection = (HttpURLConnection) new URL(OLLAMA_URL).openConnection();
            try {
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);
                connection.setConnectTimeout(5000); 
                connection.setReadTimeout(30000); 
                
                try (OutputStream os = connection.getOutputStream()) {
                    os.write(requestBody.getBytes("utf-8"));
                }
                
                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), "utf-8"))) {
                        StringBuilder response = new StringBuilder();
                        String responseLine;
                        while ((responseLine = br.readLine()) != null) {
                            response.append(responseLine.trim());
                        }
                        
                        String aiResponse = parseJsonResponse(response.toString());
                        
                        cacheLock.lock();
                        try {
                            responseCache.put(cacheKey, aiResponse);
                        } finally {
                            cacheLock.unlock();
                        }
                        
                        return aiResponse;
                    }
                } else {
                    System.err.println("Ollama API request failed with code: " + responseCode);
                    return "Sorry, I'm having trouble thinking right now.";
                }
            } finally {
                connection.disconnect();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Error processing your request: " + e.getMessage();
        } finally {
            requestLock.unlock();
        }
    }

    private static String buildContext(String prompt, List<Message> history) {
        StringBuilder context = new StringBuilder();
        for (Message message : history) {
            context.append(message.getAuthor()).append(": ").append(message.getContent()).append("\n");
        }
        context.append(prompt);
        return context.toString();
    }

    private static String buildCacheKey(String prompt, List<Message> history) {
        StringBuilder key = new StringBuilder(prompt);
        for (Message message : history) {
            key.append(message.getAuthor()).append(message.getContent());
        }
        return key.toString();
    }

    private static String buildJsonRequest(String context) {
        return String.format(
            "{\"model\":\"llama3\",\"prompt\":\"%s\",\"stream\":false}",
            escapeJsonString(context)
        );
    }

    private static String escapeJsonString(String input) {
        StringBuilder sb = new StringBuilder();
        for (char c : input.toCharArray()) {
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '\"': sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String parseJsonResponse(String json) {
        int responseStart = json.indexOf("\"response\":\"") + 12;
        if (responseStart < 12) return "";
        
        int responseEnd = json.indexOf("\"", responseStart);
        if (responseEnd < 0) responseEnd = json.length() - 1;
        
        StringBuilder result = new StringBuilder();
        boolean escape = false;
        
        for (int i = responseStart; i < responseEnd; i++) {
            char c = json.charAt(i);
            
            if (escape) {
                switch (c) {
                    case '"': result.append('"'); break;
                    case 'n': result.append('\n'); break;
                    case 'r': result.append('\r'); break;
                    case 't': result.append('\t'); break;
                    case '\\': result.append('\\'); break;
                    default: result.append('\\').append(c);
                }
                escape = false;
            } else if (c == '\\') {
                escape = true;
            } else {
                result.append(c);
            }
        }
        
        return result.toString();
    }
}