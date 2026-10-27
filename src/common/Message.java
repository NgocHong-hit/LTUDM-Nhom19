package common;
import java.io.Serializable;

public class Message implements Serializable{
    private static final long serialVersionUID = 1L;
    private String action;
    private String status;
    private String data;

    public Message(){}

    public Message(String action, String status, String data){
        this.action = action;
        this.status = status;
        this.data = data;
    }

    public String getAction(){
        return action;
    }
    public void setAction (String action){
        this.action = action;
    }

    public String getStatus(){
        return status;
    }
    public void setStatus (String status){
        this.status = status;
    }

    public String getData(){
        return data;
    }
    public void setData (String data){
        this.data = data;
    }

}