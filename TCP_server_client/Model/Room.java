package Model;

import java.util.List;
import java.util.ArrayList;
import Model.Message;


public class Room {

    private List<Client> members;

    private List<Message> messages;

    private int maxNumberOfMembers;
    
    private boolean isAiRoom;
    
    private String name;

    private int Id;

    // Constructor to initialize the room
    public Room(int Id, String Name, int maxNumberOfMembers, boolean isAiRoom) {
        this.isAiRoom = isAiRoom;
        this.maxNumberOfMembers = maxNumberOfMembers;
        this.members = new ArrayList<>();
        this.messages = new ArrayList<>();
        this.name = Name;
        this.Id = Id;
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

    public List<Message> getMessages() {
        return messages;
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

    public boolean removeMember(Client member) {
        return members.remove(member);
    }

    public void addMessage(Message message) {
        messages.add(message);
    }

    public int getNumberOfMembers() {
        return members.size();
    }

    public boolean getIsAi(){
        return isAiRoom;
    }

    public int getId(){
        return this.Id;
    }
}
