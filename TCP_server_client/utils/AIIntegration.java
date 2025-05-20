package utils;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import java.util.logging.*;
import Model.Message;

public class AIIntegration {
    private static final String OLLAMA_URL = "http://localhost:11434/api/generate";
    private static final String AI_MODEL = "llama3";
    private static final ReentrantLock requestLock = new ReentrantLock();
    
    private static final Map<String, String> responseCache = new HashMap<>();
    private static final ReentrantLock cacheLock = new ReentrantLock();
    private static final long CACHE_TTL = 10 * 60 * 1000; 
    
    private static volatile boolean isOllamaAvailable = true;
    private static final int MAX_RETRIES = 2;
    private static final int RETRY_DELAY_MS = 1000;
    
    private static final ExecutorService aiExecutor = 
        Executors.newFixedThreadPool(5); 
    
    private static final ScheduledExecutorService pollingExecutor = 
        Executors.newSingleThreadScheduledExecutor();
    private static final int POLLING_INTERVAL_SECONDS = 5;
    private static volatile boolean isPollingActive = false;
    private static ScheduledFuture<?> pollingTask = null;
    
    public interface AIResponseCallback {
        void onResponseReceived(String response, String originalMessage);
        void onError(String errorMessage, String originalMessage);
    }
    
    private static final Logger LOGGER = Logger.getLogger(AIIntegration.class.getName());
    
