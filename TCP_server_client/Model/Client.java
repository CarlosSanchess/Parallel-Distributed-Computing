package Model;

import java.util.List;
import java.net.InetAddress;
import java.util.ArrayList;

public class Client {

    private InetAddress inetaddr;
    private boolean isAuth;
    private String userName;
    private String hashedPassword;
    private int clientId;


    public Client(int clientId, InetAddress inetaddr, String userName, String hashedPassword) {
        this.clientId = clientId;
        this.inetaddr = inetaddr;
        this.userName = userName;
        this.hashedPassword = hashedPassword;
        this.isAuth = false;
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
  
}
