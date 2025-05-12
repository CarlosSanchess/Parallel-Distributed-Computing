package Model;

import java.net.InetAddress;
import java.net.Socket;

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
    private transient Socket socket;

    public Client(int clientId, InetAddress inetaddr, String userName, String hashedPassword, boolean isAi) {
        this.clientId = clientId;
        this.inetaddr = inetaddr;
        this.userName = userName;
        this.hashedPassword = hashedPassword;
        this.isAuth = false;
        this.state = ClientState.NOT_IN_ROOM;
        this.roomId = -1;
        this.isAi = isAi;
    }

    public String toCredentialString() {
        return String.join(",",
            String.valueOf(clientId),
            inetaddr.getHostAddress(),
            userName,
            hashedPassword
        );
    }

    // Getters and Setters
    public String getName() { return userName; }
    public String getPassword() { return hashedPassword; }
    public InetAddress getInetaddr() { return inetaddr; }
    public int getId() { return clientId; }
    public ClientState getState() { return state; }
    public int getRoomId() { return roomId; }
    public boolean isAi() { return isAi; }
    public boolean isAuth() { return isAuth; }
    public Socket getSocket() { return socket; }

    public void setAuthTrue() { isAuth = true; }
    public void setState(ClientState cs) { state = cs; }
    public void setSocket(Socket socket) { this.socket = socket; }

    public int setRoom(int RoomId) {
        if (state.equals(ClientState.NOT_IN_ROOM)) {
            roomId = RoomId;
            state = ClientState.IN_ROOM;
            return RoomId;
        }
        return -1;
    }

    public void leaveRoom() {
        roomId = -1;
        state = ClientState.NOT_IN_ROOM;
    }
}