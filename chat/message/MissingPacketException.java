package chat.message;
public class MissingPacketException extends Exception{
    public MissingPacketException(){
        super();
    }

    public MissingPacketException(String msg){
        super(msg);
    }
}