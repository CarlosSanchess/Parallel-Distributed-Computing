package Model;

import java.util.List;
import java.net.InetAddress;
import java.util.ArrayList;

public class Client {

    public enum ClientState {
        NOT_IN_ROOM,
        IN_ROOM,
        WAITING,
        EXITING
    }

    private InetAddress inetaddr;
    private boolean isAuth;
    private String userName;
    private String hashedPassword;
    private int clientId;
    private ClientState state;
    private int roomId;
    private boolean isAi;
    

    public Client(int clientId, InetAddress inetaddr, String userName, String hashedPassword, boolean isAi) {
        this.clientId = clientId;
        this.inetaddr = inetaddr;
        this.userName = userName;
        this.hashedPassword = hashedPassword;
        this.isAuth = false;
        this.state = ClientState.NOT_IN_ROOM; //Not In Room By default, when creating Client
        this.roomId = -1; // Not in Room
        this.isAi = isAi;
    }


    public String getName() {
        return this.userName;
    }

    public String getPassword(){
        return this.hashedPassword;
    }

    public InetAddress getInetaddr(){
        return this.inetaddr;
    }

    public void setAuthTrue(){
        this.isAuth = true;
    }
    
    public int getId(){
       return this.clientId;
    }
    public ClientState getState(){
        return this.state;
    }

    public void setState(ClientState cs){
        this.state = cs;
    }
    public int setRoom(int RoomId){
        if(this.state.equals(ClientState.NOT_IN_ROOM)){
            this.roomId = RoomId;
            this.state = ClientState.IN_ROOM;
        }
        return -1;
    }

    public int getRoomId(){
        return this.roomId;
    }
}
