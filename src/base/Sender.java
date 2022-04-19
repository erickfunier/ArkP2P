package base;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Sender {
    static final List<Mensagem> mensagemBuffer = new ArrayList<>(); // buffer de pacotes
    static final List<Integer> outOfOrder = new ArrayList<>(); // buffer de pacotes para o envio fora de ordem
    static Timer timer = new Timer(); // Timer utilizado para o time out
    static ThreadRun threadRun;
    static int lastReceivedId = 0; // usado para armazenar o ultimo id recebido pelo receiver, eh atualizado sempre que um pacote eh recebido
    static int n = 3; // usado para o go-back-n

    // Modos de envio das mensagens
    enum Mode {
        lenta,
        perda,
        fora_de_ordem,
        duplicada,
        normal,
        reenvio // Usado apenas para o reenvio de fora de ordem
    }

    static class ThreadRun extends Thread {
        private final DatagramSocket datagramSocket;
        public ThreadRun(DatagramSocket datagramSocket) {
            this.datagramSocket = datagramSocket;
        }
        @Override
        public void run() {
            try {
                while(true) {
                    receivePacket(datagramSocket); // blocking
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    // classe usada para manusear o Timeout dos pacotes enviados
    static class Timeout extends TimerTask {
        private final int id;
        private final DatagramSocket datagramSocket;
        private final InetAddress inetAddress;

        public Timeout(int id, DatagramSocket datagramSocket, InetAddress inetAddress) {
            this.id = id;
            this.datagramSocket = datagramSocket;
            this.inetAddress = inetAddress;
        }

        public void run() {
            Mensagem mensagem = mensagemBuffer.get(id);

            if (mensagem.getAck() == Mensagem.Ack.RECONHECIDO) {
                this.cancel();
            } else {
                List<Integer> range;
                if (mensagemBuffer.get(lastReceivedId).getAck() == Mensagem.Ack.RECONHECIDO)
                    range = IntStream.range(lastReceivedId + 1, mensagemBuffer.size()).boxed().collect(Collectors.toList());
                else
                    range = IntStream.range(lastReceivedId, mensagemBuffer.size()).boxed().collect(Collectors.toList());
                try {
                    if (range.size() > 0) {
                        System.out.println("Timeout! Reenviar " + range);
                        for (int i = 0, j = 0; j < range.size(); i++, j++) {
                            sendMessage(timer, datagramSocket, inetAddress, range.get(j), null);

                            if (i == n - 1) {
                                i = -1;
                                delay();
                            }
                        }
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // Usado para simular o modo lentidao
    private static void delay() {
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    // usado para o envio dos pacotes(mensagem) atravez do Socket
    // @timer: instância do timer para agendar um timeout
    // @datagramSocket: socket inicializado para o envio do pacote
    // @inetAddress: endereço ip para o envio do pacote
    // @index: index do pacote(Mensagem) no buffer para ser enviado
    // @mode: modo de envio, utilizado apenas para realizar a impressao da mensagem com o modo de envio
    private static void sendMessage(Timer timer, DatagramSocket datagramSocket, InetAddress inetAddress, int index, Mode mode) throws IOException {
        Mensagem mensagem = mensagemBuffer.get(index); // obtem a mensagem do buffer de pacotes
        byte[] sendData = Mensagem.msg2byte(mensagem); // obtem o array bytes a partir da mensagem

        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, inetAddress, 9876);
        datagramSocket.send(sendPacket);

        // Timeout - Instancia uma nova TimerTask e agenda ela no Timer passado como parametro. A função run do timer será chamada apos 5 segundos
        if (timer != null) {
            TimerTask task = new Timeout(mensagem.getIdentificador(), datagramSocket, inetAddress);

            timer.schedule(task, 5000);

            if (!threadRun.isAlive())
                threadRun.start();
        }

        mensagem.setAck(Mensagem.Ack.ENVIADO_NAO_RECONHECIDO); // Pacote ja foi enviado, atualiza o ack para ENVIADO_NAO_RECONHECIDO


        // utilizado para realizar a impressao do log da mensagem enviada, mensagens enviadas com o modo duplicado, serao impressas apenas uma vez
        if (mode != null)
            System.out.println("Mensagem \""+ mensagem.getMsg() +"\" enviada como "+ mode +" com o id " + mensagem.getIdentificador());

        // Verifica se tem pacote fora de ordem a ser enviado
        if (!outOfOrder.isEmpty()) {
            Collections.shuffle(outOfOrder);
            for (int i = 0; i < outOfOrder.size(); i++) {

                mensagem = mensagemBuffer.get(outOfOrder.get(i));

                sendData = Mensagem.msg2byte(mensagem);

                sendPacket = new DatagramPacket(sendData, sendData.length, inetAddress, 9876);
                datagramSocket.send(sendPacket);

                mensagem.setAck(Mensagem.Ack.ENVIADO_NAO_RECONHECIDO);
                System.out.println("Mensagem \"" + mensagem.getMsg() + "\" enviada como " + Mode.fora_de_ordem.toString().replaceAll("_", " ") + " com o id " + mensagem.getIdentificador());

                if (timer != null) {
                    TimerTask task2 = new Timeout(mensagem.getIdentificador(), datagramSocket, inetAddress);

                    timer.schedule(task2, 5000);
                }
            }
            outOfOrder.clear();
        }
    }

    // Utilizada para receber os pacotes de resposta do Receiver
    private static void receivePacket(DatagramSocket datagramSocket) throws IOException {

        byte[] recDataBuffer = new byte[1024];
        DatagramPacket recDatagramPacket = new DatagramPacket(recDataBuffer, recDataBuffer.length);

        datagramSocket.receive(recDatagramPacket);

        Mensagem mensagemReceived = Mensagem.byte2msg(recDatagramPacket.getData());

        // Caso o pacote recebido tenha o ACK de RECONHECIDO, atualiza o buffer do sender com o ACK da mensagem e faz o log para o usuario
        if (mensagemReceived.getAck() == Mensagem.Ack.RECONHECIDO && mensagemBuffer.get(mensagemReceived.getIdentificador()).getAck() != Mensagem.Ack.RECONHECIDO) {
            mensagemBuffer.get(mensagemReceived.getIdentificador()).setAck(Mensagem.Ack.RECONHECIDO);
            System.out.println("Mensagem id " + mensagemReceived.getIdentificador() + " recebida pelo receiver");

            // atualiza o id da ultima mensagem recebida pelo Receiver caso id seja maior que o ultimo id recebido
            if (mensagemReceived.getIdentificador() > lastReceivedId)
                lastReceivedId = mensagemReceived.getIdentificador();
        }

    }

    public static void main(String[] args) throws IOException {
        DatagramSocket datagramSocket = new DatagramSocket();
        InetAddress inetAddress = InetAddress.getByName("127.0.0.1");
        Scanner userInput = new Scanner(System.in);
        int idCounter = -1; // variavel utilizada como contador progressivo de pacotes criados para novas mensagens
        threadRun = new ThreadRun(datagramSocket);

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

            // trata o modo de acordo
            switch (Mode.values()[mode-1]) {
                case lenta:
                    mensagem.setAck(Mensagem.Ack.AUTORIZADO_NAO_ENVIADO);
                    mensagemBuffer.add(mensagem);
                    delay();
                    sendMessage(timer, datagramSocket, inetAddress, mensagemBuffer.size()-1, Mode.lenta);

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

                    break;
                case normal:
                    mensagem.setAck(Mensagem.Ack.AUTORIZADO_NAO_ENVIADO);
                    mensagemBuffer.add(mensagem);
                    sendMessage(timer, datagramSocket, inetAddress, mensagemBuffer.size()-1, Mode.normal);

                    break;
            }

            userInput.nextLine();
        }

        threadRun.interrupt();
        datagramSocket.close();
        timer.cancel();
    }
}
