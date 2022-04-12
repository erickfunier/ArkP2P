package base;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;

public class Receiver {
    private static byte[] msg2byte(Mensagem msg) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream)) {
            objectOutputStream.writeObject(msg);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return byteArrayOutputStream.toByteArray();
    }

    private static Mensagem byte2msg(byte[] data) {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(data);
        try (ObjectInputStream objectInputStream = new ObjectInputStream((byteArrayInputStream))) {
            return (Mensagem) objectInputStream.readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static void sendMessage(DatagramSocket datagramSocket, InetAddress inetAddress, int port, List<Mensagem> mensagemBuffer) throws IOException {
        Mensagem mensagem = mensagemBuffer.get(mensagemBuffer.size()-1);
        mensagem.setAck(Mensagem.Ack.RECONHECIDO);

        byte[] sendDataBuffer = msg2byte(mensagem);
        DatagramPacket sendDatagramPacket = new DatagramPacket(sendDataBuffer, sendDataBuffer.length, inetAddress, port);
        datagramSocket.send(sendDatagramPacket);
    }

    public static void main(String[] args) {
        List<Mensagem> mensagemBuffer = new ArrayList<>();
        int idCounter = -1;
        try {
            DatagramSocket datagramSocket = new DatagramSocket(9876);


            while (true) {
                byte[] recDataBuffer = new byte[1024];
                DatagramPacket recDatagramPacket = new DatagramPacket(recDataBuffer, recDataBuffer.length);
                System.out.println("Waiting packet...");
                datagramSocket.receive(recDatagramPacket);

                System.out.println("Packet received!");

                Mensagem mensagem = byte2msg(recDatagramPacket.getData());

                if (mensagem.getIdentificador() == idCounter + 1) {
                    // Mensagem na ordem
                    mensagemBuffer.add(mensagem);

                    System.out.println("Mensagem id " + mensagem.getIdentificador() + " recebida na ordem, entregando para a camada de aplicação");
                }

                InetAddress inetAddress = recDatagramPacket.getAddress();
                int port = recDatagramPacket.getPort();
                sendMessage(datagramSocket, inetAddress, port, mensagemBuffer);

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
