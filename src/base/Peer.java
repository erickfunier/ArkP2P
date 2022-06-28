package base;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Peer {

    private static String peerIp;
    private static int peerPort;
    private static String destPath;
    private static final List<String> fileList = new ArrayList<>(); // lista de arquivos no peer
    private static List<String> lastSearchPeerList = new ArrayList<>();

    private static final Map<Integer, Mensagem> msgQueue = new ConcurrentHashMap<>(); // lista de mensagens enviadas esperando OK do servidor

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

    static class ThreadDownloadRequester extends Thread {
        private InetAddress ip;
        private int port;
        private final String fileName;
        private final List<String> searchPeerList;
        private final DatagramSocket datagramSocket;

        private final InetAddress serverAddress;

        private final int serverPort;
        private final List<String> searchPeerListQueue = new ArrayList<>();
        public ThreadDownloadRequester(List<String> searchPeerList, InetAddress ip, int port, String fileName, DatagramSocket datagramSocket, InetAddress serverAddress, int serverPort) {
            this.ip = ip;
            this.port = port;
            this.fileName = fileName;
            this.searchPeerList = searchPeerList;
            this.datagramSocket = datagramSocket;
            this.serverAddress = serverAddress;
            this.serverPort = serverPort;
        }
        @Override
        public void run() {
            int iterateCount = 0;
            while (true) {
                try (SocketChannel socketChannel = SocketChannel.open()) {

                    SocketAddress socketAddress = new InetSocketAddress(ip, port);
                    socketChannel.connect(socketAddress);

                    Mensagem mensagem = new Mensagem(-1, Mensagem.Req.DOWNLOAD, Collections.singletonList(fileName));

                    ByteBuffer buffer = ByteBuffer.wrap(Mensagem.msg2byteJsonComp(mensagem));
                    socketChannel.write(buffer);

                    if (!readFileFromSocket(socketChannel, fileName)) {
                        if (!searchPeerListQueue.contains(this.ip.getHostAddress() + ":" + this.port)) {
                            searchPeerListQueue.add(this.ip.getHostAddress() + ":" + this.port);
                            this.searchPeerList.remove(this.ip.getHostAddress() + ":" + this.port);
                        }

                        if (!this.searchPeerList.isEmpty()) {
                            ip = InetAddress.getByName(this.searchPeerList.get(0).split(":")[0]);
                            port = Integer.parseInt(this.searchPeerList.get(0).split(":")[1]);
                        } else {
                            ip = InetAddress.getByName(this.searchPeerListQueue.get(iterateCount).split(":")[0]);
                            port = Integer.parseInt(this.searchPeerListQueue.get(iterateCount).split(":")[1]);
                            if (iterateCount >= this.searchPeerList.size()) {
                                iterateCount = 0;
                            } else {
                                iterateCount++;
                            }
                        }
                        System.out.println("peer " + socketChannel.socket().getInetAddress().getHostAddress() + ":"
                                + socketChannel.socket().getPort() + " negou o download, pedindo agora para o peer "
                                + this.ip.getHostAddress() + ":" + this.port);
                        socketChannel.close();
                    } else {
                        socketChannel.close();
                        break;
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            Mensagem mensagem = new Mensagem(msgIdCounter, Mensagem.Req.UPDATE, Collections.singletonList(fileName));

            sendMessage(mensagem, datagramSocket, serverAddress, serverPort);
        }
    }

    static class ThreadDownloadReceiver extends Thread {
        private final SocketChannel socketChannel;
        public ThreadDownloadReceiver(SocketChannel socketChannel) {
            this.socketChannel = socketChannel;
        }
        @Override
        public void run() {
            try {
                ByteBuffer buffer = ByteBuffer.allocate(1024);
                int read = this.socketChannel.read(buffer);

                if (read > 0) {
                    byte[] data = new byte[read];
                    buffer.position(0);
                    buffer.get(data);

                    Mensagem mensagemRec = Mensagem.byte2msgJsonDecomp(data);

                    if (mensagemRec != null && mensagemRec.getClass().equals(Mensagem.class)) {
                        String fileName = mensagemRec.getMsgList().get(0);

                        File file = new File(destPath + "\\" + fileName);
                        if (!file.isFile()) {
                            sendDownloadNegado(buffer);
                        } else {
                            Random rd = new Random(); // creating Random object

                            if (rd.nextBoolean()) {
                                sendDownloadNegado(buffer);
                            } else {
                                sendFile(this.socketChannel, fileName);
                                //sendFile2(this.socketChannel, fileName);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private void sendDownloadNegado(ByteBuffer buffer) throws IOException {
            Mensagem mensagem = new Mensagem(-1, Mensagem.Req.DOWNLOAD_NEGADO, null);

            buffer.clear();
            buffer = ByteBuffer.wrap(Mensagem.msg2byteJsonComp(mensagem));
            this.socketChannel.write(buffer);

            this.socketChannel.socket().close();
        }
    }

    static class ThreadDownloadReceiverHandler extends Thread {
        public ThreadDownloadReceiverHandler() {
        }
        @Override
        public void run() {
            try (ServerSocketChannel serverSocketChannel = ServerSocketChannel.open()) {
                serverSocketChannel.socket().bind(new InetSocketAddress(peerIp, peerPort));

                while (true) {
                    ThreadDownloadReceiver threadDownloadReceiver = new ThreadDownloadReceiver(serverSocketChannel.accept());
                    threadDownloadReceiver.start();
                }
            } catch (IOException ignored) {
                // O socket sera fechado quando a thread for finalizada no comando LEAVE, gerando essa excecao
            }
        }
    }

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
                System.out.println("Reenviando");
                sendMessage(mensagem, datagramSocket, inetAddress, port);
                msgQueue.remove(mensagem.getId());
            }
        }
    }

    public static boolean readFileFromSocket(SocketChannel socketChannel, String fileName) {
        try {
            ByteBuffer buffer = ByteBuffer.allocate(1024);

            int read = socketChannel.read(buffer);
            if (read > 0) {
                byte[] data = new byte[read];
                buffer.position(0);
                buffer.get(data);

                Mensagem mensagem = Mensagem.byte2msgJsonDecomp(data);

                if (mensagem != null && mensagem.getClass().equals(Mensagem.class)) {
                    socketChannel.close();
                    return false;
                } else {
                    RandomAccessFile aFile = new RandomAccessFile(destPath + "\\" + fileName, "rw");
                    FileChannel fileChannel = aFile.getChannel();
                    buffer.flip();
                    fileChannel.write(buffer);
                    buffer.clear();
                    while (socketChannel.read(buffer) > 0) {
                        buffer.flip();
                        fileChannel.write(buffer);
                        buffer.clear();
                    }
                    Thread.sleep(1000);
                    fileChannel.close();
                    System.out.println("Arquivo " + fileName + " baixado com sucesso na pasta " + destPath);
                    aFile.close();
                    socketChannel.close();
                    return true;
                }
            }

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return false;
    }


    public static void sendFile(SocketChannel socketChannel, String fileName) {
        RandomAccessFile aFile;
        try {
            File file = new File(destPath + "\\" + fileName);
            aFile = getRandomAccessFile(socketChannel, file);
            socketChannel.close();
            aFile.close();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void sendFile2(SocketChannel socketChannel, String fileName) {
        FileChannel inChannel = null;
        try {
            inChannel = new FileInputStream(destPath + "\\" + fileName).getChannel();
            inChannel.transferTo(0, inChannel.size(), socketChannel);
            socketChannel.close();
            inChannel.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


        /*RandomAccessFile aFile;
        try {
            File file = new File(destPath + "\\" + fileName);
            aFile = getRandomAccessFile(socketChannel, file);
            socketChannel.close();
            aFile.close();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }*/
    }

    static RandomAccessFile getRandomAccessFile(SocketChannel socketChannel, File file) throws IOException, InterruptedException {
        RandomAccessFile aFile;
        aFile = new RandomAccessFile(file, "r");
        FileChannel inChannel = aFile.getChannel();


        ByteBuffer buffer = ByteBuffer.allocate(1024);
        while (inChannel.read(buffer) > 0) {
            buffer.flip();
            socketChannel.write(buffer);
            buffer.clear();
        }
        Thread.sleep(1000);
        return aFile;
    }

    // usado para o envio dos pacotes(mensagem) atravez do Socket
    // @timer: instância do timer para agendar um timeout
    // @datagramSocket: socket inicializado para o envio do pacote
    // @inetAddress: endereço ip para o envio do pacote
    // @index: index do pacote(Mensagem) no buffer para ser enviado
    // @mode: modo de envio, utilizado apenas para realizar a impressao da mensagem com o modo de envio
    private static void sendMessage(Mensagem mensagem, DatagramSocket datagramSocket, InetAddress inetAddress, int port) {
        byte[] sendData = Mensagem.msg2byteJsonComp(mensagem); // obtem o array bytes a partir da mensagem

        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, inetAddress, port);
        try {
            datagramSocket.send(sendPacket);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Timer timer = new Timer();

        if (mensagem.getRequest() != Mensagem.Req.ALIVE_OK) {
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

        //Mensagem msgReceived = Mensagem.byte2msg(recDatagramPacket.getData());
        Mensagem msgReceived = Mensagem.byte2msgJsonDecomp(recDatagramPacket.getData());

        switch(Objects.requireNonNull(msgReceived).getRequest()) {
            case JOIN_OK:
                msgQueue.remove(msgReceived.getId());

                peerIp = msgReceived.getMsgList().get(0).split(":")[0];
                peerPort = Integer.parseInt(msgReceived.getMsgList().get(0).split(":")[1]);

                System.out.print("Sou peer " + msgReceived.getMsgList().get(0) + " com arquivos ");
                for (String file : fileList) {
                    System.out.print(file + " ");
                }
                System.out.print("\n");

                ThreadDownloadReceiverHandler threadDownloadReceiverHandler = new ThreadDownloadReceiverHandler();
                threadDownloadReceiverHandler.start();
                break;
            case ALIVE:
                InetAddress inetAddress = recDatagramPacket.getAddress();
                int port = recDatagramPacket.getPort();

                Mensagem mensagemResp = new Mensagem(-1, Mensagem.Req.ALIVE_OK, null);

                sendMessage(mensagemResp, datagramSocket, inetAddress, port);
                break;
            case LEAVE_OK:
            case UPDATE_OK:
                msgQueue.remove(msgReceived.getId());

                break;
            case SEARCH:
                msgQueue.remove(msgReceived.getId());

                lastSearchPeerList.clear();
                lastSearchPeerList = msgReceived.getMsgList();

                System.out.print("Peers com o arquivo solicitado: ");
                for (String peer : lastSearchPeerList) {
                    System.out.print(peer + " ");
                }
                System.out.print("\n");

                break;
        }
    }

    public static void main(String[] args) throws IOException {
        Scanner mmi = new Scanner(System.in); // interface homem maquina

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
                    destPath = mmi.next();

                    serverAddress = InetAddress.getByName(serverIp);

                    if (destPath.contains(" "))
                        destPath = "'" + destPath + "'";

                    File folder = new File(destPath);

                    File[] listOfFiles = folder.listFiles();

                    fileList.clear();
                    assert listOfFiles != null;
                    for (File file : listOfFiles) {
                        if (file.isFile()) {
                            fileList.add(file.getName());
                        }
                    }

                    ThreadReceive threadReceive = new ThreadReceive(datagramSocket);
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
                    String ipString = mmi.next();
                    int port = mmi.nextInt();
                    String filename = mmi.next();

                    InetAddress ip = InetAddress.getByName(ipString);

                    ThreadDownloadRequester threadDownloadRequester = new ThreadDownloadRequester(lastSearchPeerList, ip, port, filename, datagramSocket, serverAddress, serverPort);
                    threadDownloadRequester.start();


                    break;
                case 4: // LEAVE
                    mensagem = new Mensagem(msgIdCounter, Mensagem.Req.LEAVE, null);

                    sendMessage(mensagem, datagramSocket, serverAddress, serverPort);

                    break;
            }
        }



    }
}
