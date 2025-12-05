package management;

import management.message.*;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

public class ManagementServer {

    private final int port;
    private final ConcurrentHashMap<String, String> registeredUsers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Socket> activeClients = new ConcurrentHashMap<>();

    public ManagementServer(int port) {
        this.port = port;
    }

    public void start() throws Exception {
        System.out.println("Management Server gestartet auf Port " + port);

         try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket client = serverSocket.accept();  
                Thread t = new Thread(() -> handleClient(client), "client-handler");
                t.start();
            }
        }
    }

    private void handleClient(Socket clientSocket) {
        String registeredAs = null;
        try (Socket s = clientSocket;
             InputStream in = s.getInputStream();
             OutputStream out = s.getOutputStream()) {

            byte[] buffer = new byte[4096];
            while (true) {
                int read = in.read(buffer);
                if (read == -1) break;

                MngMessage msg = MngCodec.decode(buffer, read);

                switch (msg.getType()) {

                    case REGISTRIEREN -> handleRegister(msg, out);
                        //TODO: txt File mit Username + Passwort
                    case ANMELDUNG -> {
                        registeredAs = handleLogin(msg, out, s);
                     }
                    case CHATAUFFORDERUNG -> handleChatRequest(msg);
                    case CHATANFRAGE_OK, CHATANFRAGE_NOK -> forwardChatResponse(msg);
                    case CHATAUFFORDERUNG_ABGELEHNT -> forwardChatResponse(msg);
                    case SEND_MESSAGE -> handleSendMessage(msg);
                    case CLOSE_CHAT -> handleCloseChat(msg);
                    case DELETE_IP -> handleDeleteIp(msg);
                    case ABMELDUNG -> handleLogout()
                    default -> {
                     }
                }
            }
        } catch (Exception e) {
             System.err.println("Client handler error: " + e.getMessage());
        } finally {
             if (registeredAs != null) {
                activeClients.remove(registeredAs);
                System.out.println("User '" + registeredAs + "' disconnected and removed from activeClients");
            } else {
                 activeClients.values().removeIf(sock -> sock == clientSocket);
            }
        }
    }

    private void handleRegister(MngMessage msg, OutputStream out) throws Exception {
        String[] parts = msg.getData().split(":", 2);
        String user = parts[0];
        String pass = parts.length > 1 ? parts[1] : "";

        if (registeredUsers.containsKey(user)) {
            out.write(MngCodec.encode(new MngSimpleMessage(MngType.REGISTRIEREN_NOK, "Benutzer existiert")));
            out.flush();
            return;
        }

        registeredUsers.put(user, pass);
        out.write(MngCodec.encode(new MngSimpleMessage(MngType.REGISTRIEREN_OK, "Registriert")));
        out.flush();
        System.out.println("Registered user: " + user);
    }

 
    private String handleLogin(MngMessage msg, OutputStream out, Socket clientSocket) throws Exception {
        String[] parts = msg.getData().split(":", 2);
        String user = parts[0];
        String pass = parts.length > 1 ? parts[1] : "";

        if (!registeredUsers.containsKey(user) || !registeredUsers.get(user).equals(pass)) {
            out.write(MngCodec.encode(new MngSimpleMessage(MngType.ANMELDUNG_NOK, "Login fehlgeschlagen")));
            out.flush();
            return null;
        }

        activeClients.put(user, clientSocket);
        out.write(MngCodec.encode(new MngSimpleMessage(MngType.ANMELDUNG_OK, "Login ok:" + user)));
        out.flush();
        System.out.println("User logged in: " + user);
        return user;
    }

    private void handleLogout(MngMessage msg, OutputStream out, Socket clientSocket) throws Exception {
        String user = msg.getData();
        if(activeClients.containsKey(user)){
            activeClients.remove(user);
            out.write(MngCodec.encode(new MngSimpleMessage(MngType.ABMELDUNG_OK, "Logout ok:")));
            out.flush();
            System.out.println("User logged out: " + user);
        }
        else{
            out.write(MngCodec.encode(new MngSimpleMessage(MngType.ABMELDUNG_NOK, "Logout fehlgeschlagen")));
            out.flush();
        }
    }

    private void handleChatRequest(MngMessage msg) throws Exception {
         String[] parts = msg.getData().split(":", 2);
        String from = parts[0];
        String to = parts.length > 1 ? parts[1] : "";

        Socket target = activeClients.get(to);
        if (target == null || target.isClosed()) {
            Socket sender = activeClients.get(from);
            if (sender != null) {
                sender.getOutputStream().write(MngCodec.encode(
                        new MngSimpleMessage(MngType.CHATAUFFORDERUNG_NOK, "Benutzer offline")
                ));
                sender.getOutputStream().flush();
            }
            return;
        }

         target.getOutputStream().write(MngCodec.encode(new MngSimpleMessage(MngType.CHATANFRAGE, from)));
        target.getOutputStream().flush();

         Socket sender = activeClients.get(from);
        if (sender != null) {
            sender.getOutputStream().write(MngCodec.encode(new MngSimpleMessage(MngType.CHATAUFFORDERUNG_OK, "zugestellt")));
            sender.getOutputStream().flush();
        }
    }

    private void forwardChatResponse(MngMessage msg) throws Exception {
         String[] parts = msg.getData().split(":", 3);
        String from = parts[0];
        String to = parts.length > 1 ? parts[1] : "";

        Socket target = activeClients.get(to);
        if (target != null && !target.isClosed()) {
            target.getOutputStream().write(MngCodec.encode(msg));
            target.getOutputStream().flush();
        }

         if (msg.getType() == MngType.CHATANFRAGE_OK) {
            Socket fromSock = activeClients.get(from);
            if (fromSock != null && !fromSock.isClosed() && target != null && !target.isClosed()) {
                String ip = fromSock.getInetAddress().getHostAddress();
                target.getOutputStream().write(MngCodec.encode(
                        new MngSimpleMessage(MngType.IP_ADRESSE, from + ":" + ip)
                ));
                target.getOutputStream().flush();
            }
        }
    }

    private void handleSendMessage(MngMessage msg) throws Exception {
         String[] parts = msg.getData().split(":", 3);
        String from = parts[0];
        String to = parts.length > 1 ? parts[1] : "";
        String message = parts.length > 2 ? parts[2] : "";

        Socket target = activeClients.get(to);
        if (target != null && !target.isClosed()) {
             target.getOutputStream().write(MngCodec.encode(
                    new MngSimpleMessage(MngType.SEND_MESSAGE, from + ":" + message)
            ));
            target.getOutputStream().flush();
        } else {
             Socket sender = activeClients.get(from);
            if (sender != null && !sender.isClosed()) {
                sender.getOutputStream().write(MngCodec.encode(
                        new MngSimpleMessage(MngType.CHATAUFFORDERUNG_NOK, "Benutzer offline")
                ));
                sender.getOutputStream().flush();
            }
        }
    }

    private void handleCloseChat(MngMessage msg) {
     }

    private void handleDeleteIp(MngMessage msg) {
     }

    public static void main(String[] args) throws Exception {
        new ManagementServer(9090).start();
    }
}
