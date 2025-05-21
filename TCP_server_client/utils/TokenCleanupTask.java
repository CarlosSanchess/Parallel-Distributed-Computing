package utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class TokenCleanupTask implements Runnable {
    private volatile boolean running = true;
    private final long checkIntervalMs = 5 * 60 * 1000; 
    private final ReentrantLock serverLock;
    
    public TokenCleanupTask(ReentrantLock serverLock) {
        this.serverLock = serverLock;
    }
    
    @Override
    public void run() {
        System.out.println("[INFO] Token cleanup thread started");
        
        while (running) {
            try {
                checkAndRemoveExpiredTokens();
                
                utils.safeSleep((int) checkIntervalMs);
            } catch (Exception e) {
                System.err.println("[ERROR] Exception in token cleanup thread: " + e.getMessage());
                e.printStackTrace();
                
                utils.safeSleep(30000); 
            }
        }
        
        System.out.println("[INFO] Token cleanup thread stopped");
    }
    
    public void stop() {
        running = false;
    }
    
    private void checkAndRemoveExpiredTokens() {
        System.out.println("[INFO] Checking for expired tokens...");
        
        List<String[]> expiredTokens = new ArrayList<>();
        
        Map<String, String[]> tokenMap = utils.readTokens();
        long currentTime = System.currentTimeMillis() / 1000L; 
        
        for (Map.Entry<String, String[]> entry : tokenMap.entrySet()) {
            String token = entry.getKey();
            String[] tokenData = entry.getValue();
            
            if (tokenData.length >= 3) {
                String userId = tokenData[0];
                String username = tokenData[1];
                long expiryTimestamp;
                
                try {
                    expiryTimestamp = Long.parseLong(tokenData[2]);
                    
                    if (currentTime > expiryTimestamp) {
                        expiredTokens.add(new String[]{userId, username});
                    }
                } catch (NumberFormatException e) {
                    System.err.println("[WARNING] Invalid timestamp format for token: " + token);
                }
            }
        }
        
        if (!expiredTokens.isEmpty()) {
            serverLock.lock();
            try {
                for (String[] tokenInfo : expiredTokens) {
                    String userId = tokenInfo[0];
                    String username = tokenInfo[1];
                    System.out.println("[INFO] Removing expired token for user: " + username + " (ID: " + userId + ")");
                    utils.removeToken(userId, username);
                }
                System.out.println("[INFO] Token cleanup completed. Removed " + expiredTokens.size() + " expired tokens.");
            } finally {
                serverLock.unlock();
            }
        } else {
            System.out.println("[INFO] Token cleanup completed. No expired tokens found.");
        }
    }
}