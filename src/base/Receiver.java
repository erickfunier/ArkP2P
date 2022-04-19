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
    static final List<Mensagem> mensagemBuffer = new ArrayList<>(); // buffer de pacotes
    // usado para o envio dos pacotes(mensagem) atravez do Socket
    // @datagramSocket: socket inicializado para o envio do pacote
    // @inetAddress: endereço ip para o envio do pacote
    // @port: porta para o envio do pacote
    // @mode: modo de envio, utilizado apenas para realizar a impressao da mensagem com o modo de envio
    private static void sendMessage(DatagramSocket datagramSocket, InetAddress inetAddress, int port) throws IOException {
        Mensagem mensagem;
        // caso o pacote recebido seja o primeiro do buffer e fora de ordem, cria um novo pacote com um identificador 0
        // caso cotrario, retorna o ultimo pacote recebido, portanto, um pacote RECONHECIDO
        if (mensagemBuffer.isEmpty()) {
            mensagem = new Mensagem(0, null);
        } else {
            mensagem = mensagemBuffer.get(mensagemBuffer.size()-1);
        }

        byte[] sendDataBuffer = Mensagem.msg2byte(mensagem);
        DatagramPacket sendDatagramPacket = new DatagramPacket(sendDataBuffer, sendDataBuffer.length, inetAddress, port);
        datagramSocket.send(sendDatagramPacket);
    }

    public static void main(String[] args) {
        int idCounter = -1; // variavel utilizada como contador progressivo de pacotes criados para novas mensagens recebidas
        try {
            DatagramSocket datagramSocket = new DatagramSocket(9876);

            while (true) {
                byte[] recDataBuffer = new byte[1024];
                DatagramPacket recDatagramPacket = new DatagramPacket(recDataBuffer, recDataBuffer.length);
                datagramSocket.receive(recDatagramPacket);

                Mensagem mensagem = Mensagem.byte2msg(recDatagramPacket.getData());

                if (mensagem.getIdentificador() == idCounter + 1) {
                    // Mensagem na ordem
                    mensagem.setAck(Mensagem.Ack.RECONHECIDO);
                    mensagemBuffer.add(mensagem);

                    System.out.println("Mensagem id " + mensagem.getIdentificador() + " recebida na ordem, entregando para a camada de aplicacao");
                    idCounter++;

                } else if (mensagem.getIdentificador() <= idCounter) {
                    // Mensagem duplicada
                    System.out.println("Mensagem id " + mensagem.getIdentificador() + " recebida de forma duplicada");

                } else {
                    // Mensagem fora de ordem
                    int lastReceived;
                    if (mensagemBuffer.size() > 0)
                        lastReceived = mensagemBuffer.get(mensagemBuffer.size()-1).getIdentificador();
                    else
                        lastReceived = -1;

                    // Realiza a impressao dos identificadores das mensagens faltantes
                    List<Integer> range = IntStream.range(lastReceived+1, mensagem.getIdentificador()).boxed().collect(Collectors.toList());
                    System.out.println("Mensagem id " + mensagem.getIdentificador() + " recebida fora de ordem, ainda não recebidos os identificadores " + range);
                }

                InetAddress inetAddress = recDatagramPacket.getAddress();
                int port = recDatagramPacket.getPort();
                sendMessage(datagramSocket, inetAddress, port);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
