package base;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Peer {

    private static String peerIp;
    private static int peerPort;

    private static String path;
    private static final List<String> fileList = new ArrayList<>(); // lista de arquivos no peer

    private static Map<Integer, Mensagem> msgQueue = new ConcurrentHashMap<>(); // lista de mensagens enviadas esperando OK do servidor

    static Timer timer = new Timer(); // Timer utilizado para o time out
    static ThreadReceive threadReceive;

    private static int msgIdCounter = -1;


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
        private final DatagramSocket datagramSocket;
        private final InetAddress inetAddress;
        private final int port;
        private final Mensagem mensagem;

        public Timeout(Mensagem mensagem, DatagramSocket datagramSocket, InetAddress inetAddress, int port) {
            this.mensagem = mensagem;
            this.datagramSocket = datagramSocket;
            this.inetAddress = inetAddress;
            this.port = port;
        }

        public void run() {
            if (msgQueue.containsKey(mensagem.getId())) {
                System.out.println("Resending msg");
                sendMessage(mensagem, datagramSocket, inetAddress, port);
                msgQueue.remove(mensagem.getId());
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
    private static void sendMessage(Mensagem mensagem, DatagramSocket datagramSocket, InetAddress inetAddress, int port) {

        //byte[] sendData = Mensagem.msg2byteComp(mensagem); // obtem o array bytes a partir da mensagem
        byte[] sendData = Mensagem.msg2byteJsonComp(mensagem); // obtem o array bytes a partir da mensagem

        System.out.println(sendData.length);

        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, inetAddress, port);
        try {
            datagramSocket.send(sendPacket);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (timer != null && mensagem.getRequest() != Mensagem.Req.ALIVE_OK) {
            TimerTask task = new Timeout(mensagem, datagramSocket, inetAddress, port);

            timer.schedule(task, 2000);
            msgQueue.put(mensagem.getId(), mensagem);
        }
    }

    // Utilizada para receber os pacotes dos peers e do server
    private static void receivePacket(DatagramSocket datagramSocket) throws IOException {

        byte[] recDataBuffer = new byte[1024];
        DatagramPacket recDatagramPacket = new DatagramPacket(recDataBuffer, recDataBuffer.length);

        datagramSocket.receive(recDatagramPacket);

        //Mensagem msgReceived = Mensagem.byte2msgDecomp(recDatagramPacket.getData());
        Mensagem msgReceived = Mensagem.byte2msgJsonDecomp(recDatagramPacket.getData());

        switch(Objects.requireNonNull(msgReceived).getRequest()) {
            case JOIN_OK:
                msgQueue.remove(msgReceived.getId());

                System.out.print("Sou peer " + msgReceived.getMsgList().get(0) + " com arquivos ");
                for (String file : fileList) {
                    System.out.print(file + " ");
                }
                System.out.print("\n");
                break;
            case ALIVE:
                InetAddress inetAddress = recDatagramPacket.getAddress();
                int port = recDatagramPacket.getPort();

                Mensagem mensagemResp = new Mensagem(-1, Mensagem.Req.ALIVE_OK, null);

                sendMessage(mensagemResp, datagramSocket, inetAddress, port);
                break;
            case LEAVE_OK:
                msgQueue.remove(msgReceived.getId());

                break;
            case SEARCH:
                msgQueue.remove(msgReceived.getId());

                List<String> searchPeerList = msgReceived.getMsgList();

                System.out.print("Peers com o arquivo solicitado: ");
                for (String peer : searchPeerList) {
                    System.out.print(peer);
                }
                System.out.print("\n");

                break;
        }
    }

    public static void main(String[] args) throws IOException {
        Scanner mmi = new Scanner(System.in); // interface homem maquina

        /*peerIp = mmi.next();
        peerPort = mmi.nextInt();

        InetAddress peerAddress = InetAddress.getByName(peerIp);*/

        DatagramSocket datagramSocket = new DatagramSocket();

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
            msgIdCounter++;
            // trata o modo de acordo
            switch (mode) {
                case 1: // JOIN
                    serverIp = mmi.next();
                    serverPort = mmi.nextInt();
                    path = mmi.next();

                    serverAddress = InetAddress.getByName(serverIp);

                    File folder = new File(path);

                    File[] listOfFiles = folder.listFiles();

                    fileList.clear();
                    assert listOfFiles != null;
                    for (File file : listOfFiles) {
                        if (file.isFile()) {
                            fileList.add(file.getName());
                        }
                    }

                    threadReceive = new ThreadReceive(datagramSocket);
                    threadReceive.start();

                    mensagem = new Mensagem(msgIdCounter, Mensagem.Req.JOIN, fileList);

                    sendMessage(mensagem, datagramSocket, serverAddress, serverPort);

                    break;
                case 2: // SEARCH
                    String searchFile = mmi.next();

                    mensagem = new Mensagem(msgIdCounter, Mensagem.Req.SEARCH, Collections.singletonList(searchFile));

                    sendMessage(mensagem, datagramSocket, serverAddress, serverPort);
                    break;
                case 3: // DOWNLOAD
                    // TODO: Requisicao iterando na lista de peer
                    break;
                case 4: // LEAVE
                    mensagem = new Mensagem(msgIdCounter, Mensagem.Req.LEAVE, null);

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
