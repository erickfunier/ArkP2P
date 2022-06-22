package base;

import com.google.gson.Gson;

import java.io.*;
import java.util.Arrays;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

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

    public static byte[] msg2byteComp(Mensagem msg) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream)) {
            objectOutputStream.writeObject(msg);

        } catch (IOException e) {
            e.printStackTrace();
        }
        return compress(byteArrayOutputStream.toByteArray());
    }

    public static byte[] compress(byte[] data) {

        if (data == null) {
            throw new IllegalArgumentException("data was null");
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

            System.out.println("Original: " + data.length + " bytes, -->  Compressed: " + output.length + " bytes");
            return output;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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

    public static Mensagem byte2msgDecomp(byte[] data) {
        data = decompress(data);

        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(data);
        try (ObjectInputStream objectInputStream = new ObjectInputStream((byteArrayInputStream))) {
            return (Mensagem) objectInputStream.readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static byte[] decompress(byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("data was null");
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
            System.out.println("Original: " + data.length + " bytes --> " + "Uncompressed: " + output.length + " bytes");
            return output;
        } catch (IOException | DataFormatException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] msg2byteJson(Mensagem msg) {
        Gson gson = new Gson();
        String msgJson = gson.toJson(msg);
        return msgJson.getBytes();
    }

    public static byte[] msg2byteJsonComp(Mensagem msg) {
        Gson gson = new Gson();
        String msgJson = gson.toJson(msg);
        return compress(msgJson.getBytes());
    }

    public static Mensagem byte2msgJson(byte[] data) {
        Gson gson = new Gson();
        String msgJson = Arrays.toString(data);
        return gson.fromJson(msgJson, Mensagem.class);
    }

    public static Mensagem byte2msgJsonDecomp(byte[] data) {
        data = decompress(data);

        Gson gson = new Gson();
        String msgJson = Arrays.toString(data);
        return gson.fromJson(msgJson, Mensagem.class);
    }
}