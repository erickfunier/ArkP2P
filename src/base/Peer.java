package base;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Peer {

    private static String destPath; // caminho da pasta contendo os arquivos
    private static final List<String> fileList = new ArrayList<>(); // lista de arquivos no peer
    private static List<String> lastSearchPeerList = new ArrayList<>(); // lista com os peers resultantes da ultima busca por um arquivo
    private static String lastSearchFileName;
    private static final Map<Integer, Mensagem> msgQueue = new ConcurrentHashMap<>(); // lista de mensagens enviadas esperando OK
    private static int msgIdCounter = -1; // contador para o ID das mensagens enviadas

    /**
     * Thread utilizado para monitorar e manipular as requisicoes e, respostas de requisicoes, atraves do UDP
     */
    static class ThreadRequestMonitorHandler extends Thread {
        private final DatagramSocket datagramSocket;
        public ThreadRequestMonitorHandler(DatagramSocket datagramSocket) {
            this.datagramSocket = datagramSocket;
        }
        @Override
        public void run() {
            try {
                while(true) {
                    requestHandler(datagramSocket); // blocking
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

        /**
         * utilizado para manipular as requisicoes ou, respostas de requisicoes, UDP
         * @param datagramSocket DatagramSocket utilizado para comunicacao UDP
         * @throws IOException
         */
        private void requestHandler(DatagramSocket datagramSocket) throws IOException {
            byte[] recDataBuffer = new byte[1024];
            DatagramPacket recDatagramPacket = new DatagramPacket(recDataBuffer, recDataBuffer.length);

            datagramSocket.receive(recDatagramPacket);

            Mensagem msgReceived = Mensagem.byte2msgJsonDecomp(recDatagramPacket.getData());

            switch(Objects.requireNonNull(msgReceived).getRequest()) {
                case JOIN_OK:
                    msgQueue.remove(msgReceived.getId());

                    // porta do peer
                    int peerPort = Integer.parseInt(msgReceived.getMsgList().get(0).split(":")[1]);

                    System.out.print("Sou peer " + msgReceived.getMsgList().get(0) + " com arquivos ");
                    for (String file : fileList) {
                        System.out.print(file + " ");
                    }
                    System.out.print("\n");

                    ThreadDownloadRequestMonitor threadDownloadRequestMonitor = new ThreadDownloadRequestMonitor(peerPort);
                    threadDownloadRequestMonitor.start();
                    break;
                case ALIVE:
                    InetAddress inetAddress = recDatagramPacket.getAddress();
                    int port = recDatagramPacket.getPort();

                    Mensagem mensagemResp = new Mensagem(-1, Mensagem.Req.ALIVE_OK, null);

                    sendMsg(mensagemResp, datagramSocket, inetAddress, port);
                    break;
                case LEAVE_OK:
                    lastSearchPeerList.clear();
                    msgQueue.remove(msgReceived.getId());

                    break;
                case UPDATE_OK:
                    msgQueue.remove(msgReceived.getId());

                    break;
                case SEARCH:
                    msgQueue.remove(msgReceived.getId());

                    lastSearchPeerList.clear();
                    lastSearchPeerList = msgReceived.getMsgList();

                    System.out.print("peers com o arquivo solicitado: ");
                    for (String peer : lastSearchPeerList) {
                        System.out.print(peer + " ");
                    }
                    System.out.print("\n");

                    break;
            }
        }
    }

    /**
     * Thread utilizado para realizar as requisicoes de DOWNLOAD a outro peer atraves do TCP
     */
    static class ThreadDownloadRequester extends Thread {
        private InetAddress ip;
        private int port;
        private final String fileName;
        private final List<String> searchPeerList;
        private final DatagramSocket datagramSocket;

        private final InetAddress serverAddress;

        private final int serverPort;

        public ThreadDownloadRequester(List<String> searchPeerList, InetAddress ip, int port, String fileName, DatagramSocket datagramSocket, InetAddress serverAddress, int serverPort) {
            this.ip = ip;
            this.port = port;
            this.fileName = fileName;
            this.searchPeerList = new ArrayList<>(searchPeerList);
            this.datagramSocket = datagramSocket;
            this.serverAddress = serverAddress;
            this.serverPort = serverPort;
        }
        @Override
        public void run() {
            int iterateCount = 0;
            while (!this.searchPeerList.isEmpty()) {
                try (Socket socket = new Socket(ip, port)) {

                    OutputStream outputStream = socket.getOutputStream();

                    Mensagem mensagem = new Mensagem(-1, Mensagem.Req.DOWNLOAD, Collections.singletonList(fileName));

                    byte[] buffer = Mensagem.msg2byteJsonComp(mensagem);
                    outputStream.write(buffer);
                    outputStream.flush();

                    if (!receiveFileFromSocket(socket, fileName)) {
                        if (this.searchPeerList.size() > 1) {
                            this.searchPeerList.remove(this.ip.getHostAddress() + ":" + this.port);
                            ip = InetAddress.getByName(this.searchPeerList.get(0).split(":")[0]);
                            port = Integer.parseInt(this.searchPeerList.get(0).split(":")[1]);
                        } else {
                            if (iterateCount == 0) {
                                this.searchPeerList.remove(this.ip.getHostAddress() + ":" + this.port);
                                Thread.sleep(3000);
                            }
                        }

                        System.out.println("peer " + socket.getInetAddress().getHostAddress() + ":"
                                + socket.getPort() + " negou o download, pedindo agora para o peer "
                                + this.ip.getHostAddress() + ":" + this.port);
                        socket.close();
                    } else {
                        socket.close();
                        break;
                    }

                } catch (IOException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
                iterateCount++;
            }
            Mensagem mensagem = new Mensagem(msgIdCounter, Mensagem.Req.UPDATE, Collections.singletonList(fileName));

            sendMsg(mensagem, datagramSocket, serverAddress, serverPort);
        }

        /**
         * utilizado para receber um arquivo a partir do socket TCP
         * @param socket socket da conexao com o outro peer
         * @param fileName nome do arquivo a ser recebido
         * @return
         */
        private boolean receiveFileFromSocket(Socket socket, String fileName) {
            try {
                InputStream inputStream = socket.getInputStream();
                byte[] buffer = new byte[1024];

                int read = inputStream.read(buffer);
                if (read > 0) {
                    byte[] data = buffer.clone();

                    Mensagem mensagem = Mensagem.byte2msgJsonDecomp(data);

                    if (mensagem != null && mensagem.getClass().equals(Mensagem.class) && mensagem.getRequest().equals(Mensagem.Req.DOWNLOAD_NEGADO)) {
                        socket.close();
                        return false;
                    } else {
                        FileOutputStream fileOutputStream = new FileOutputStream(destPath + "\\" + fileName);

                        fileOutputStream.write(buffer);
                        while (inputStream.read(buffer) > 0) {
                            fileOutputStream.write(buffer);
                        }

                        fileOutputStream.close();
                        socket.close();
                        System.out.println("Arquivo " + fileName + " baixado com sucesso na pasta " + destPath);
                        return true;
                    }
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
            return false;
        }
    }

    /**
     * Thread utilizado para monitorar as requisicoes de DOWNLOAD de outro peer atraves do TCP
     */
    static class ThreadDownloadRequestMonitor extends Thread {
        int peerPort;

        public ThreadDownloadRequestMonitor(int peerPort) {
            this.peerPort = peerPort;
        }
        @Override
        public void run() {
            try (ServerSocket serverSocket = new ServerSocket(this.peerPort)) {
                while (true) {
                    ThreadDownloadResponse threadDownloadResponse = new ThreadDownloadResponse(serverSocket.accept());
                    threadDownloadResponse.start();
                }
            } catch (IOException ignored) {
                // O socket sera fechado quando a thread for finalizada no comando LEAVE, gerando essa excecao
            }
        }
    }

    /**
     * Thread utilizado para responder a requisicao de DOWNLOAD atraves do TCP
     */
    static class ThreadDownloadResponse extends Thread {
        private final Socket socket;

        public ThreadDownloadResponse(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                byte[] buffer = new byte[1024];
                InputStream inputStream = this.socket.getInputStream();

                int read = inputStream.read(buffer);

                if (read > 0) {
                    byte[] data = buffer.clone();

                    Mensagem mensagemRec = Mensagem.byte2msgJsonDecomp(data);

                    if (mensagemRec != null && mensagemRec.getClass().equals(Mensagem.class)) {
                        String fileName = mensagemRec.getMsgList().get(0);

                        File file = new File(destPath + "\\" + fileName);
                        if (!file.isFile()) {
                            sendDownloadNegado();
                        } else {
                            Random rd = new Random();

                            if (rd.nextBoolean()) {
                                sendDownloadNegado();
                            } else {
                                sendFileToSocket(this.socket, fileName);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * utilizado para enviar um arquivo atraves do socket TCP
         * @param socket socket da conexao com o outro peer
         * @param fileName nome do arquivo a ser enviado
         */
        private void sendFileToSocket(Socket socket, String fileName) {
            try {
                FileInputStream fileInputStream = new FileInputStream(destPath + "\\" + fileName);
                OutputStream outputStream = socket.getOutputStream();

                byte[] buffer = new byte[1024];
                while (fileInputStream.read(buffer) > 0) {
                    outputStream.write(buffer);
                }

                fileInputStream.close();
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        /**
         * utilizado para enviar uma resposta (mensagem) de DOWNLOAD_NEGADO
         * @throws IOException
         */
        private void sendDownloadNegado() throws IOException {
            Mensagem mensagem = new Mensagem(-1, Mensagem.Req.DOWNLOAD_NEGADO, null);

            byte[] buffer = Mensagem.msg2byteJsonComp(mensagem);

            OutputStream outputStream = socket.getOutputStream();

            outputStream.write(buffer);

            this.socket.close();
        }
    }

    /**
     * TimeTask utilizado para reenviar as mensagens que nao receberam a resposta OK do servidor atraves do UDP
     */
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
                sendMsg(mensagem, datagramSocket, inetAddress, port);
                msgQueue.remove(mensagem.getId());
            }
        }
    }

    /**
     * utilizado para o envio dos pacotes(mensagem) atraves do Socket UDP
     * @param msg mensagem a ser enviada
     * @param datagramSocket socket inicializado para o envio do pacote
     * @param inetAddress endereço ip para o envio do pacote
     * @param port porta para o envio do pacote
     */
    private static void sendMsg(Mensagem msg, DatagramSocket datagramSocket, InetAddress inetAddress, int port) {
        byte[] sendData = Mensagem.msg2byteJsonComp(msg); // obtem o array bytes a partir da mensagem

        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, inetAddress, port);
        try {
            datagramSocket.send(sendPacket);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Timer timer = new Timer();

        if (msg.getRequest() != Mensagem.Req.ALIVE_OK) {
            TimerTask task = new Timeout(msg, datagramSocket, inetAddress, port);

            timer.schedule(task, 2000);
            msgQueue.put(msg.getId(), msg);
        }
    }

    public static void main(String[] args) throws IOException {
        Scanner mmi = new Scanner(System.in); // interface homem maquina

        InetAddress peerIp = InetAddress.getByName(mmi.nextLine());
        int peerPort = mmi.nextInt();
        mmi.next();
        destPath = mmi.nextLine();

        DatagramSocket datagramSocket = new DatagramSocket(peerPort, peerIp);

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

                    ThreadRequestMonitorHandler threadRequestMonitorHandler = new ThreadRequestMonitorHandler(datagramSocket);
                    threadRequestMonitorHandler.start();

                    mensagem = new Mensagem(msgIdCounter, Mensagem.Req.JOIN, fileList);

                    sendMsg(mensagem, datagramSocket, serverAddress, serverPort);

                    break;
                case 2: // SEARCH
                    lastSearchFileName = mmi.next();

                    mensagem = new Mensagem(msgIdCounter, Mensagem.Req.SEARCH, Collections.singletonList(lastSearchFileName));

                    sendMsg(mensagem, datagramSocket, serverAddress, serverPort);
                    break;
                case 3: // DOWNLOAD
                    String ipString = mmi.next();
                    int port = mmi.nextInt();

                    InetAddress ip = InetAddress.getByName(ipString);

                    ThreadDownloadRequester threadDownloadRequester = new ThreadDownloadRequester(lastSearchPeerList, ip, port, lastSearchFileName, datagramSocket, serverAddress, serverPort);
                    threadDownloadRequester.start();

                    break;
                case 4: // LEAVE
                    mensagem = new Mensagem(msgIdCounter, Mensagem.Req.LEAVE, null);
                    lastSearchFileName = "";
                    lastSearchPeerList.clear();

                    sendMsg(mensagem, datagramSocket, serverAddress, serverPort);

                    break;
            }
        }



    }
}
