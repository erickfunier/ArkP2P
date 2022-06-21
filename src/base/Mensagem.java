package base;

import java.io.*;
import java.util.List;

// Classe utilizada como objeto no envio e recebimento de mensagens
public class Mensagem implements Serializable {
    // header
    private int id;
    private Req req;

    // data
    private final List<String> msgList;

    // utilizado como valores do parametro ACK
    enum Req {
        JOIN,
        SEARCH,
        DOWNLOAD,
        LEAVE,
        ALIVE,
        ALIVE_OK,
        JOIN_OK,
        UPDATE,
        DOWNLOAD_NEGADO,
        LEAVE_OK
    }

    public Mensagem(int id, Req req, List<String> msgList) {
        this.id = id;
        this.req = req;
        this.msgList = msgList;
    }

    public int getId() {
        return id;
    }

    public Req getRequest() {
        return req;
    }

    public void setRequest(Req req) {
        this.req = req;
    }

    public List<String> getMsgList() {
        return msgList;
    }

    // usado para serializar um pacote(Mensagem) para um array de bytes
    public static byte[] msg2byte(Mensagem msg) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream)) {
            objectOutputStream.writeObject(msg);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return byteArrayOutputStream.toByteArray();
    }

    // usado apra deserializar um array de byte[] em um pacote(Mensagem)
    public static Mensagem byte2msg(byte[] data) {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(data);
        try (ObjectInputStream objectInputStream = new ObjectInputStream((byteArrayInputStream))) {
            return (Mensagem) objectInputStream.readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }
}