package base;

import java.io.*;
import java.net.*;
import java.util.*;

public class Peer {

    private static String peerIp;
    private static int peerPort;

    private static String path;
    private static List<String> fileList = new ArrayList<>(); // lista de arquivos no peer


    static Timer timer = new Timer(); // Timer utilizado para o time out
    static ThreadReceive threadReceive;


    static class ThreadReceive extends Thread {
        private final DatagramSocket datagramSocket;
        public ThreadReceive(DatagramSocket datagramSocket) {
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

        @Override
        protected void finalize() throws Throwable {
            super.finalize();

            datagramSocket.close();
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
            /*Mensagem mensagem = fileList.get(id);

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
            }*/
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
    private static void sendMessage(Mensagem mensagem, DatagramSocket datagramSocket, InetAddress inetAddress, int port) throws IOException {
        byte[] sendData = Mensagem.msg2byte(mensagem); // obtem o array bytes a partir da mensagem

        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, inetAddress, port);
        datagramSocket.send(sendPacket);

    }

    // Utilizada para receber os pacotes dos peers e do server
    private static void receivePacket(DatagramSocket datagramSocket) throws IOException {

        byte[] recDataBuffer = new byte[1024];
        DatagramPacket recDatagramPacket = new DatagramPacket(recDataBuffer, recDataBuffer.length);

        datagramSocket.receive(recDatagramPacket);

        Mensagem mensagemReceived = Mensagem.byte2msg(recDatagramPacket.getData());

        switch(Objects.requireNonNull(mensagemReceived).getRequest()) {
            case JOIN_OK:
                System.out.print("Sou peer " + datagramSocket.getLocalAddress().getHostAddress() + ":" + datagramSocket.getLocalPort() + " com arquivos ");
                for (String file : mensagemReceived.getMsgList()) {
                    System.out.print(file + " ");
                }
                System.out.print("\n");
                break;
            case ALIVE:
                InetAddress inetAddress = recDatagramPacket.getAddress();
                int port = recDatagramPacket.getPort();

                Mensagem mensagemResp = new Mensagem(Mensagem.Req.ALIVE_OK, null);

                sendMessage(mensagemResp, datagramSocket, inetAddress, port);
                break;
            case LEAVE_OK:
                System.out.println("LEAVE_OK");
                break;
        }
    }

    public static void main(String[] args) throws IOException {
        Scanner mmi = new Scanner(System.in); // interface homem maquina

        peerIp = mmi.next();
        peerPort = mmi.nextInt();

        InetAddress peerAddress = InetAddress.getByName(peerIp);

        DatagramSocket datagramSocket = new DatagramSocket(peerPort, peerAddress);

        String serverIp;
        int serverPort = 0;

        InetAddress serverAddress = null;



        while (true) {
            System.out.println("Menu:\n" +
                    "1 - JOIN\n" +
                    "2 - SEARCH\n" +
                    "3 - DOWNLOAD\n" +
                    "4 - LEAVE");
            int mode = mmi.nextInt();
            Mensagem mensagem;

            // trata o modo de acordo
            switch (mode) {
                case 1: // JOIN
                    serverIp = mmi.next();
                    serverPort = mmi.nextInt();
                    path = mmi.next();

                    serverAddress = InetAddress.getByName(serverIp);

                    File folder = new File(path);

                    File[] listOfFiles = folder.listFiles();

                    assert listOfFiles != null;
                    for (File file : listOfFiles) {
                        if (file.isFile()) {
                            fileList.add(file.getName());
                        }
                    }

                    /*for (String file : fileList) {
                        System.out.println(file);
                    }*/
                    threadReceive = new ThreadReceive(datagramSocket);
                    threadReceive.start();

                    mensagem = new Mensagem(Mensagem.Req.JOIN, fileList);

                    sendMessage(mensagem, datagramSocket, serverAddress, serverPort);

                    break;
                case 2: // SEARCH
                    String searchFile = mmi.next();
                    mensagem = new Mensagem(Mensagem.Req.SEARCH, Collections.singletonList(searchFile));

                    sendMessage(mensagem, datagramSocket, serverAddress, serverPort);
                    break;
                case 3: // DOWNLOAD
                    // TODO: Requisicao iterando na lista de peer
                    break;
                case 4: // LEAVE
                    mensagem = new Mensagem(Mensagem.Req.LEAVE, null);

                    sendMessage(mensagem, datagramSocket, serverAddress, serverPort);

                    break;
            }
        }



    }
    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        threadReceive.interrupt();
        timer.cancel();
    }
}