    static {
        try {
            Handler fileHandler = new FileHandler("ai_integration.log", true);
            fileHandler.setFormatter(new SimpleFormatter());
            LOGGER.addHandler(fileHandler);
            LOGGER.setLevel(Level.INFO);
        } catch (IOException e) {
            System.err.println("Failed to initialize logger: " + e.getMessage());
        }
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("Shutting down AI executor service");
            aiExecutor.shutdown();
            pollingExecutor.shutdown();
            try {
                if (!aiExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    aiExecutor.shutdownNow();
                }
                if (!pollingExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    pollingExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                aiExecutor.shutdownNow();
                pollingExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }));
    }

    public static void processMessageAsync(String prompt, List<Message> conversationHistory, 
                                          AIResponseCallback callback) {
        if (prompt == null || prompt.trim().isEmpty()) {
            LOGGER.warning("Empty prompt received, rejecting request");
            callback.onError("Cannot process empty prompt", prompt);
            return;
        }
        
        if (!isOllamaAvailable) {
            LOGGER.warning("Ollama marked as unavailable, rejecting request");
            startPollingIfNotActive(); 
            callback.onError("AI service is currently unavailable. Please try again later.", prompt);
            return;
        }

        aiExecutor.submit(() -> {
            try {
                String cacheKey = buildCacheKey(prompt, conversationHistory);
                String cachedResponse = null;
                
                cacheLock.lock();
                try {
                    cachedResponse = responseCache.get(cacheKey);
                    if (cachedResponse != null) {
                        LOGGER.info("Cache hit for prompt: " + prompt);
                        callback.onResponseReceived(cachedResponse, prompt);
                        return;
                    }
                } finally {
                    cacheLock.unlock();
                }
                
                LOGGER.info("Processing new async query: " + prompt);
                String context = buildContext(prompt, conversationHistory);
                String requestBody = buildJsonRequest(context);
                String response = null;
                
                requestLock.lock();
                try {
                    for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
                        if (attempt > 0) {
                            LOGGER.info("Retry attempt " + attempt + " for query");
                            try {
                                Thread.sleep(RETRY_DELAY_MS * attempt);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                LOGGER.warning("Retry delay interrupted");
                            }
                        }
                        
                        response = sendRequest(requestBody);
                        if (response != null) {
                            break;
                        }
                    }
                } finally {
                    requestLock.unlock();
                }
                
                if (response != null) {
                    cacheLock.lock();
                    try {
                        responseCache.put(cacheKey, response);
                        scheduleCacheCleanup(cacheKey);
                    } finally {
                        cacheLock.unlock();
                    }
                    
                    callback.onResponseReceived(response, prompt);
                } else {
                    LOGGER.severe("All retry attempts failed for: " + prompt);
                    startPollingIfNotActive(); 
                    callback.onError("Unable to get a response from the AI service after multiple attempts.", prompt);
                }
            } catch (Exception e) {
                LOGGER.severe("Exception in async AI processing: " + e.getMessage());
                e.printStackTrace();
                callback.onError("Error processing AI request: " + e.getMessage(), prompt);
            }
        });
    }
    
    public static String performQuery(String prompt, List<Message> conversationHistory) {
        if (prompt == null || prompt.trim().isEmpty()) {
            LOGGER.warning("Empty prompt received, rejecting request");
            return "Cannot process empty prompt";
        }
        
        if (!isOllamaAvailable) {
            LOGGER.warning("Ollama marked as unavailable, rejecting request");
            startPollingIfNotActive(); 
            return "AI service is currently unavailable. Please try again later.";
        }

        String cacheKey = buildCacheKey(prompt, conversationHistory);
        
        cacheLock.lock();
        try {
            String cachedResponse = responseCache.get(cacheKey);
            if (cachedResponse != null) {
                LOGGER.info("Cache hit for prompt: " + prompt);
                return cachedResponse;
            }
        } finally {
            cacheLock.unlock();
        }
        
        LOGGER.info("Processing new query: " + prompt);
        
        requestLock.lock();
        try {
            String context = buildContext(prompt, conversationHistory);
            String requestBody = buildJsonRequest(context);
            
            for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
                if (attempt > 0) {
                    LOGGER.info("Retry attempt " + attempt + " for query");
                    try {
                        Thread.sleep(RETRY_DELAY_MS * attempt);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        LOGGER.warning("Retry delay interrupted");
                    }
                }
                
                String response = sendRequest(requestBody);
                if (response != null) {
                    return response;
                }
            }
            
            LOGGER.severe("All retry attempts failed");
            startPollingIfNotActive();
            return "Unable to get a response from the AI service after multiple attempts.";
        } finally {
            requestLock.unlock();
        }
    }
    
    private static String sendRequest(String requestBody) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(OLLAMA_URL);
            connection = (HttpURLConnection) url.openConnection();
            
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(60000);  
            
            LOGGER.fine("Sending request to Ollama API");
            try (OutputStream os = connection.getOutputStream()) {
                os.write(requestBody.getBytes("UTF-8"));
            }
            
            int responseCode = connection.getResponseCode();
            LOGGER.info("Received response code: " + responseCode);
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), "UTF-8"))) {
                    
                    String responseText = readAllLines(br);
                    String aiResponse = parseJsonResponse(responseText);
                    
                    if (aiResponse == null || aiResponse.isEmpty()) {
                        LOGGER.warning("Received empty response from Ollama");
                        return "AI returned an empty response.";
                    }
                    
                    isOllamaAvailable = true;
                    stopPollingIfActive();
                    LOGGER.info("Successfully received AI response of length: " + aiResponse.length());
                    return aiResponse;
                }
            } else {
                handleErrorResponse(responseCode);
                LOGGER.warning("API error response: " + responseCode);
                return null;
            }
        } catch (ConnectException | SocketTimeoutException e) {
            isOllamaAvailable = false;
            startPollingIfNotActive(); 
            LOGGER.severe("Connection error: " + e.getMessage());
            return null;
        } catch (IOException e) {
            isOllamaAvailable = false;
            startPollingIfNotActive(); 
            LOGGER.severe("I/O error: " + e.getMessage());
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static synchronized void startPollingIfNotActive() {
        if (!isPollingActive) {
            LOGGER.info("Starting Ollama availability polling");
            isPollingActive = true;
            pollingTask = pollingExecutor.scheduleAtFixedRate(() -> {
                LOGGER.fine("Polling Ollama availability...");
                boolean available = pingOllama();
                if (available) {
                    LOGGER.info("Ollama is now available");
                    stopPollingIfActive();
                }
            }, 0, POLLING_INTERVAL_SECONDS, TimeUnit.SECONDS);
        }
    }
    
    private static synchronized void stopPollingIfActive() {
        if (isPollingActive && pollingTask != null) {
            LOGGER.info("Stopping Ollama availability polling");
            pollingTask.cancel(false);
            isPollingActive = false;
            pollingTask = null;
        }
    }

    private static String readAllLines(BufferedReader reader) throws IOException {
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        return response.toString();
    }

    private static void handleErrorResponse(int responseCode) {
        boolean wasAvailable = isOllamaAvailable;
        isOllamaAvailable = (responseCode != HttpURLConnection.HTTP_NOT_FOUND && 
                            responseCode != HttpURLConnection.HTTP_UNAVAILABLE);
                            
        if (!isOllamaAvailable && wasAvailable) {
            startPollingIfNotActive();
        }
                            
        if (responseCode >= 500) {
            LOGGER.severe("Server error from Ollama API: " + responseCode);
        } else if (responseCode >= 400) {
            LOGGER.warning("Client error in Ollama API request: " + responseCode);
        }
    }

    private static void scheduleCacheCleanup(String key) {
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                cacheLock.lock();
                try {
                    responseCache.remove(key);
                    LOGGER.fine("Removed expired cache entry: " + key);
                } finally {
                    cacheLock.unlock();
                }
            }
        }, CACHE_TTL);
    }

    private static String buildContext(String prompt, List<Message> history) {
        StringBuilder context = new StringBuilder();
        if (history != null && !history.isEmpty()) {
            LOGGER.fine("Building context with " + history.size() + " historical messages");
            for (Message message : history) {
                if (message != null && message.getAuthor() != null && message.getContent() != null) {
                    context.append(message.getAuthor())
                          .append(": ")
                          .append(message.getContent())
                          .append("\n");
                }
            }
        }
        context.append(prompt);
        return context.toString();
    }

    private static String buildCacheKey(String prompt, List<Message> history) {
        StringBuilder key = new StringBuilder(prompt);
        if (history != null) {
            for (Message message : history) {
                if (message != null && message.getAuthor() != null && message.getContent() != null) {
                    key.append(message.getAuthor())
                       .append(message.getContent());
                }
            }
        }
        return key.toString();
    }

    private static String buildJsonRequest(String context) {
        return String.format(
            "{\"model\":\"%s\",\"prompt\":\"%s\",\"stream\":false}",
            AI_MODEL,
            escapeJsonString(context)
        );
    }

    private static String escapeJsonString(String input) {
        if (input == null) return "";
        
        StringBuilder sb = new StringBuilder();
        for (char c : input.toCharArray()) {
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '\"': sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 32) {
                        String hex = Integer.toHexString(c);
                        sb.append("\\u");
                        for (int i = 0; i < 4 - hex.length(); i++) {
                            sb.append('0');
                        }
                        sb.append(hex);
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    private static String parseJsonResponse(String json) {
        try {
            if (json == null || json.isEmpty()) {
                LOGGER.warning("Empty JSON response received");
                return "No response from AI";
            }
            
            int responseStart = json.indexOf("\"response\":\"") + 12;
            if (responseStart < 12) {
                LOGGER.warning("Invalid JSON response format: " + json);
                return "No response from AI";
            }
            
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
                        case 'b': result.append('\b'); break;
                        case 'f': result.append('\f'); break;
                        case 'u':
                            if (i + 4 < responseEnd) {
                                String hex = json.substring(i + 1, i + 5);
                                try {
                                    int unicodeChar = Integer.parseInt(hex, 16);
                                    result.append((char) unicodeChar);
                                    i += 4;
                                } catch (NumberFormatException e) {
                                    result.append("\\u").append(hex);
                                    i += 4;
                                }
                            } else {
                                result.append('\\').append(c);
                            }
                            break;
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
        } catch (Exception e) {
            LOGGER.severe("Error parsing AI response: " + e.getMessage());
            return "Error parsing AI response: " + e.getMessage();
        }
    }

    public static boolean isOllamaAvailable() {
        return isOllamaAvailable;
    }
    
    public static void setOllamaAvailability(boolean available) {
        boolean wasAvailable = isOllamaAvailable;
        isOllamaAvailable = available;
        LOGGER.info("Ollama availability set to: " + available);
        
        if (!isOllamaAvailable && wasAvailable) {
            startPollingIfNotActive();
        } else if (isOllamaAvailable && !wasAvailable) {
            stopPollingIfActive();
        }
    }
    
    public static boolean pingOllama() {
        HttpURLConnection connection = null;
        try {
            URI uri = URI.create("http://localhost:11434/api/version");
            URL url = uri.toURL();
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            
            int responseCode = connection.getResponseCode();
            boolean available = (responseCode == HttpURLConnection.HTTP_OK);
            
            boolean wasAvailable = isOllamaAvailable;
            isOllamaAvailable = available;
            
            if (available && !wasAvailable) {
                LOGGER.info("Ollama is now available");
                stopPollingIfActive();
            } else if (!available && wasAvailable) {
                LOGGER.warning("Ollama is now unavailable");
                startPollingIfNotActive();
            }
            
            LOGGER.info("Ollama ping result: " + available + " (response code: " + responseCode + ")");
            
            return available;
        } catch (Exception e) {
            boolean wasAvailable = isOllamaAvailable;
            isOllamaAvailable = false;
            
            if (wasAvailable) {
                LOGGER.warning("Ollama is now unavailable");
                startPollingIfNotActive();
            }
            
            LOGGER.warning("Ollama ping failed: " + e.getMessage());
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    
    public static void clearCache() {
        cacheLock.lock();
        try {
            int cacheSize = responseCache.size();
            responseCache.clear();
            LOGGER.info("Cache cleared. Removed " + cacheSize + " entries.");
        } finally {
            cacheLock.unlock();
        }
    }
    
    public static int getCacheSize() {
        cacheLock.lock();
        try {
            return responseCache.size();
        } finally {
            cacheLock.unlock();
        }
    }
    
    public static boolean isPollingActive() {
        return isPollingActive;
    }
}