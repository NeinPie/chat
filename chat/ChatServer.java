package chat;

import java.net.*;

import chat.message.*;

import static chat.message.ChatMessageType.PONG;

public class ChatServer {
    /*TODO: Muss iwie mit ChatClient zsm geführt Werden*/

    public static void main(String[] args) throws Exception {
        DatagramSocket serverSocket = new DatagramSocket(9876);
        byte[] receiveData = new byte[1024];
        int actNr = -1;

        System.out.println("[SERVER] Server läuft auf Port 9876");

        while (true) {
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            serverSocket.receive(receivePacket);

            ChatMessage ping = ChatMessageCodec.decode(receivePacket.getData());
            try{
                if(actNr+1 != ping.getPacketNr()){
                    throw new MissingPacketException();
                }
                else{
                    actNr = ping.getPacketNr();
                }

                ChatMessage pong = new ChatMessageBase(PONG, ping.getSeq(), System.nanoTime(), ping.getPacketNr());

                InetAddress ipAdress = receivePacket.getAddress();
                int port = receivePacket.getPort();

                byte[] sendData = ChatMessageCodec.encode(pong);

                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, ipAdress, port);
                serverSocket.send(sendPacket);
            } catch (MissingPacketException e){
                System.out.println("Packet " + (actNr+1) + " fehlt. Packet " + ping.getPacketNr() + "wird verworfen.");
                ChatMessage pong = new ChatMessageBase(PONG, ping.getSeq(), System.nanoTime(), actNr);
                InetAddress ipAdress = receivePacket.getAddress();
                int port = receivePacket.getPort();
                byte[] sendData = ChatMessageCodec.encode(pong);
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, ipAdress, port);
                serverSocket.send(sendPacket);
            }
        }
    }
}
