package management;

import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Scanner;
import management.message.*;

public class ManagementClient {

    private final String host;
    private final int port;

    private String name;// Benutzername

    private String currentChatPartner = null; // aktueller Chatpartner

    private String currentChatRequestFrom = null; //Chat-Anfrage von diesem Benutzer

    private final Scanner scanner = new Scanner(System.in);

    private Socket socket;
    private volatile boolean running = true;

    private DatagramSocket partnersocket;
    private InetAddress partnerIP;
    private int partnerPort;
    private MessageListener partnerMessageListener;

    public class MessageListener extends Thread{
       @Override
        public void run(){
            while(!isInterrupted()){
                byte[] recvData = new byte[1024];
                DatagramPacket recvPacket = new DatagramPacket(recvData, recvData.length);
                System.out.println("[CLIENT] WARTE_AUF_MESSAGE...");
                partnersocket.receive(recvPacket);

                MngMessage msg = MngCodec.decode(recvPacket.getData(), recvPacket.getLength());
                if (msg.getType() == MngType.SEND_MESSAGE) {
                    System.out.println("[CLIENT] Message von " + currentChatPartner + " erhalten:");
                    System.out.println("    " + currentChatPartner + ": " + msg.getData());
                }
                MngSimpleMessage sendMessage = new MngSimpleMessage(MngType.SEND_MESSAGE_ACK, msg.getData());
                byte[] sendData1 = MngCodec.encode(sendMessage);
                DatagramPacket sendPacket = new DatagramPacket(sendData1, sendData1.length, partnerIP, partnerPort);
                System.out.println("\n[CLIENT] SENDE_MESSAGE_ACK(" + msg.getData() + ") ...");
                try {
                    partnersocket.send(sendPacket);
                } catch (IOException e) {
                    System.out.println("ERROR ACK SENDEN FEHLGESCHLAGEN");
                    e.printStackTrace();
                }
            }
        }
    }

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
                        System.out.println("Chatanfrage angenommen");
                    } else {
                        System.out.println("Keine offene Chatanfrage.");
                    }

                } else if (cmd.equals("/nok")) {
                    if (currentChatRequestFrom != null) {
                        send(MngType.CHATANFRAGE_NOK, currentChatRequestFrom + ":" + name);
                        System.out.println("Chatanfrage abgelehnt");
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
                    //send(MngType.SEND_MESSAGE, name + ":" + currentChatPartner + ":" + message);
                    MngSimpleMessage sendMessage = new MngSimpleMessage(MngType.SEND_MESSAGE, message);
                    byte[] sendData1 = MngCodec.encode(sendMessage);
                    DatagramPacket sendPacket = new DatagramPacket(sendData1, sendData1.length, partnerIP, partnerPort);
                    System.out.println("\n[CLIENT] Zustand: WARTE_AUF_AUFRUF " + message);
                    System.out.println("[CLIENT] SENDE_MESSAGE_(" + message + ") ...");
                    partnersocket.send(sendPacket);

                    int versuche = 0;
                    final int MAX_VERSUCHE = 3;
                    boolean korrektesPongErhalten = false;
                    while (!korrektesPongErhalten && versuche < MAX_VERSUCHE) {
                        try {
                            byte[] recvData = new byte[1024];
                            DatagramPacket recvPacket = new DatagramPacket(recvData, recvData.length);
                            System.out.println("[CLIENT] WARTE_AUF_ACK(" + message + ") ...");
                            partnersocket.receive(recvPacket);

                            MngMessage msg = MngCodec.decode(recvPacket.getData(), recvPacket.getLength());
                            if (msg.getType() == MngType.SEND_MESSAGE_ACK && msg.getData().equals(message)) {
                                System.out.println("[CLIENT] Korrektes ACK(" + message + ") erhalten.");
                                korrektesPongErhalten = true;
                            }
                        } catch (SocketTimeoutException e) {
                            System.out.println("[CLIENT] Timeout! Sende Message(" + message + ") erneut...");
                            partnersocket.send(sendPacket);
                            versuche++;
                        }
                    }
                    if(versuche >= MAX_VERSUCHE){
                        System.out.println("[CLIENT] Partner ist offline.");
                    }


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

                if (msg.getType() == MngType.IP_ADRESSE) {
                    String[] p = msg.getData().split(":");
                    System.out.println("225");
                    currentChatPartner = p[0]; 
                    System.out.println(currentChatPartner);
                    currentChatRequestFrom = null;
                    System.out.println("229");
                    partnersocket = new DatagramSocket();
                    System.out.println("231");
                    partnersocket.setSoTimeout(5000);
                    System.out.println("233");
                    partnerIP = InetAddress.getByName("localhost");
                    System.out.println("235");
                    partnerPort = Integer.parseInt(p[1]);
                    System.out.println("237");
                    partnerMessageListener = new MessageListener();
                    System.out.println("239");
                    System.out.println("[CLIENT] Verbindung zum Partner wurde aufgebaut.");
                }

                if (msg.getType() == MngType.CLOSE_CHAT) {
                    currentChatPartner = null;
                    currentChatRequestFrom = null;
                    disconnectToPartner();
                }
                if (msg.getType() == MngType.ABMELDUNG_OK) {
                    name = null;
                    currentChatPartner = null;
                    currentChatRequestFrom = null;
                }

                if (msg.getType() == MngType.CHATAUFFORDERUNG_ABGELEHNT) {
                    name = null;
                    currentChatPartner = null;
                    currentChatRequestFrom = null;
                    System.out.println("Chatanfrage wurde abgelehnt");
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

    //beende Verbindung mit Partner
    private void disconnectToPartner(){
        partnerMessageListener.interrupt();
        partnersocket.close();
        partnerIP = null;
        partnerPort = 0;
    }



    public static void main(String[] args) throws Exception {
        new ManagementClient("localhost", 9090).start();
    }
}