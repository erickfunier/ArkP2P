package base;

import com.google.gson.Gson;

import java.io.*;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;

// Classe utilizada como objeto no envio e recebimento de mensagens
public class Mensagem implements Serializable {
    // header
    private final int id;
    private final Req req;

    // data
    private final List<String> msgList;

    // utilizado como valores do parametro REQUEST
    enum Req {
        JOIN,
        SEARCH,
        DOWNLOAD,
        LEAVE,
        ALIVE,
        ALIVE_OK,
        JOIN_OK,
        UPDATE,
        UPDATE_OK,
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

    public List<String> getMsgList() {
        return msgList;
    }


    // funcoes utilitarias

    /**
     * utilizado para comprimir um array de bytes
     * @param data array de bytes a ser comprimido
     * @return
     */
    private static byte[] compress(byte[] data) {
        if (data == null) {
            return null;
        }

        Deflater deflater = new Deflater();
        deflater.setInput(data);

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length)) {
            deflater.finish();
            byte[] buffer = new byte[1024];

            while (!deflater.finished()) {
                outputStream.write(buffer, 0, deflater.deflate(buffer));
            }

            byte[] dataOutput = outputStream.toByteArray();
            deflater.end();

            return dataOutput;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * utilizado para dedcomprimir
     * @param data array de bytes a ser descomprimido
     * @return
     */
    private static byte[] decompress(byte[] data) {
        if (data == null) {
            return null;
        }

        Inflater inflater = new Inflater();
        inflater.setInput(data);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length)) {
            byte[] buffer = new byte[1024];
            while (!inflater.finished()) {
                outputStream.write(buffer, 0, inflater.inflate(buffer));
            }

            byte[] dataOutput = outputStream.toByteArray();
            inflater.end();

            return dataOutput;
        } catch (IOException | DataFormatException e) {
            return null;
        }
    }

    /**
     * usado para obter um array de bytes proveniente de um objeto Mensagem,
     * o convertendo para json e comprimindo (Mensagem->json->bytes comprimidos)
     * @param msg objeto Mensagem a ser convertido em um array de bytes
     * @return
     */
    public static byte[] msg2byteJsonComp(Mensagem msg) {
        Gson gson = new Gson();
        String msgJson = gson.toJson(msg);
        return compress(msgJson.getBytes());
    }

    /**
     * usado para obter um objeto Mensagem a partir de um array de bytes,
     * o descomprimindo e convertendo para json (bytes comprimidos->json->Mensagem)
     * @param data array de bytes a ser convertido em um objeto Mensagem
     * @return
     */
    public static Mensagem byte2msgJsonDecomp(byte[] data) {
        data = decompress(data);
        if (data == null) {
            return null;
        }

        Gson gson = new Gson();
        String msgJson = new String(data);

        return gson.fromJson(msgJson, Mensagem.class);
    }
}