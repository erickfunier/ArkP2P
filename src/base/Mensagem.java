package base;

import java.io.*;

// Classe utilizada como objeto no envio e recebimento de mensagens
public class Mensagem implements Serializable {
    // header
    private final int identificador;
    private Ack ack;

    // data
    private final String msg;

    // utilizado como valores do parametro ACK
    enum Ack {
        NAO_AUTORIZADO,
        DESCARTADO,
        AUTORIZADO_NAO_ENVIADO,
        ENVIADO_NAO_RECONHECIDO,
        RECONHECIDO
    }

    public Mensagem(int identificador, String msg) {
        this.identificador = identificador;
        this.ack = Ack.NAO_AUTORIZADO; // Inicializa o pacote como NAO AUTORIZADO
        this.msg = msg;
    }


    public int getIdentificador() {
        return identificador;
    }

    public Ack getAck() {
        return ack;
    }

    public void setAck(Ack ack) {
        this.ack = ack;
    }

    public String getMsg() {
        return msg;
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