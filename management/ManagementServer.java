package management;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import management.message.*;

public class ManagementServer {

    private final int port;

    // registrierte Benutzer (login:pass)
    private final ConcurrentHashMap<String, String> registeredUsers = new ConcurrentHashMap<>(); 

    // aktive Clients (login:socket)
    private final ConcurrentHashMap<String, Socket> activeClients = new ConcurrentHashMap<>();

    // aktive Chats (user - partner)
    private final ConcurrentHashMap<String, String> chatPartner = new ConcurrentHashMap<>();

    public ManagementServer(int port) {
        this.port = port;
    }

    public void start() throws Exception {
        readTXT();
        System.out.println("Management Server gestartet auf Port " + port);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket client = serverSocket.accept();
                Thread t = new Thread(() -> handleClient(client), "client-handler");
                t.start();
            }
        }
    }

    private void readTXT() {
        try (BufferedReader reader = new BufferedReader(new FileReader("register.txt"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":", 2);
                registeredUsers.put(parts[0], parts.length > 1 ? parts[1] : "");
            }
        } catch (IOException ignored) {}
    }

    private void writeTXT(String user, String pass) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("register.txt", true))) {
            writer.write(user + ":" + pass);
            writer.newLine();
        } catch (IOException ignored) {}
    }

    private void handleClient(Socket clientSocket) {
        String anmClients = null;// Benutzername des angemeldeten Clients

        try (Socket s = clientSocket;
             InputStream in = s.getInputStream();
             OutputStream out = s.getOutputStream()
        ) {
            byte[] buffer = new byte[4096];

            while (true) {
                int read = in.read(buffer);
                if (read == -1) break;

                MngMessage msg = MngCodec.decode(buffer, read);

                switch (msg.getType()) {

                     case REGISTRIEREN:
                         handleRegister(msg, out);
                        break;

                    case ANMELDUNG:
                         anmClients = handleLogin(msg, out, s);
                         break;

                    case CHATAUFFORDERUNG:
                        handleChatRequest(msg);
                        break;

                    case CHATANFRAGE:
                        handleIncomingChatRequest(msg);
                        break;

                    case CHATANFRAGE_OK:
                        handleChatAccept(msg);
                        break;

                    case CHATANFRAGE_NOK:
                        handleChatDecline(msg);
                        break;

                    case SEND_MESSAGE:
                       handleSendMessage(msg);
                       break;

                    case CLOSE_CHAT:
                       handleCloseChat(msg);
                       break;

                    case ABMELDUNG:
                       handleLogout(msg, out, clientSocket);
                    break;

                    default:
                       System.out.println("Unbekannter Nachrichtentyp: " + msg.getType());
                        break;
                }
            }

        } catch (Exception ignored) {}

        if (anmClients != null) {
            activeClients.remove(anmClients);// Aufräumen bei Verbindungsende
            chatPartner.remove(anmClients);
        }
    }
    
    // Registrierung
    private void handleRegister(MngMessage msg, OutputStream out) throws Exception {
        String[] p = msg.getData().split(":", 2);
        String user = p[0];
        String pass = p.length > 1 ? p[1] : "";

        if (registeredUsers.containsKey(user)) {
            out.write(MngCodec.encode(new MngSimpleMessage(MngType.REGISTRIEREN_NOK, "Benutzer existiert")));
            out.flush();
            System.out.println("SERVER | REGISTRIEREN_NOK | Benutzer existiert: " + user);
            return;
        }

        registeredUsers.put(user, pass);
        writeTXT(user, pass);

        out.write(MngCodec.encode(new MngSimpleMessage(MngType.REGISTRIEREN_OK, "Registriert")));
        out.flush();
        System.out.println("Registered user: " + user);
    }
    
    // Login
    private String handleLogin(MngMessage msg, OutputStream out, Socket s) throws Exception {
        String[] p = msg.getData().split(":", 2);
        String user = p[0];
        String pass = p[1];

        if (!registeredUsers.containsKey(user) || !registeredUsers.get(user).equals(pass)) {
            out.write(MngCodec.encode(new MngSimpleMessage(MngType.ANMELDUNG_NOK, "Login fehlgeschlagen")));
            out.flush();
            return null;
        }

        activeClients.put(user, s);

        out.write(MngCodec.encode(new MngSimpleMessage(MngType.ANMELDUNG_OK, "Login ok:" + user)));
        out.flush();
        System.out.println("User logged in: " + user);

        return user;
    }
    
    // Logout
    private void handleLogout(MngMessage msg, OutputStream out, Socket clientSocket) throws Exception {
        String user = msg.getData();

        if (activeClients.containsKey(user)) {
            activeClients.remove(user);

            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.getOutputStream().write(MngCodec.encode(
                        new MngSimpleMessage(MngType.ABMELDUNG_OK, "Logout ok:")
                ));
                clientSocket.getOutputStream().flush();
            }

            System.out.println("User logged out: " + user);
        } else {
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.getOutputStream().write(MngCodec.encode(
                        new MngSimpleMessage(MngType.ABMELDUNG_NOK, "Logout fehlgeschlagen")
                ));
                clientSocket.getOutputStream().flush();
            }

            System.out.println("Logout failed: " + user);
        }
    }


    // Chat-Anfrage senden
    private void handleChatRequest(MngMessage msg) throws Exception {
        String[] p = msg.getData().split(":", 2);
        String from = p[0];
        String to = p[1];

        Socket target = activeClients.get(to);
        Socket sender = activeClients.get(from);

        if (target == null) {
            if (sender != null) {
                sender.getOutputStream().write(MngCodec.encode(
                        new MngSimpleMessage(MngType.CHATAUFFORDERUNG_NOK, "offline")
                ));
            }
            return;
        }
        
        // Chat-Partner speichern
        chatPartner.put(from, to);
        chatPartner.put(to, from);
        
        // Chat-Anfrage an Empfänger senden
        target.getOutputStream().write(MngCodec.encode(new MngSimpleMessage(MngType.CHATANFRAGE, from)));
        target.getOutputStream().flush();

        sender.getOutputStream().write(MngCodec.encode(new MngSimpleMessage(MngType.CHATAUFFORDERUNG_OK, "zugestellt"))); // Bestätigung an Sender
        sender.getOutputStream().flush();
    }
    
    // Chat-Anfrage weiterleiten
    private void handleIncomingChatRequest(MngMessage msg) throws Exception {
        String from = msg.getData();
        Socket target = activeClients.get(chatPartner.get(from));
        if (target != null) {
            target.getOutputStream().write(MngCodec.encode(new MngSimpleMessage(MngType.CHATANFRAGE, from)));
            target.getOutputStream().flush();
        }
    }
    
    // Chat akzeptieren
    private void handleChatAccept(MngMessage msg) throws Exception {
        String [] p = msg.getData().split(":");
        String from = p[0];
        String to = p[1];

        if (to == null) return;

        Socket target = activeClients.get(to);
        if (target != null) {
            target.getOutputStream().write(MngCodec.encode(new MngSimpleMessage(MngType.IP_ADRESSE, from + ":" +"9876")));
            target.getOutputStream().flush();
            System.out.println("Sende IP an " + to);
        }

        Socket target2 = activeClients.get(from);
        if (target2 != null) {
            target2.getOutputStream().write(MngCodec.encode(new MngSimpleMessage(MngType.IP_ADRESSE, to + ":" + "9876")));
            target2.getOutputStream().flush();
            System.out.println("Sende IP an " + from);
        }
    }
    
    // Chat ablehnen
    private void handleChatDecline(MngMessage msg) throws Exception {
        String [] p = msg.getData().split(":");
        String from = p[0];
        String to = p[1];


        if (to == null) return;

        Socket target = activeClients.get(from);
        if (target != null) {
            target.getOutputStream().write(MngCodec.encode(new MngSimpleMessage(MngType.CHATAUFFORDERUNG_ABGELEHNT, to)));
            target.getOutputStream().flush();
        }

        chatPartner.remove(from);
        chatPartner.remove(to);
    }

    // Nachricht senden
    private void handleSendMessage(MngMessage msg) throws Exception {
        String[] p = msg.getData().split(":", 2);
        String from = p[0];
        String text = p[1];

        String to = chatPartner.get(from);
        if (to == null) return;

        Socket target = activeClients.get(to);
        if (target != null) {
            target.getOutputStream().write(MngCodec.encode(new MngSimpleMessage(MngType.SEND_MESSAGE, from + ":" + text)));
            target.getOutputStream().flush();
        }
    }

    // Chat schließen
    private void handleCloseChat(MngMessage msg) throws Exception {
        String [] p = msg.getData().split(":");
        String user = p[0];
        String partner = p[1];
        if (partner == null) return;

        Socket to = activeClients.get(partner);
        if (to != null) {
            to.getOutputStream().write(MngCodec.encode(new MngSimpleMessage(MngType.DELETE_IP, user)));
            to.getOutputStream().flush();
            System.out.println("Disconnect " + partner + " von " + user);
        }

        Socket target2 = activeClients.get(user);
        if (target2 != null) {
            target2.getOutputStream().write(MngCodec.encode(new MngSimpleMessage(MngType.DELETE_IP, partner)));
            target2.getOutputStream().flush();
            System.out.println("Disconnect " + user + " von " + partner);
        }

        chatPartner.remove(user);
        chatPartner.remove(partner);
    }

    public static void main(String[] args) throws Exception {
        new ManagementServer(9090).start();
    }
}
