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

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("  Room{\n");
        sb.append("  IsAi = ").append(isAiRoom ? 1 : 0).append(",\n");
        sb.append("  Id = ").append(Id).append(",\n");
        sb.append("  Name = \"").append(name).append("\",\n");
        sb.append("  MaxNumberOfMembers = ").append(maxNumberOfMembers).append(",\n");
        sb.append("  Members = [\n");
        for (Client member : members) {
            sb.append("    ").append(member.getName().toString()).append(",\n");
        }
        sb.append("  ],\n");
        sb.append("  Messages = [\n");
        for (Message message : messages) {
            sb.append("    ").append(message.toString()).append(",\n");
        }
        sb.append("  ]\n");
        sb.append("}");
        return sb.toString();
    }

}
