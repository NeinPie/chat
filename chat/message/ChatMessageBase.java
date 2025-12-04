package chat.message;

public class ChatMessageBase implements ChatMessage {
    private final int seq;
    private final long time;
    private final int PACKET_NUMBER;
    private ChatMessageType msgType;

    public ChatMessageBase(ChatMessageType type, int seq, long time, int packetNumber) {
        this.seq = seq;
        this.time = time;
        PACKET_NUMBER = packetNumber;
        this.msgType = type;
    }

    @Override
    public int getSeq() {
        return seq;
    }

    @Override
    public long getTime() {
        return time;
    }

    @Override
    public int getPacketNr() {
        return PACKET_NUMBER;
    }

    @Override
    public String toString() {
        return "Pong[seq=" + seq + ", ts=" + time + "]";
    }

    public ChatMessageType getType() {
        return msgType;
    }
}
