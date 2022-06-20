package base;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Peer {

    private static String ip;
    private static int port;

    private static String destPath;
    private static final List<String> fileList = new ArrayList<>(); // lista de arquivos no peer


    static Timer timer = new Timer(); // Timer utilizado para o time out
    static ThreadRun threadRun;


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
            Mensagem mensagem = fileList.get(id);

            if (mensagem.getRequest() == Mensagem.Ack.RECONHECIDO) {
                this.cancel();
            } else {
                List<Integer> range;
                if (fileList.get(lastReceivedId).getRequest() == Mensagem.Ack.RECONHECIDO)
                    range = IntStream.range(lastReceivedId + 1, fileList.size()).boxed().collect(Collectors.toList());
                else
                    range = IntStream.range(lastReceivedId, fileList.size()).boxed().collect(Collectors.toList());
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
    private static void sendMessage(Mensagem mensagem, Timer timer, DatagramSocket datagramSocket, InetAddress inetAddress) throws IOException {
        byte[] sendData = Mensagem.msg2byte(mensagem); // obtem o array bytes a partir da mensagem

        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, inetAddress, 10098);
        datagramSocket.send(sendPacket);

        // Timeout - Instancia uma nova TimerTask e agenda ela no Timer passado como parametro. A função run do timer será chamada apos 5 segundos
        if (timer != null) {
            TimerTask task = new Timeout(mensagem.getId(), datagramSocket, inetAddress);

            timer.schedule(task, 5000);

            if (!threadRun.isAlive())
                threadRun.start();
        }

        mensagem.setRequest(Mensagem.Ack.ENVIADO_NAO_RECONHECIDO); // Pacote ja foi enviado, atualiza o ack para ENVIADO_NAO_RECONHECIDO


        // utilizado para realizar a impressao do log da mensagem enviada, mensagens enviadas com o modo duplicado, serao impressas apenas uma vez
        if (mode != null)
            System.out.println("Mensagem \""+ mensagem.getMsgList() +"\" enviada como "+ mode +" com o id " + mensagem.getId());

        // Verifica se tem pacote fora de ordem a ser enviado
        if (!outOfOrder.isEmpty()) {
            Collections.shuffle(outOfOrder);
            for (int i = 0; i < outOfOrder.size(); i++) {

                mensagem = fileList.get(outOfOrder.get(i));

                sendData = Mensagem.msg2byte(mensagem);

                sendPacket = new DatagramPacket(sendData, sendData.length, inetAddress, 9876);
                datagramSocket.send(sendPacket);

                mensagem.setRequest(Mensagem.Ack.ENVIADO_NAO_RECONHECIDO);
                System.out.println("Mensagem \"" + mensagem.getMsgList() + "\" enviada como " + Mode.fora_de_ordem.toString().replaceAll("_", " ") + " com o id " + mensagem.getId());

                if (timer != null) {
                    TimerTask task2 = new Timeout(mensagem.getId(), datagramSocket, inetAddress);

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
        if (mensagemReceived.getRequest() == Mensagem.Ack.RECONHECIDO && fileList.get(mensagemReceived.getId()).getRequest() != Mensagem.Ack.RECONHECIDO) {
            fileList.get(mensagemReceived.getId()).setRequest(Mensagem.Ack.RECONHECIDO);
            System.out.println("Mensagem id " + mensagemReceived.getId() + " recebida pelo receiver");

            // atualiza o id da ultima mensagem recebida pelo Receiver caso id seja maior que o ultimo id recebido
            if (mensagemReceived.getId() > lastReceivedId)
                lastReceivedId = mensagemReceived.getId();
        }

    }

    public static void main(String[] args) throws IOException {
        Scanner mmi = new Scanner(System.in); // interface homem maquina
        ip = mmi.next();
        port = mmi.nextInt();
        destPath = mmi.next();

        DatagramSocket datagramSocket = new DatagramSocket();
        InetAddress inetAddress = InetAddress.getByName("127.0.0.1");

        int idCounter = -1; // variavel utilizada como contador progressivo de pacotes criados para novas mensagens
        threadRun = new ThreadRun(datagramSocket);

        boolean leave = false;

        while (!leave) {
            System.out.println("Menu:\n" +
                    "1 - JOIN\n" +
                    "2 - SEARCH\n" +
                    "3 - DOWNLOAD\n" +
                    "4 - LEAVE");
            int mode = mmi.nextInt();
            idCounter++;
            Mensagem mensagem;

            // trata o modo de acordo
            switch (Mensagem.Req.values()[mode-1]) {
                case JOIN:
                    mensagem = new Mensagem(Mensagem.Req.JOIN, fileList);

                    sendMessage(mensagem, timer, datagramSocket, inetAddress);

                    break;
                case SEARCH:
                    String searchFile = mmi.next();
                    mensagem = new Mensagem(Mensagem.Req.SEARCH, Collections.singletonList(searchFile));

                    sendMessage(mensagem, timer, datagramSocket, inetAddress);
                    break;
                case DOWNLOAD:
                    // TODO: Requisicao iterando na lista de peer
                    break;
                case LEAVE:
                    mensagem = new Mensagem(idCounter, Mensagem.Req.LEAVE, null);

                    sendMessage(mensagem, timer, datagramSocket, inetAddress);
                    leave = true;
                    break;
            }

            mmi.nextLine();
        }

        threadRun.interrupt();
        datagramSocket.close();
        timer.cancel();
    }
}
