package Model;

import java.util.List;
import java.util.ArrayList;


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
    if (member == null) {
        return false;
    }
    
    // Check if room is full
    if (maxNumberOfMembers != -1 && members.size() >= maxNumberOfMembers) {
        return false;
    }

    // Check if member is already in room
    if (members.contains(member)) {
        return false;
    }

    // Add member
    members.add(member);
    System.out.println("[DEBUG] Added member " + member.getName() + " to room " + this.getName());
    return true;
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

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Room{");
        sb.append("IsAi = ").append(isAiRoom ? 1 : 0).append(", ");
        sb.append("Id = ").append(Id).append(", ");
        sb.append("Name = \"").append(name).append("\", ");
        sb.append("MaxNumberOfMembers = ").append(maxNumberOfMembers).append(", ");
        sb.append("Members = [");
        for (Client member : members) {
            sb.append(member.getName().toString()).append(", ");
        }
        sb.append("], ");
        sb.append("Messages = [");
        for (Message message : messages) {
            sb.append(message.toString()).append(", ");
        }
        sb.append("]}");
        return sb.toString();
    }
}
