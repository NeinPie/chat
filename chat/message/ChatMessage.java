package chat.message;

public interface ChatMessage {
    ChatMessageType getType();
    int getSeq();
    long getTime();
    int getPacketNr();
}