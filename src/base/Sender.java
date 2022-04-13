package base;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Sender {
    static List<Mensagem> mensagemBuffer = new ArrayList<>();
    static Timer timer;
    static int lastReceived = 0;
    static List<Integer> outOfOrder = new ArrayList<>();

    enum Mode {
        lenta(1),
        perda(2),
        fora_de_ordem(3),
        duplicada(4),
        normal(5),
        reenvio(6); // Usado apenas para o reenvio de fora de ordem

        private final int mode;

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

    static class Timeout extends TimerTask {
        private int id;
        private DatagramSocket datagramSocket;
        private InetAddress inetAddress;

        public Timeout(int id, DatagramSocket datagramSocket, InetAddress inetAddress) {
            this.id = id;
            this.datagramSocket = datagramSocket;
            this.inetAddress = inetAddress;
        }

        // TimerTask.run() method will be used to perform the action of the task

        public void run() {
            Mensagem mensagem = mensagemBuffer.get(id);

            if (mensagem.getAck() == Mensagem.Ack.RECONHECIDO)
                this.cancel();
            else {
                List<Integer> range = IntStream.range(lastReceived+1, mensagemBuffer.size()).boxed().collect(Collectors.toList());
                try {
                    if (range.size() > 0) {
                        System.out.println("Timeout! Reenviar " + range);
                        for (Integer i : range) {
                            sendMessage(timer, datagramSocket, inetAddress, i, null);
                            receivePacket(datagramSocket);
                        }
                    } else {
                        sendMessage(timer, datagramSocket, inetAddress, mensagemBuffer.size()-1, null);
                    }
                    //this.run();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static void sendMessage(Timer timer, DatagramSocket datagramSocket, InetAddress inetAddress, int index, Mode mode) throws IOException {
        Mensagem mensagem = mensagemBuffer.get(index);
        byte[] sendData = msg2byte(mensagem);

        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, inetAddress, 9876);
        datagramSocket.send(sendPacket);
        if (timer != null) {
            TimerTask task = new Timeout(mensagem.getIdentificador(), datagramSocket, inetAddress);

            timer.schedule(task, 5000);
        }

        mensagem.setAck(Mensagem.Ack.ENVIADO_NAO_RECONHECIDO);
        if (mode != null)
            System.out.println("Mensagem \""+ mensagem.getMsg() +"\" enviada como "+ mode +" com o id " + mensagem.getIdentificador());

        // Verifica se tem pacote fora de ordem a ser enviado
        if (!outOfOrder.isEmpty()) {
            for (Integer integer : outOfOrder) {
                mensagem = mensagemBuffer.get(integer);

                sendData = msg2byte(mensagem);

                sendPacket = new DatagramPacket(sendData, sendData.length, inetAddress, 9876);
                datagramSocket.send(sendPacket);

                mensagem.setAck(Mensagem.Ack.ENVIADO_NAO_RECONHECIDO);
                System.out.println("Mensagem \"" + mensagem.getMsg() + "\" enviada como " + Mode.fora_de_ordem.toString().replaceAll("_", " ") + " com o id " + mensagem.getIdentificador());

                TimerTask task2 = new Timeout(mensagem.getIdentificador(), datagramSocket, inetAddress);

                timer.schedule(task2, 5000);
                receivePacket(datagramSocket);

            }
            outOfOrder.clear();
        }
    }

    private static void receivePacket(DatagramSocket datagramSocket) throws IOException {
        byte[] recDataBuffer = new byte[1024];
        DatagramPacket recDatagramPacket = new DatagramPacket(recDataBuffer, recDataBuffer.length);

        datagramSocket.receive(recDatagramPacket);

        Mensagem mensagemReceived = byte2msg(recDatagramPacket.getData());

        if (mensagemReceived.getAck() == Mensagem.Ack.RECONHECIDO) {
            mensagemBuffer.get(mensagemReceived.getIdentificador()).setAck(Mensagem.Ack.RECONHECIDO);
            System.out.println("Mensagem id " + mensagemReceived.getIdentificador() + " recebida pelo receiver");
        }
        if (mensagemReceived.getIdentificador() > lastReceived)
            lastReceived = mensagemReceived.getIdentificador();


    }

    public static void main(String[] args) throws IOException {
        DatagramSocket datagramSocket = new DatagramSocket();
        InetAddress inetAddress = InetAddress.getByName("127.0.0.1");
        Scanner userInput = new Scanner(System.in);
        int idCounter = -1;
        timer = new Timer();

        while (true) {
            System.out.println("Digite a mensagem a ser enviada, ou se desejar sair digite \\exit:");
            String input = userInput.nextLine();
            if (input.equals("\\exit"))
                break;

            idCounter++;
            Mensagem mensagem = new Mensagem(idCounter, input);

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
                    sendMessage(timer, datagramSocket, inetAddress, mensagemBuffer.size()-1, Mode.lenta);
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
                    outOfOrder.add(mensagem.getIdentificador());
                    break;
                case duplicada:
                    mensagem.setAck(Mensagem.Ack.AUTORIZADO_NAO_ENVIADO);
                    mensagemBuffer.add(mensagem);
                    sendMessage(timer, datagramSocket, inetAddress, mensagemBuffer.size()-1, null);
                    sendMessage(timer, datagramSocket, inetAddress, mensagemBuffer.size()-1, Mode.duplicada);
                    //System.out.println("Mensagem \""+ mensagem.getMsg() +"\" enviada como duplicada com o id " + mensagem.getIdentificador());

                    // TODO
                    // Start timer and handle the receive message
                    break;
                case normal:
                    mensagem.setAck(Mensagem.Ack.AUTORIZADO_NAO_ENVIADO);
                    mensagemBuffer.add(mensagem);
                    sendMessage(timer, datagramSocket, inetAddress, mensagemBuffer.size()-1, Mode.normal);
                    //System.out.println("Mensagem \""+ mensagem.getMsg() +"\" enviada como normal com o id " + mensagem.getIdentificador());

                    // TODO
                    // Start timer and handle the receive message
                    break;
            }


            if (mensagem.getAck() != Mensagem.Ack.NAO_AUTORIZADO && mensagem.getAck() != Mensagem.Ack.DESCARTADO) // Se nao eh fora de ordem e nao eh perda
                receivePacket(datagramSocket);
            userInput.nextLine();
        }

        datagramSocket.close();
        timer.cancel();
    }
}
