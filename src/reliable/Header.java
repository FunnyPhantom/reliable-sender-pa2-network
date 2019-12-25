package reliable;

import java.net.DatagramPacket;
import java.nio.ByteBuffer;

public class Header {
    private int seqNumber;
    private int ackNumber;
    private int checksum;
    private boolean isAck;
    private boolean isSyn;
    private boolean isTerminate;
    static final int SEQ_NUMBER_OFFSET = 0;
    static final int ACK_NUMBER_OFFSET = 4;
    static final int CHECKSUM_OFFSET = 8;
    static final int IS_ACK_OFFSET = 12;
    static final int IS_SYN_OFFSET = 13;
    static final int IS_TERMINATE_OFFSET= 14;

    static final int HEADER_SIZE = 15;

    private Header() {}

    public static Header getInstance() {
        return new Header();
    }

    public Header setAck(boolean ack) {
        isAck = ack;
        return this;
    }

    public Header setSeqNumber(int seqNumber) {
        this.seqNumber = seqNumber;
        return this;
    }

    public Header setAckNumber(int ackNumber) {
        this.ackNumber = ackNumber;
        return this;
    }

    public Header setChecksum(int checksum) {
        this.checksum = checksum;
        return this;
    }

    public Header setSyn(boolean syn) {
        isSyn = syn;
        return this;
    }

    public Header setTerminate(boolean terminate) {
        isTerminate = terminate;
        return this;
    }
    public static Header getInstanceWithDefaultValues() {
        return getInstance()
                .setSeqNumber(0)
                .setAckNumber(0)
                .setChecksum(0)
                .setAck(false)
                .setSyn(false)
                .setTerminate(false);
    }

    public int getSeqNumber() {
        return seqNumber;
    }

    public int getAckNumber() {
        return ackNumber;
    }

    public int getChecksum() {
        return checksum;
    }

    public boolean isAck() {
        return isAck;
    }

    public boolean isSyn() {
        return isSyn;
    }

    public byte[] toBytes() {
        byte isAckByte = (byte) (isAck? 1: 0);
        byte isSynByte = (byte) (isSyn? 1: 0);
        byte isTerminateByte = (byte) (isTerminate? 1: 0);
        var bb = ByteBuffer.allocate(HEADER_SIZE)
                .putInt(SEQ_NUMBER_OFFSET, seqNumber)
                .putInt(ACK_NUMBER_OFFSET, ackNumber)
                .putInt(CHECKSUM_OFFSET, checksum)
                .put(IS_ACK_OFFSET, isAckByte)
                .put(IS_SYN_OFFSET, isSynByte)
                .put(IS_TERMINATE_OFFSET, isTerminateByte);

//        System.out.println("Header.toBytes");
//        System.out.println("checksum = " + checksum);
//        System.out.println("bb.getInt(CHECKSUM_OFFSET) = " + bb.getInt(CHECKSUM_OFFSET));

        return bb.array();
    }
    public static Header getHeaderFromBytes(byte[] data, int offset, int len) {
        var bBuf = ByteBuffer.allocate(len).put(data, offset, len);
        return getInstance()
                .setSeqNumber(bBuf.getInt(SEQ_NUMBER_OFFSET))
                .setAckNumber(bBuf.getInt(ACK_NUMBER_OFFSET))
                .setChecksum(bBuf.getInt(CHECKSUM_OFFSET))
                .setAck(bBuf.get(IS_ACK_OFFSET) == 1)
                .setSyn(bBuf.get(IS_SYN_OFFSET) == 1)
                .setTerminate(bBuf.get(IS_TERMINATE_OFFSET) == 1);
    }
    public static Header getHeaderFromPacket(DatagramPacket p, int offset, int len) {
        return getHeaderFromBytes(p.getData(), offset, len);
    }

    public static Header getHeaderFromPacket(DatagramPacket p) {
        return getHeaderFromBytes(p.getData(), 0, HEADER_SIZE);
    }
}
