package Model;

import java.util.List;
import java.util.ArrayList;

public class Client {

    private String hostName;

    private String userName;
    private String hashedPassword;


    public Client(String hostName, String userName, String hashedPassword) {
        this.hostName = hostName;
        this.userName = userName;
        this.hashedPassword = hashedPassword;
    }


    public String getName() {
        return this.userName;
    }

    public String getPassword(){
        return this.hashedPassword;
    }

    public String getHostname(){
        return this.hostName;
    }
  
}
