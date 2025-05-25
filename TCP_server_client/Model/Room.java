package Model;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;

public class Room {

    private final ArrayList<Client> members;
    private final ArrayList<Message> messages;

    private final ReentrantLock memberLock = new ReentrantLock();
    private final ReentrantLock messageLock = new ReentrantLock();

    private int maxNumberOfMembers;
    private boolean isAiRoom;
    private String name;
    private int Id;

    public Room(int Id, String Name, int maxNumberOfMembers, boolean isAiRoom) {
        this.isAiRoom = isAiRoom;
        this.maxNumberOfMembers = maxNumberOfMembers;
        this.members = new ArrayList<>();
        this.messages = new ArrayList<>();
        this.name = Name;
        this.Id = Id;
    }

    public List<Client> getMembers() {
        memberLock.lock();
        try {
            return new ArrayList<>(members); // Return a copy to avoid external modification
        } finally {
            memberLock.unlock();
        }
    }

    public void updateMembers(List<Client> newMembers) {
        memberLock.lock();
        try {
            this.members.clear();
            this.members.addAll(newMembers);
        } finally {
            memberLock.unlock();
        }
    }

    public List<Message> getMessages() {
        messageLock.lock();
        try {
            return new ArrayList<>(messages); // Return a copy
        } finally {
            messageLock.unlock();
        }
    }

    public int getMaxNumberOfMembers() {
        return maxNumberOfMembers;
    }

    public void setMaxNumberOfMembers(int maxNumberOfMembers) {
        this.maxNumberOfMembers = maxNumberOfMembers;
    }

    public boolean addMember(Client member) {
        if (member == null) return false;

        memberLock.lock();
        try {
            if (maxNumberOfMembers != -1 && members.size() >= maxNumberOfMembers) {
                return false;
            }
            if (members.contains(member)) {
                return false;
            }
            members.add(member);
            System.out.println("[DEBUG] Added member " + member.getName() + " to room " + this.getName());
            return true;
        } finally {
            memberLock.unlock();
        }
    }

    public boolean removeMember(Client member) {
        memberLock.lock();
        try {
            return members.remove(member);
        } finally {
            memberLock.unlock();
        }
    }

    public void addMessage(Message message) {
        messageLock.lock();
        try {
            messages.add(message);
        } finally {
            messageLock.unlock();
        }
    }

    public int getNumberOfMembers() {
        memberLock.lock();
        try {
            return members.size();
        } finally {
            memberLock.unlock();
        }
    }

    public boolean getIsAi() {
        return isAiRoom;
    }

    public int getId() {
        return this.Id;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        memberLock.lock();
        messageLock.lock();
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("Room{");
            sb.append("IsAi = ").append(isAiRoom ? 1 : 0).append(", ");
            sb.append("Id = ").append(Id).append(", ");
            sb.append("Name = \"").append(name).append("\", ");
            sb.append("MaxNumberOfMembers = ").append(maxNumberOfMembers).append(", ");
            sb.append("Members = [");
            for (Client member : members) {
                sb.append(member.getName()).append(", ");
            }
            sb.append("], ");
            sb.append("Messages = [");
            for (Message message : messages) {
                sb.append(message.toString()).append(", ");
            }
            sb.append("]}");
            return sb.toString();
        } finally {
            messageLock.unlock();
            memberLock.unlock();
        }
    }
}
