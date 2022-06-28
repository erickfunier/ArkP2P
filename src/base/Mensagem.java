package base;

import com.google.gson.Gson;

import java.io.*;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

// Classe utilizada como objeto no envio e recebimento de mensagens
public class Mensagem implements Serializable {
    // header
    private final int id;
    private Req req;

    // data
    private List<String> msgList;

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

    public static byte[] compress(byte[] data) {
        if (data == null) {
            return null;
        }

        Deflater deflater = new Deflater();
        deflater.setInput(data);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length)) {
            deflater.finish();
            byte[] buffer = new byte[1024];
            // limit while loop to max 10000 runs - to avoid infinite loops
            int maxRun = 0;
            while (!deflater.finished() && maxRun < 10000) { int count = deflater.deflate(buffer); outputStream.write(buffer, 0, count); maxRun++; if (maxRun >= 9998) {
                System.out.println("max run reached - stopping to avoid infinite looping");
                break;
            }
            }

            byte[] output = outputStream.toByteArray();
            deflater.end();

            //System.out.println("Original: " + data.length + " bytes, -->  Compressed: " + output.length + " bytes");
            return output;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] decompress(byte[] data) {
        if (data == null) {
            return null;
        }

        Inflater inflater = new Inflater();
        inflater.setInput(data);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length)) {
            byte[] buffer = new byte[1024];
            // limit while loop to max 10000 runs - to avoid infinite loops
            int maxRun = 0;
            while (!inflater.finished() && maxRun < Integer.MAX_VALUE) { int count = inflater.inflate(buffer); outputStream.write(buffer, 0, count); maxRun++; if (maxRun >= Integer.MAX_VALUE - 1) {
                System.out.println("max run reached - stopping to avoid infinite looping");
                break;
            }
            }

            byte[] output = outputStream.toByteArray();
            inflater.end();

            return output;
        } catch (IOException | DataFormatException e) {
            return null;
        }
    }

    public static byte[] msg2byteJsonComp(Mensagem msg) {
        Gson gson = new Gson();
        String msgJson = gson.toJson(msg);
        return compress(msgJson.getBytes());
    }

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