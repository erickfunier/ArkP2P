package base;

import java.io.Serializable;

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
}