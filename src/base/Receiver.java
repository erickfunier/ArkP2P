package base;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

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

    public static void main(String[] args) {
        try {
            DatagramSocket datagramSocket = new DatagramSocket(9876);

            while (true) {
                byte[] recDataBuffer = new byte[1024];
                DatagramPacket recDatagramPacket = new DatagramPacket(recDataBuffer, recDataBuffer.length);
                System.out.println("Waiting packet...");
                datagramSocket.receive(recDatagramPacket);

                System.out.println("Packet received!");

                InetAddress inetAddress = recDatagramPacket.getAddress();
                int port = recDatagramPacket.getPort();
                Mensagem mensagem = byte2msg(recDatagramPacket.getData());

                System.out.println(mensagem.getMsg());

                Mensagem mensagemToSend = new Mensagem(0, mensagem.getMsg());

                byte[] sendDataBuffer = msg2byte(mensagemToSend);
                DatagramPacket sendDatagramPacket = new DatagramPacket(sendDataBuffer, sendDataBuffer.length, inetAddress, port);

                datagramSocket.send(sendDatagramPacket);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
