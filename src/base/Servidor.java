package base;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Servidor {
    public static final int PORT = 10098;

    static final Map<String, List<String>> peerList = new ConcurrentHashMap<>();
    static final Map<String, List<String>> peerPendingAliveQueue = new ConcurrentHashMap<>();

    static class Timeout extends TimerTask {
        private final String peerAddress;

        public Timeout(String peerAddress) {
            this.peerAddress = peerAddress;
        }

        public void run() {
            if (peerPendingAliveQueue.containsKey(peerAddress)) { // ALIVE_OK nao recebido, remover peer
                System.out.print("Peer " + peerAddress.split(":")[0] + ":"
                        + peerAddress.split(":")[1] + " morto. Eliminando seus arquivos ");
                for (String file : peerList.get(peerAddress)) {
                    System.out.print(file + " ");
                }
                System.out.print("\n");
                peerList.remove(peerAddress);
                peerPendingAliveQueue.remove(peerAddress);
            }
        }
    }

    /**
     * Thread utilizado para verificacao do Alive dos peers
     */
    static class ThreadAlive extends Thread {
        private final DatagramSocket datagramSocket;

        public ThreadAlive(DatagramSocket datagramSocket) {
            this.datagramSocket = datagramSocket;
        }

        @Override
        public void run() {
            while (true) {
                delay(); // aguarda ~30 segundos
                for (Map.Entry<String, List<String>> peer : peerList.entrySet()) {
                    if (!peerPendingAliveQueue.containsKey(peer.getKey())) { // alive ja solicitado para esse peer
                        String peerIp = peer.getKey().split(":")[0];
                        int port = Integer.parseInt(peer.getKey().split(":")[1]);

                        try {
                            InetAddress inetAddress = InetAddress.getByName(peerIp);

                            Mensagem mensagem = new Mensagem(-1, Mensagem.Req.ALIVE, null);

                            sendMsg(mensagem, datagramSocket, inetAddress, port);
                            peerPendingAliveQueue.put(peer.getKey(), peer.getValue());

                            Timer timer = new Timer(); // Timer utilizado para o time out aguardando o ALIVE_OK

                            // Timeout - Instancia uma nova TimerTask e agenda ela no Timer passado como parametro o ip e porta do peer. A função run do timer será chamada apos 2 segundos
                            TimerTask task = new Timeout(peer.getKey());

                            timer.schedule(task, 2000);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }

        }

        @Override
        protected void finalize() throws Throwable {
            super.finalize();

            datagramSocket.close();
        }
    }

    /**
     * Thread utilizado para manipular as requisicoes provenientes do UDP
     */
    static class ThreadRequestHandler extends Thread {
        private final DatagramSocket datagramSocket;
        private final DatagramPacket datagramPacket;

        public ThreadRequestHandler(DatagramSocket datagramSocket, DatagramPacket datagramPacket) {
            this.datagramSocket = datagramSocket;
            this.datagramPacket = datagramPacket;
        }

        @Override
        public void run() {
            requestHandler();
        }

        private void requestHandler() {
            Mensagem receivedMsg = Mensagem.byte2msgJsonDecomp(datagramPacket.getData());
            Mensagem replyMsg;
            InetAddress receivedIp = datagramPacket.getAddress();
            int receivedPort = datagramPacket.getPort();
            String receivedPeerName = receivedIp.getHostAddress() + ":" + receivedPort;

            switch(Objects.requireNonNull(receivedMsg).getRequest()) {
                case JOIN:
                    if (!peerList.containsKey(receivedPeerName)) {
                        peerList.put(receivedPeerName, receivedMsg.getMsgList());
                        System.out.print("Peer " + receivedIp.getHostAddress() + ":" + receivedPort + " adicionado com arquivos ");
                        for (String file : receivedMsg.getMsgList()) {
                            System.out.print(file + " ");
                        }
                        System.out.print("\n");

                        replyMsg = new Mensagem(receivedMsg.getId(), Mensagem.Req.JOIN_OK, Collections.singletonList(receivedPeerName));
                        sendMsg(replyMsg, datagramSocket, receivedIp, receivedPort);
                    }

                    break;
                case ALIVE_OK:
                    peerPendingAliveQueue.remove(receivedPeerName);

                    break;
                case LEAVE:
                    if (peerList.containsKey(receivedPeerName)) {
                        peerList.remove(receivedPeerName);

                        replyMsg = new Mensagem(receivedMsg.getId(), Mensagem.Req.LEAVE_OK, null);
                        sendMsg(replyMsg, datagramSocket, receivedIp, receivedPort);
                    }

                    break;
                case SEARCH:
                    String fileName = receivedMsg.getMsgList().get(0);
                    List<String> searchPeerList = new ArrayList<>();
                    System.out.println("Peer " + receivedIp.getHostAddress() + ":" + receivedPort + " solicitou arquivo " + fileName);

                    for (Map.Entry<String, List<String>> peer : peerList.entrySet()) {
                        if (peer.getValue().contains(fileName)) {
                            searchPeerList.add(peer.getKey());
                        }
                    }

                    replyMsg = new Mensagem(receivedMsg.getId(), Mensagem.Req.SEARCH, searchPeerList);
                    sendMsg(replyMsg, datagramSocket, receivedIp, receivedPort);

                    break;
                case UPDATE:
                    String newFileName = receivedMsg.getMsgList().get(0);

                    for (Map.Entry<String, List<String>> peer : peerList.entrySet()) {
                        if (peer.getKey().equals(receivedIp.getHostAddress() + ":" + receivedPort)) {
                            if (!peer.getValue().contains(newFileName))
                                peer.getValue().add(newFileName);
                        }
                    }

                    replyMsg = new Mensagem(receivedMsg.getId(), Mensagem.Req.UPDATE_OK, null);
                    sendMsg(replyMsg, datagramSocket, receivedIp, receivedPort);

                    break;
            }
        }
    }

    /**
     * Usado para a espera de 30 segundos da checagem da ThreadAlive
     */
    private static void delay() {
        try {
            Thread.sleep(30000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * utilizado para o envio dos pacotes(mensagem) atraves do Socket
     * @param msg mensagem a ser enviada
     * @param datagramSocket socket inicializado para o envio do pacote
     * @param inetAddress endereço ip para o envio do pacote
     * @param port porta para o envio do pacote
     */
    private static void sendMsg(Mensagem msg, DatagramSocket datagramSocket, InetAddress inetAddress, int port) {
        byte[] sendDataBuffer = Mensagem.msg2byteJsonComp(msg);

        DatagramPacket sendDatagramPacket = new DatagramPacket(sendDataBuffer, sendDataBuffer.length, inetAddress, port);
        try {
            datagramSocket.send(sendDatagramPacket);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * utilizado para monitorar a chegada de algum pacote no datagramSocket,
     * ao receber o pacote uma thread para trata-lo de acordo eh acionada
     * @param datagramSocket socket inicializado para monitorar os pacotes
     * @throws IOException excecao do recebimento do datagrama
     */
    private static void requestMonitor(DatagramSocket datagramSocket) throws IOException {
        byte[] recDataBuffer = new byte[1024];
        DatagramPacket recDatagramPacket = new DatagramPacket(recDataBuffer, recDataBuffer.length);

        datagramSocket.receive(recDatagramPacket); // blocking

        ThreadRequestHandler threadRequestHandler = new ThreadRequestHandler(datagramSocket, recDatagramPacket);
        threadRequestHandler.start();
    }

    public static void main(String[] args) throws UnknownHostException, SocketException {
        Scanner mmi = new Scanner(System.in);
        String ip = mmi.next();

        InetAddress inetAddressServer = InetAddress.getByName(ip);
        DatagramSocket datagramSocket = new DatagramSocket(PORT, inetAddressServer);

        ThreadAlive threadAlive = new ThreadAlive(datagramSocket);
        threadAlive.start();

        while(true) {
            try {
                requestMonitor(datagramSocket);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
