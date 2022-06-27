package base;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Servidor {
    static final Map<String, List<String>> peerList = new ConcurrentHashMap<>();
    static final Map<String, List<String>> peerPendingAliveQueue = new ConcurrentHashMap<>();
    public static final int PORT = 10098;
    static Timer timer = new Timer(); // Timer utilizado para o time out aguardando o ALIVE_OK
    static ThreadAlive threadAlive;
    private static DatagramSocket datagramSocket;

    public Servidor(DatagramSocket datagramSocket) {
        Servidor.datagramSocket = datagramSocket;
    }

    static class ThreadAlive extends Thread {
        private final DatagramSocket datagramSocket;
        public ThreadAlive(DatagramSocket datagramSocket) {
            this.datagramSocket = datagramSocket;
        }
        @Override
        public void run() {
            while (true) {
                delay();
                for (Map.Entry<String, List<String>> peer : peerList.entrySet()) {
                    if (!peerPendingAliveQueue.containsKey(peer.getKey())) { // alive ja solicitado para esse peer
                        String peerIp = peer.getKey().split(":")[0];
                        int port = Integer.parseInt(peer.getKey().split(":")[1]);

                        try {
                            InetAddress inetAddress = InetAddress.getByName(peerIp);

                            Mensagem mensagem = new Mensagem(-1, Mensagem.Req.ALIVE, null);

                            sendMessage(mensagem, datagramSocket, inetAddress, port);
                            peerPendingAliveQueue.put(peer.getKey(), peer.getValue());

                            // Timeout - Instancia uma nova TimerTask e agenda ela no Timer passado como parametro. A função run do timer será chamada apos 5 segundos
                            if (timer != null) {
                                TimerTask task = new Timeout(peer.getKey());

                                timer.schedule(task, 2000);
                            }

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

    static class ThreadHandleRequest extends Thread {
        private final DatagramSocket datagramSocket;
        private final DatagramPacket datagramPacket;
        public ThreadHandleRequest(DatagramSocket datagramSocket, DatagramPacket datagramPacket) {
            this.datagramSocket = datagramSocket;
            this.datagramPacket = datagramPacket;
        }
        @Override
        public void run() {
            //Mensagem msgReceived = Mensagem.byte2msg(datagramPacket.getData());
            Mensagem msgReceived = Mensagem.byte2msgJsonDecomp(datagramPacket.getData());
            Mensagem msgReply;
            InetAddress ipReceived;
            int portReceived;
            String peerNameReceived;

            switch(Objects.requireNonNull(msgReceived).getRequest()) {
                case JOIN:
                    ipReceived = datagramPacket.getAddress();
                    portReceived = datagramPacket.getPort();

                    System.out.print("Peer " + ipReceived.getHostAddress() + ":" + portReceived + " adicionado com arquivos ");
                    for (String file : msgReceived.getMsgList()) {
                        System.out.print(file + " ");
                    }
                    System.out.print("\n");

                    peerNameReceived = ipReceived.getHostAddress() + ":" + portReceived;

                    peerList.put(peerNameReceived, msgReceived.getMsgList());

                    msgReply = new Mensagem(msgReceived.getId(), Mensagem.Req.JOIN_OK, Collections.singletonList(peerNameReceived));

                    sendMessage(msgReply, datagramSocket, ipReceived, portReceived);
                    break;
                case ALIVE_OK:
                    ipReceived = datagramPacket.getAddress();
                    portReceived = datagramPacket.getPort();

                    peerNameReceived = ipReceived.getHostAddress() + ":" + portReceived;

                    peerPendingAliveQueue.remove(peerNameReceived);
                    break;
                case LEAVE:
                    ipReceived = datagramPacket.getAddress();
                    portReceived = datagramPacket.getPort();

                    peerNameReceived = ipReceived.getHostAddress() + ":" + portReceived;

                    peerList.remove(peerNameReceived);
                    msgReply = new Mensagem(msgReceived.getId(), Mensagem.Req.LEAVE_OK, null);


                    sendMessage(msgReply, datagramSocket, ipReceived, portReceived);
                    break;
                case SEARCH:
                    ipReceived = datagramPacket.getAddress();
                    portReceived = datagramPacket.getPort();

                    List<String> searchPeerList = new ArrayList<>();

                    for (Map.Entry<String, List<String>> peer : peerList.entrySet()) {
                        if (peer.getValue().contains(msgReceived.getMsgList().get(0))) {

                            searchPeerList.add(peer.getKey());
                        }
                    }

                    msgReply = new Mensagem(msgReceived.getId(), Mensagem.Req.SEARCH, searchPeerList);

                    sendMessage(msgReply, datagramSocket, ipReceived, portReceived);
                    break;
                case UPDATE:
                    ipReceived = datagramPacket.getAddress();
                    portReceived = datagramPacket.getPort();

                    for (Map.Entry<String, List<String>> peer : peerList.entrySet()) {
                        if (peer.getKey().equals(ipReceived.getHostAddress() + ":" + portReceived)) {
                            peer.getValue().add(msgReceived.getMsgList().get(0));
                        }
                    }

                    msgReply = new Mensagem(msgReceived.getId(), Mensagem.Req.UPDATE_OK, null);

                    sendMessage(msgReply, datagramSocket, ipReceived, portReceived);
                    break;
            }
        }
    }

    // Usado para simular o modo lentidao
    private static void delay() {
        try {
            Thread.sleep(30000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    static class Timeout extends TimerTask {
        private final String peerAddress;

        public Timeout(String peerAddress) {
            this.peerAddress = peerAddress;
        }

        public void run() {
            if (peerPendingAliveQueue.containsKey(peerAddress)) { // ALIVE_OK not received, remove peer
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

    // usado para o envio dos pacotes(mensagem) atravez do Socket
    // @mensagem:
    // @datagramSocket: socket inicializado para o envio do pacote
    // @inetAddress: endereço ip para o envio do pacote
    // @port: porta para o envio do pacote
    private static void sendMessage(Mensagem mensagem, DatagramSocket datagramSocket, InetAddress inetAddress, int port) {

        byte[] sendDataBuffer = Mensagem.msg2byteJsonComp(mensagem);

        DatagramPacket sendDatagramPacket = new DatagramPacket(sendDataBuffer, sendDataBuffer.length, inetAddress, port);
        try {
            datagramSocket.send(sendDatagramPacket);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void receivePacket(DatagramSocket datagramSocket) throws IOException {
        byte[] recDataBuffer = new byte[1024];
        DatagramPacket recDatagramPacket = new DatagramPacket(recDataBuffer, recDataBuffer.length);

        datagramSocket.receive(recDatagramPacket); // blocking

        ThreadHandleRequest threadHandleRequest = new ThreadHandleRequest(datagramSocket, recDatagramPacket);
        threadHandleRequest.start();
    }

    public static void main(String[] args) throws UnknownHostException, SocketException {
        String ip;
        Scanner mmi = new Scanner(System.in);
        ip = mmi.next();

        InetAddress inetAddressServer = InetAddress.getByName(ip);
        datagramSocket = new DatagramSocket(PORT, inetAddressServer);

        threadAlive = new ThreadAlive(datagramSocket);
        threadAlive.start();



        while(true) {
            try {
                if (datagramSocket.isClosed()) {
                    System.out.println("Socket Closed");
                } else {
                    receivePacket(datagramSocket);
                }

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
