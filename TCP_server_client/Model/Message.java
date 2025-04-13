package Model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Message {

    private final LocalDateTime time;
    private final String author;
    private final String content;

    public Message(String author, String content) {
        this.time = LocalDateTime.now(); 
        this.author = author;
        this.content = content;
    }

    public LocalDateTime getTime() {
        return time;
    }

    public String getFormattedTime() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
        return time.format(formatter);
    }

    public String getAuthor() {
        return author;
    }

    public String getContent() {
        return content;
    }

    //Formatar para output
    @Override
    public String toString() {
        return "[" + getFormattedTime() + "] " + author + ": " + content;
    }
}
