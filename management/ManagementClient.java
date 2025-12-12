package management;

import management.message.*;

import java.io.InputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Scanner;

public class ManagementClient {

    private final String host;
    private final int port;

    private String name;// Benutzername

    private String currentChatPartner = null; // aktueller Chatpartner

    private String currentChatRequestFrom = null; //Chat-Anfrage von diesem Benutzer

    private final Scanner scanner = new Scanner(System.in);

    private Socket socket;
    private volatile boolean running = true;

    public ManagementClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void start() throws Exception {

        socket = new Socket(host, port); // Verbindungsaufbau

        System.out.println("Willkommen zum Chat-Client!");
        System.out.println("Die wichtigsten Befehle:");
        System.out.println("  /reg user pass     Benutzer registrieren");
        System.out.println("  /login user pass   Anmelden");
        System.out.println("  /chat user         Chat mit Benutzer starten");
        System.out.println("  /ok                Chat-Anfrage annehmen");
        System.out.println("  /nok               Chat-Anfrage ablehnen");
        System.out.println("  /msg text          Nachricht senden");
        System.out.println("  /close             Aktuellen Chat beenden");
        System.out.println("  /logout            Abmelden");
        System.out.println("  /exit              Programm beenden");
        System.out.println();

        Thread listener = new Thread(this::listen, "listener-thread");
        listener.start();

        while (running) {
            System.out.print("> ");

            String cmd = scanner.nextLine();
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
                    send(MngType.CHATAUFFORDERUNG, name + ":" + p[1]);


                } else if (cmd.equals("/ok")) {
                    if (currentChatRequestFrom != null) {
                        send(MngType.CHATANFRAGE_OK, currentChatRequestFrom + ":" + name);
                        currentChatPartner = currentChatRequestFrom;   
                        currentChatRequestFrom = null;
                    } else {
                        System.out.println("Keine offene Chatanfrage.");
                    }

                } else if (cmd.equals("/nok")) {
                    if (currentChatRequestFrom != null) {
                        send(MngType.CHATANFRAGE_NOK, currentChatRequestFrom + ":" + name);
                        currentChatRequestFrom = null;
                    } else {
                        System.out.println("Keine offene Chatanfrage.");
                    }

                } else if (cmd.startsWith("/msg ")) {
                    if (currentChatPartner == null) {
                        System.out.println("Kein aktiver Chat.");
                        continue;
                    }
                    String message = cmd.substring(5);
                    send(MngType.SEND_MESSAGE, name + ":" + currentChatPartner + ":" + message);


                } else if (cmd.equals("/close")) {
                    if (currentChatPartner != null) {
                        send(MngType.CLOSE_CHAT, name + ":" + currentChatPartner);
                        currentChatPartner = null;
                    } else {
                        System.out.println("Kein aktiver Chat.");
                    }

                } else if (cmd.equals("/logout")) {
                    send(MngType.ABMELDUNG, name);
                }

            } catch (IOException e) {
                System.out.println("Verbindung verloren.");
                shutdown();
            }
        }
    }

    // Nachricht an Server senden
    private void send(MngType type, String data) throws IOException {
        if (socket != null && !socket.isClosed()) {
            socket.getOutputStream().write(
                    MngCodec.encode(new MngSimpleMessage(type, data))
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

                if (msg.getType() == MngType.ANMELDUNG_OK) {
                    String[] msgdata = msg.getData().split(":");
                    name = msgdata[1];
                }

                if (msg.getType() == MngType.CHATANFRAGE) {
                    currentChatRequestFrom = msg.getData();
                }

                if (msg.getType() == MngType.CHATANFRAGE_OK) {
                    String[] p = msg.getData().split(":");
                    currentChatPartner = p[1]; 
                    currentChatRequestFrom = null;
                }

                if (msg.getType() == MngType.CLOSE_CHAT) {
                    currentChatPartner = null;
                    currentChatRequestFrom = null;
                }
                if (msg.getType() == MngType.ABMELDUNG_OK) {
                    name = null;
                    currentChatPartner = null;
                    currentChatRequestFrom = null;
                }

                System.out.println("\nSERVER | " + msg.getType() +
                        " | " + msg.getData());
                System.out.print("> ");
            }

        } catch (Exception ignored) {

        } finally {
            shutdown();
        }
    }

    // Client beenden
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
