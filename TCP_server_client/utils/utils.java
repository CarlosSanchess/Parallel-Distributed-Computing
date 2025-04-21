package utils;

public class utils {
    public static void safeSleep(int ms){
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); //todo
            System.out.println("Thread sleep interrupted: " + e.getMessage());
        }
    }
}
