package management.message;

public class MngSimpleMessage implements MngMessage {

    private final MngType type;
    private final String data;

    public MngSimpleMessage(MngType type, String data) {
        this.type = type;
        this.data = data == null ? "" : data;
    }

    @Override
    public MngType getType() {
        return type;
    }

    @Override
    public String getData() {
        return data;
    }

    @Override
    public String toString() {
        return "MgmtMessage{" + "type=" + type + ", data='" + data + '\'' + '}';
    }
}
