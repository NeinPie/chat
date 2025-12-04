package management.message;

import java.nio.charset.StandardCharsets;

public class MngCodec {

    public static byte[] encode(MngMessage msg) {
        String s = "type=" + msg.getType().name() + ";data=" + msg.getData();
        return s.getBytes(StandardCharsets.UTF_8);
    }

    public static MngMessage decode(byte[] data, int length) {
        return decode(new String(data, 0, length, StandardCharsets.UTF_8));
    }

    public static MngMessage decode(String s) {

        String[] parts = s.split(";");
        MngType type = null;
        String data = "";

        for (String p : parts) {
            String[] kv = p.split("=", 2);

            if (kv.length != 2) continue;

            switch (kv[0]) {
                case "type" -> type = MngType.valueOf(kv[1].trim());
                case "data" -> data = kv[1].trim();
            }
        }

        if (type == null)
            throw new IllegalArgumentException("Fehlender Typ in Nachricht: " + s);

        return new MngSimpleMessage(type, data);
    }
}
