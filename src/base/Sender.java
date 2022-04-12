package base;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Sender {
    enum Mode {
        lenta(1),
        perda(2),
        fora_de_ordem(3),
        duplicada(4),
        normal(5);

        private int mode;

        Mode(int mode) {
            this.mode = mode;
        }

        public int getMode() {
            return mode;
        }
    }

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

    private static void delay() {
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static void sendMessage(DatagramSocket datagramSocket, InetAddress inetAddress, List<Mensagem> mensagemBuffer, Mode mode) throws IOException {
        Mensagem mensagem = mensagemBuffer.get(mensagemBuffer.size()-1);
        byte[] sendData = msg2byte(mensagem);

        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, inetAddress, 9876);
        datagramSocket.send(sendPacket);

        mensagem.setAck(Mensagem.Ack.ENVIADO_NAO_RECONHECIDO);
        if (mode != null)
            System.out.println("Mensagem \""+ mensagem.getMsg() +"\" enviada como "+ mode +" com o id " + mensagem.getIdentificador());

        // Verifica se tem pacote fora de ordem a ser enviado
        if (mensagemBuffer.size() > 1) {
            mensagem = mensagemBuffer.get(mensagemBuffer.size()-2);

            if (mensagem.getAck() == Mensagem.Ack.NAO_AUTORIZADO) {
                sendData = msg2byte(mensagem);

                sendPacket = new DatagramPacket(sendData, sendData.length, inetAddress, 9876);
                datagramSocket.send(sendPacket);

                mensagem.setAck(Mensagem.Ack.ENVIADO_NAO_RECONHECIDO);
                System.out.println("Mensagem \""+ mensagem.getMsg() +"\" enviada como "+ Mode.fora_de_ordem.toString().replaceAll("_", " ") +" com o id " + mensagem.getIdentificador());
            }
        }
    }

    public static void main(String[] args) throws IOException {
        DatagramSocket datagramSocket = new DatagramSocket();
        InetAddress inetAddress = InetAddress.getByName("127.0.0.1");
        Scanner userInput = new Scanner(System.in);
        List<Mensagem> mensagemBuffer = new ArrayList<>();
        int idCounter = -1;

        while (true) {
            System.out.println("Digite a mensagem a ser enviada, ou se desejar sair digite \\exit:");
            String input = userInput.nextLine();
            if (input.equals("\\exit"))
                break;

            idCounter++;
            Mensagem mensagem = new Mensagem(idCounter, input);

            //byte[] sendData = input.getBytes();
            System.out.println("Escolha a forma de envio:\n" +
                    "1 - lenta\n" +
                    "2 - perda\n" +
                    "3 - fora de ordem\n" +
                    "4 - duplicada\n" +
                    "5 - normal");
            int mode = userInput.nextInt();
            switch (Mode.values()[mode-1]) {
                case lenta:
                    mensagem.setAck(Mensagem.Ack.AUTORIZADO_NAO_ENVIADO);
                    mensagemBuffer.add(mensagem);
                    delay();
                    sendMessage(datagramSocket, inetAddress, mensagemBuffer, Mode.lenta);
                    //System.out.println("Mensagem \""+ mensagem.getMsg() +"\" enviada como lenta com o id " + mensagem.getIdentificador());

                    // TODO
                    // Start timer and handle the receive message
                    break;
                case perda:
                    mensagem.setAck(Mensagem.Ack.DESCARTADO);
                    mensagemBuffer.add(mensagem);
                    break;
                case fora_de_ordem:
                    mensagem.setAck(Mensagem.Ack.NAO_AUTORIZADO);
                    mensagemBuffer.add(mensagem);
                    break;
                case duplicada:
                    mensagem.setAck(Mensagem.Ack.AUTORIZADO_NAO_ENVIADO);
                    mensagemBuffer.add(mensagem);
                    sendMessage(datagramSocket, inetAddress, mensagemBuffer, null);
                    sendMessage(datagramSocket, inetAddress, mensagemBuffer, Mode.duplicada);
                    //System.out.println("Mensagem \""+ mensagem.getMsg() +"\" enviada como duplicada com o id " + mensagem.getIdentificador());

                    // TODO
                    // Start timer and handle the receive message
                    break;
                case normal:
                    mensagem.setAck(Mensagem.Ack.AUTORIZADO_NAO_ENVIADO);
                    mensagemBuffer.add(mensagem);
                    sendMessage(datagramSocket, inetAddress, mensagemBuffer, Mode.normal);
                    //System.out.println("Mensagem \""+ mensagem.getMsg() +"\" enviada como normal com o id " + mensagem.getIdentificador());

                    // TODO
                    // Start timer and handle the receive message
                    break;
            }

            /*byte[] recDataBuffer = new byte[1024];
            DatagramPacket recDatagramPacket = new DatagramPacket(recDataBuffer, recDataBuffer.length);

            datagramSocket.receive(recDatagramPacket);
            System.out.println("Packet received!");

            Mensagem mensagemReceived = byte2msg(recDatagramPacket.getData());

            String info = new String(recDatagramPacket.getData(),
                    recDatagramPacket.getOffset(),
                    recDatagramPacket.getLength());

            System.out.println(mensagemReceived.getMsg());*/
            userInput.nextLine();
        }

        datagramSocket.close();
    }
}