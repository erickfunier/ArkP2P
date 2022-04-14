package base;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
        Mensagem mensagem;
        if (!mensagemBuffer.isEmpty()) {
            mensagem = mensagemBuffer.get(mensagemBuffer.size()-1);
            mensagem.setAck(Mensagem.Ack.RECONHECIDO);
        } else {
            mensagem = new Mensagem(0, null);
        }

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
                datagramSocket.receive(recDatagramPacket);

                Mensagem mensagem = byte2msg(recDatagramPacket.getData());

                if (mensagem.getIdentificador() == idCounter + 1) {
                    // Mensagem na ordem
                    mensagemBuffer.add(mensagem);

                    System.out.println("Mensagem id " + mensagem.getIdentificador() + " recebida na ordem, entregando para a camada de aplicação");
                    idCounter++;

                } else if (mensagem.getIdentificador() == idCounter || mensagem.getIdentificador() < idCounter) {
                    System.out.println("Mensagem id " + mensagem.getIdentificador() + " recebida de forma duplicada");

                } else {
                    int lastReceived;
                    if (mensagemBuffer.size() > 0)
                        lastReceived = mensagemBuffer.get(mensagemBuffer.size()-1).getIdentificador();
                    else
                        lastReceived = -1;
                    List<Integer> range = IntStream.range(lastReceived+1, mensagem.getIdentificador()).boxed().collect(Collectors.toList());
                    System.out.println("Mensagem id " + mensagem.getIdentificador() + " recebida fora de ordem, ainda não recebidos os identificadores " + range);

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
