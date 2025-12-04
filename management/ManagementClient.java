package management;

import management.message.*;

import java.io.InputStream;
import java.io.IOException;
import java.net.Socket;

import de.hsrm.mi.prog.util.StaticScanner;


public class ManagementClient {

    private final String host;
    private final int port;

    private Socket socket;
    private volatile boolean running = true;

    public ManagementClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void start() throws Exception {

        socket = new Socket(host, port); //Verbindungsaufbau

        Thread listener = new Thread(this::listen, "listener-thread");
        listener.start();

        while (running) {
            System.out.print("> ");

            String cmd = StaticScanner.nextString();   
            if (cmd == null) continue;

            try {

                if (cmd.equals("/exit")) {
                    shutdown();    
                    break;
                }

                if (cmd.startsWith("/reg ")) {
                    String[] p = cmd.split(" ");
                    send(MngType.REGISTRIEREN, p[1] + ":" + p[2]);

                } else if (cmd.startsWith("/login ")) {
                    String[] p = cmd.split(" ");
                    send(MngType.ANMELDUNG, p[1] + ":" + p[2]);

                } else if (cmd.startsWith("/chat ")) {
                    String[] p = cmd.split(" ");
                    send(MngType.CHATAUFFORDERUNG, p[1] + ":" + p[2]);

                } else if (cmd.startsWith("/ok ")) {
                    String[] p = cmd.split(" ");
                    send(MngType.CHATANFRAGE_OK, p[1] + ":" + p[2]);

                } else if (cmd.startsWith("/nok ")) {
                    String[] p = cmd.split(" ");
                    send(MngType.CHATANFRAGE_NOK, p[1] + ":" + p[2]);

                } else if (cmd.startsWith("/msg ")) {
                    // /msg me bob Hello World
                    String[] p = cmd.split(" ", 4);
                    send(MngType.SEND_MESSAGE, p[1] + ":" + p[2] + ":" + p[3]);

                } else if (cmd.startsWith("/close ")) {
                    send(MngType.CLOSE_CHAT, cmd.substring(7));

                } else if (cmd.startsWith("/delip ")) {
                    send(MngType.DELETE_IP, cmd.substring(7));
                }
                //TODO: Logout
                //TODO: Client kennt seinen Namen => Nicht übergeben

            } catch (IOException e) {
                System.out.println("Verbindung verloren.");
                shutdown();
            }
        }
    }

    private void send(MngType type, String payload) throws IOException {
        if (socket != null && !socket.isClosed()) {
            socket.getOutputStream().write(
                    MngCodec.encode(new MngSimpleMessage(type, payload))
            );
        }
    }

    private void listen() {
        try {
            InputStream in = socket.getInputStream();
            byte[] buf = new byte[2048];

            while (running) {
                int len = in.read(buf);
                if (len == -1) break;

                MngMessage msg = MngCodec.decode(buf, len);

                System.out.println("\nSERVER → " + msg.getType() +
                        " | " + msg.getData());
                System.out.print("> ");
            }

        } catch (Exception ignored) {

        } finally {
            shutdown();
        }
    }

    private synchronized void shutdown() {
        if (!running) return;

        running = false;

        try { if (socket != null && !socket.isClosed()) socket.close(); }
        catch (IOException ignored) {}

        System.out.println("Client beendet.");
    }

    public static void main(String[] args) throws Exception {
        new ManagementClient("localhost", 9090).start();
    }
}
