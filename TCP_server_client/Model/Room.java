package Model;

import java.util.List;
import java.util.ArrayList;


public class Room {

    private List<Client> members;

    private List<String> messages;

    private int maxNumberOfMembers;
    
    private boolean isAiRoom;
    
    private String name;

    // Constructor to initialize the room
    public Room(String Name, int maxNumberOfMembers, boolean isAiRoom) {
        this.isAiRoom = isAiRoom;
        this.maxNumberOfMembers = maxNumberOfMembers;
        this.members = new ArrayList<>();
        this.messages = new ArrayList<>();
        this.name = Name;
    }


    public List<Client> getMembers() {
        return members;
    }

    public String getName(){
        return name;
    }
    
    public void setMembers(List<Client> members) {
        this.members = members;
    }

    public List<String> getMessages() {
        return messages;
    }

    public void setMessages(List<String> messages) {
        this.messages = messages;
    }

    public int getMaxNumberOfMembers() {
        return maxNumberOfMembers;
    }

    public void setMaxNumberOfMembers(int maxNumberOfMembers) {
        this.maxNumberOfMembers = maxNumberOfMembers;
    }

    public boolean addMember(Client member) {
        if (members.size() < maxNumberOfMembers) {
            members.add(member);
            return true;
        } else {
            return false; 
        }
    }

    public boolean removeMember(String member) {
        return members.remove(member);
    }

    public void addMessage(String message) {
        messages.add(message);
    }

    public int getNumberOfMembers() {
        return members.size();
    }

    public boolean getIsAi(){
        return isAiRoom;
    }
}
