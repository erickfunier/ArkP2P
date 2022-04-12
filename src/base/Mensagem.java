package base;

import java.io.Serializable;

public class Mensagem implements Serializable {
    private int identificador;
    private Ack ack;
    private String msg;

    enum Ack {
        NAO_AUTORIZADO,
        DESCARTADO,
        AUTORIZADO_NAO_ENVIADO,
        ENVIADO_NAO_RECONHECIDO,
        RECONHECIDO
    }

    public Mensagem(int identificador, String msg) {
        this.identificador = identificador;
        this.ack = Ack.NAO_AUTORIZADO;
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

    public void setMsg(String msg) {
        this.msg = msg;
    }
}
