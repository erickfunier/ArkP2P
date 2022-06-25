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

    private static String path;
    private static final List<String> fileList = new ArrayList<>(); // lista de arquivos no peer
    private static List<String> searchPeerList = new ArrayList<>();

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

    static class ThreadTest extends Thread {
        private final DatagramSocket datagramSocket;
        public ThreadTest(DatagramSocket datagramSocket) {
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

    static class ThreadDownloadSender extends Thread {
        private final InetAddress ip;
        private final int port;
        private final String fileName;
        public ThreadDownloadSender(InetAddress ip, int port, String fileName) {
            this.ip = ip;
            this.port = port;
            this.fileName = fileName;
        }
        @Override
        public void run() {

            try (SocketChannel socketChannel = SocketChannel.open()) {
                SocketAddress socketAddress = new InetSocketAddress(ip, port);
                socketChannel.connect(socketAddress);
                System.out.println("Connected");

                Mensagem mensagem = new Mensagem(-1, Mensagem.Req.DOWNLOAD, Collections.singletonList(fileName));

                OutputStream outputStream = socketChannel.socket().getOutputStream();
                ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream);
                objectOutputStream.writeObject(mensagem);





                /*try (ObjectInputStream objectInputStream = new ObjectInputStream(socketChannel.socket().getInputStream())) {
                    Object mensagemRec = objectInputStream.readObject();

                    if (mensagemRec.getClass().equals(Mensagem.class)) {
                        socketChannel.socket().close();
                        System.out.println("MSG recebida " + ((Mensagem) mensagemRec).getRequest());
                    }
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException(e);
                } catch (IOException e) {
                    /*byte[] mybytearray = new byte[1024];
                    InputStream is2 = socketChannel.socket().getInputStream();
                    FileOutputStream fos = new FileOutputStream(fileName);
                    BufferedOutputStream bos = new BufferedOutputStream(fos);
                    int bytesRead = is2.read(mybytearray, 0, mybytearray.length);
                    bos.write(mybytearray, 0, bytesRead);
                    bos.close();
                    socketChannel.socket().close();
                    System.out.println("Done");

                    //throw new RuntimeException(e);
                }*/

                readFileFromSocket(socketChannel, fileName);

                /*byte[] mybytearray = new byte[1024];
                InputStream is = sock.getInputStream();
                FileOutputStream fos = new FileOutputStream("s.pdf");
                BufferedOutputStream bos = new BufferedOutputStream(fos);
                int bytesRead = is.read(mybytearray, 0, mybytearray.length);
                bos.write(mybytearray, 0, bytesRead);
                bos.close();
                sock.close();*/

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void readFileFromSocket(SocketChannel socketChannel, String fileName) {
        RandomAccessFile aFile;
        try {
            System.out.println(path + "\\" + fileName);
            aFile = new RandomAccessFile(path + "\\" + fileName, "rw");
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            FileChannel fileChannel = aFile.getChannel();

            int read = socketChannel.read(buffer);
            if (read > 0) {
                byte[] data = new byte[read];
                buffer.position(0);
                buffer.get(data);

                Mensagem mensagem = Mensagem.byte2msg(data);

                if (mensagem != null && mensagem.getClass().equals(Mensagem.class)) {
                    System.out.println(mensagem.getRequest());
                } else {
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
                    System.out.println("End of file reached..Closing channel");
                    socketChannel.close();
                }
            }




        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }

    }

    public static void sendFile(SocketChannel socketChannel, String fileName) {
        RandomAccessFile aFile;
        try {
            File file = new File(path + "\\" + fileName);
            aFile = new RandomAccessFile(file, "r");
            FileChannel inChannel = aFile.getChannel();
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            while (inChannel.read(buffer) > 0) {
                buffer.flip();
                socketChannel.write(buffer);
                buffer.clear();
            }
            Thread.sleep(1000);
            System.out.println("End of file reached..");
            socketChannel.close();
            aFile.close();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }

    }

    static class ThreadDownloadReceiver extends Thread {
        public ThreadDownloadReceiver() {
        }
        @Override
        public void run() {
            while (true) {
                try (ServerSocketChannel serverSocketChannel = ServerSocketChannel.open()) {
                    serverSocketChannel.socket().bind(new InetSocketAddress(peerIp, peerPort));
                    SocketChannel socketChannel = serverSocketChannel.accept();
                    System.out.println("Accepted connection : " + socketChannel);

                    InputStream is = socketChannel.socket().getInputStream();
                    ObjectInputStream objectInputStream = new ObjectInputStream(is);

                    try {
                        Object mensagemRec = objectInputStream.readObject();

                        if (mensagemRec.getClass().equals(Mensagem.class)) {
                            //socket.close();
                            System.out.println("MSG recebida " + ((Mensagem) mensagemRec).getRequest());
                            String fileName = ((Mensagem) mensagemRec).getMsgList().get(0);

                            Random rd = new Random(); // creating Random object

                            if (rd.nextBoolean()) {
                                Mensagem mensagem = new Mensagem(-1, Mensagem.Req.DOWNLOAD_NEGADO, null);

                                ByteBuffer buffer = ByteBuffer.wrap(Mensagem.msg2byte(mensagem));
                                socketChannel.write(buffer);

                                socketChannel.socket().close();
                            } else {
                                sendFile(socketChannel, fileName);
                            }

                            // send file
                        /*File myFile = new File (path + "\\" + fileName);
                        byte [] mybytearray  = new byte [(int)myFile.length()];
                        FileInputStream fis = new FileInputStream(myFile);
                        BufferedInputStream bis = new BufferedInputStream(fis);
                        bis.read(mybytearray,0,mybytearray.length);
                        OutputStream os = socketChannel.socket().getOutputStream();
                        System.out.println("Sending " + fileName + "(" + mybytearray.length + " bytes)");
                        os.write(mybytearray,0,mybytearray.length);
                        os.flush();
                        System.out.println("Done.");*/
                        } else {
                            //System.out.println("Classe errada");
                        }

                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }




                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

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

        //byte[] sendData = Mensagem.msg2byte(mensagem); // obtem o array bytes a partir da mensagem
        byte[] sendData = Mensagem.msg2byteJsonComp(mensagem); // obtem o array bytes a partir da mensagem

        //System.out.println(sendData.length);

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

                ThreadDownloadReceiver threadDownloadReceiver = new ThreadDownloadReceiver();
                threadDownloadReceiver.start();
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

                searchPeerList.clear();
                searchPeerList = msgReceived.getMsgList();

                System.out.print("Peers com o arquivo solicitado: ");
                for (String peer : searchPeerList) {
                    System.out.print(peer + " ");
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

                    if (path.contains(" "))
                        path = "'" + path + "'";

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
                    String ipString = mmi.next();
                    int port = mmi.nextInt();
                    String filename = mmi.next();

                    InetAddress ip = InetAddress.getByName(ipString);

                    ThreadDownloadSender threadDownloadSender = new ThreadDownloadSender(ip, port, filename);
                    threadDownloadSender.start();


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
