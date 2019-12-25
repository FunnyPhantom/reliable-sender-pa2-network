package reliable;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.nio.ByteBuffer;

public class PacketManager {
    private static PacketManager instance;
    private static final int MAX_MSG_SIZE = 2048;

    private PacketManager() {

    }

    public static byte[] chopTrailingZeros(byte[] data) {
        int dataLen = data.length;
        for (int i = dataLen - 1; i >= 0; i--) {
            if (data[i] != 0) {
                dataLen = i+1;
                break;
            }
        }
        byte[] newData = new byte[dataLen];
        System.arraycopy(data, 0, newData, 0, dataLen);
        return newData;
    }

    public static PacketManager getInstance() {
        if (instance == null) {
            instance = new PacketManager();
        }
        return instance;
    }

    public Header makeSynHeader() {
        return Header.getInstanceWithDefaultValues()
                .setSyn(true);

    }
    public Header makeSynAckHeader() {
        return Header.getInstanceWithDefaultValues()
                .setSyn(true)
                .setAck(true);
    }

    public DatagramPacket makeSynPacket(InetAddress address, int port) {
        var synHeader = makeSynHeader();

        return new DatagramPacket(synHeader.toBytes(), synHeader.toBytes().length, address, port);
    }

    public DatagramPacket makeSynAckPacket(InetAddress address, int port, int startSeqNumber) {
        var synAckHeader = makeSynAckHeader().setAckNumber(startSeqNumber);
        return new DatagramPacket(synAckHeader.toBytes(), synAckHeader.toBytes().length, address, port);
    }

    public DatagramPacket makeEmptyPacket(int size) {
        byte[] buf = new byte[size];
        return new DatagramPacket(buf, size);
    }
    public DatagramPacket makeEmptyPacket() {
        return makeEmptyPacket(MAX_MSG_SIZE);
    }
    public boolean isSynPacket(DatagramPacket p) {
        Header h = Header.getHeaderFromPacket(p);
        return (h.isSyn() && !h.isAck());
    }
    public boolean isSynAckPacket(DatagramPacket p) {
//        System.out.println("synack packet");
        Header h = Header.getHeaderFromPacket(p);
        return (h.isSyn() && h.isAck());
    }
    public DatagramPacket makeReliablePacket(DatagramPacket p, int seqNumber) {
//        System.out.println("PacketManager.makeReliablePacket");
//        System.out.println(" seqNumber = " + seqNumber);
        int checksum = calculateChecksum(p.getData());
        Header h = Header.getInstanceWithDefaultValues().setChecksum(checksum).setSeqNumber(seqNumber);

        int size = p.getData().length + Header.HEADER_SIZE;
        var newBytesBuffer = ByteBuffer.allocate(size);
        newBytesBuffer.put(h.toBytes());
        newBytesBuffer.put(p.getData());

        return new DatagramPacket(newBytesBuffer.array(), size, p.getAddress(), p.getPort());
    }
    public DatagramPacket makeAckPacket(int ackNumber, InetAddress address, int port) {
        var headers = Header.getInstanceWithDefaultValues().setAckNumber(ackNumber);
        return new DatagramPacket(headers.toBytes(), headers.toBytes().length, address, port);
    }

    public int calculateChecksum(byte[] data, int offset, int length) {
        int zeroPadLeft = length % 4;
//        System.out.println(zeroPadLeft);
        ByteBuffer byteBuffer;
        if (zeroPadLeft != 0) {
            zeroPadLeft = 4 - zeroPadLeft;
//            System.out.println("added zeropad");
            byte[] zeros = new byte[zeroPadLeft];
            for (int i = 0; i < zeroPadLeft; i++) {
                zeros[i] = 0;
            }
            byteBuffer = ByteBuffer.allocate(length + zeroPadLeft);
            byteBuffer.put(zeros).put(data, offset, length);
        } else {
//            System.out.println("nozeropad");
            byteBuffer = ByteBuffer.allocate(length);
            byteBuffer.put(data, offset, length);
        }
        byteBuffer.rewind();
//        System.out.println(byteBuffer.position());
//        System.out.println(byteBuffer.capacity());
        int checksum = 0;
        while (byteBuffer.hasRemaining()) {
            checksum += byteBuffer.getInt();
        }
//        System.out.println("checksum = " + checksum);
        return 0;
    }
    public int calculateChecksum(byte[] data) {
        return calculateChecksum(data, 0, data.length);
    }

    public boolean isAckNumberNPacket(DatagramPacket possibleAckPacket, int N) {
        Header h = Header.getHeaderFromPacket(possibleAckPacket);
        return h.getAckNumber() == N;
    }
    public boolean isSeqNumberNPacket(DatagramPacket possibleValidPacket, int N) {
        Header h = Header.getHeaderFromPacket(possibleValidPacket);
        return h.getAckNumber() == N;
    }

    public boolean isValidAck(DatagramPacket p, int expectedAck) {
        Header h = Header.getHeaderFromPacket(p);
        return h.getAckNumber() > expectedAck;
    }
    public int getValidAckNumber(DatagramPacket p) {
//        System.out.println(Header.getHeaderFromPacket(p).getAckNumber());
        return Header.getHeaderFromPacket(p).getAckNumber();
    }

    public boolean isValidPacket(DatagramPacket p, int seqNumberReceiver) {
        Header h = Header.getHeaderFromPacket(p);
//        System.out.println("in is valid packet");
//        System.out.println("h.getSeqNumber() = " + h.getSeqNumber());
//        System.out.println("h.getAckNumber() = " + h.getAckNumber());
//        System.out.println("h.isAck() = " + h.isAck());
//        System.out.println("h.isSyn() = " + h.isSyn());
        int checksum = calculateChecksum(getRawBytes(p));
        boolean isValid = true;
        if (h.getChecksum() != checksum) {
//            System.out.println("checksum not valid");
//            System.out.println("h.getChecksum() = " + h.getChecksum());
//            System.out.println("checksum = " + checksum);
            isValid = false;
        }
        if (h.getSeqNumber() != seqNumberReceiver) {
//            System.out.println("seqNumberNotValid");
//            System.out.println("h.getSeqNumber() = " + h.getSeqNumber());
//            System.out.println("seqNumberReceiver = " + seqNumberReceiver);
            isValid = false;
        }
        return isValid;
    }

    public byte[] getRawBytes(DatagramPacket p) {
        var bb = ByteBuffer.allocate(p.getData().length - Header.HEADER_SIZE);
        bb.put(p.getData(), Header.HEADER_SIZE, p.getData().length - Header.HEADER_SIZE);
        return bb.array();
    }
}
