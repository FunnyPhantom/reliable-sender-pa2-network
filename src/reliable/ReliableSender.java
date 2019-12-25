package reliable;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.regex.Pattern;

public class ReliableSender extends DatagramSocket {
    boolean isRelaying = false;
    boolean isConnectedToUserSender = false;
    boolean isConnectedToUserReceiver = false;
    int seqNumberSender = 0;
    int seqNumberReceiver = 0;
    Pattern connectMsgPattern = Pattern.compile("OK Relaying to .* at .*\\n.*");
    Pattern disconnectMsgPattern = Pattern.compile("OK Not relaying\\n.*");
    private static final long TIMEOUT_DELAY = 200;
    private InetAddress address = null;
    private int port = -1;
    Queue<DatagramPacket> sendQueue = new ArrayBlockingQueue<>(100);
    Thread sendHandlerThread;
    Semaphore itemsInQueueSemaphore = new Semaphore(0);


    private PacketManager packetManager;

    private DatagramSocket plainSocket;

    public ReliableSender() throws SocketException {
        super();
        plainSocket = new DatagramSocket();
        packetManager = PacketManager.getInstance();
        sendHandlerThread = new Thread(this::queueHandler);
        sendHandlerThread.start();

    }

    private static class TimeOutTask extends TimerTask{
        private DatagramPacket p;
        private DatagramSocket s;
        boolean log = false;

        public TimeOutTask(DatagramSocket s, DatagramPacket p, boolean log) {
            this.p = p;
            this.s = s;
            this.log = log;
        }
        public TimeOutTask(DatagramSocket s, DatagramPacket p) {
            this(s, p, false);
        }

        @Override
        public void run() {
            try {
                s.send(p);
                if (log){
//                    System.out.println("timeout tasssll");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void queueHandler() {
        try {
            while (true){
                itemsInQueueSemaphore.acquire();
                DatagramPacket packet = sendQueue.element();
                int currentSeqNumber = seqNumberSender;
                DatagramPacket reliablePacket = packetManager.makeReliablePacket(packet, currentSeqNumber);
                TimeOutTask timeOutTaskForReliablePacket = new TimeOutTask(plainSocket, reliablePacket);
                Timer interval = new Timer();
                interval.schedule(timeOutTaskForReliablePacket, 0, TIMEOUT_DELAY);
                DatagramPacket possibleAckPacket = packetManager.makeEmptyPacket();
                boolean loop = true;
                while (loop) {
                    plainSocket.receive(possibleAckPacket);
                    if (packetManager.isValidAck(possibleAckPacket, currentSeqNumber)) {
                        seqNumberSender = packetManager.getValidAckNumber(possibleAckPacket);
                        sendQueue.remove();
                        loop = false;
                    }
                }
                timeOutTaskForReliablePacket.cancel();
            }

        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void send(DatagramPacket p) throws IOException {
        if (isRelaying) {
            if (!isConnectedToUserSender) {
                connect(p);
            }
            sendQueue.add(p);
            itemsInQueueSemaphore.release();
        } else {
            if (address == null || port == -1) {
                address = p.getAddress();
                port = p.getPort();
            }
            plainSocket.send(p);
        }
    }

    private void connect(DatagramPacket p) throws IOException {
        DatagramPacket synPacket = packetManager.makeSynPacket(p.getAddress(), p.getPort());
        TimeOutTask timeOutTaskForSynPacket = new TimeOutTask(plainSocket, synPacket, true);
        Timer interval = new Timer();
        interval.schedule(timeOutTaskForSynPacket, 0, 3000);
        DatagramPacket possibleSynAckPacket = packetManager.makeEmptyPacket();
//        System.out.println("wating for ack");
        while (!(packetManager.isSynAckPacket(possibleSynAckPacket) || packetManager.isValidAck(possibleSynAckPacket, 0))) {
            plainSocket.receive(possibleSynAckPacket);
        }
//        System.out.println("got ack");
        timeOutTaskForSynPacket.cancel();
//        System.out.println("connected succsessfully");
        isConnectedToUserSender = true;
        seqNumberSender = 1;
//        System.out.println("seqNumberSender = " + seqNumberSender);

    }

    @Override
    public synchronized void receive(DatagramPacket p) throws IOException {
        if (isRelaying) {
            if (isConnectedToUserReceiver) {
                boolean loop = true;
                while (loop) {
                    plainSocket.receive(p);
//                    System.out.println("currentSeqNumberReceiver = " + currentSeqNumberReceiver);
                    if (packetManager.isValidPacket(p, seqNumberReceiver)) {
                        seqNumberReceiver++;
                        loop = false;
                    }
                    plainSocket.send(packetManager.makeAckPacket(seqNumberReceiver, address, port));
                }
                var data = packetManager.getRawBytes(p);
                p.setData(data);
                p.setLength(data.length);
            } else {
                waitForConnectionSignalAndSetConnectedReceiver();
                receive(p);
            }
        } else {
            plainSocket.receive(p);
            setRelayingAccordingToAns(p);
        }
    }

    private void waitForConnectionSignalAndSetConnectedReceiver() throws IOException {
        var possibleSyn = packetManager.makeEmptyPacket();
        while (!packetManager.isSynPacket(possibleSyn)) {
            plainSocket.receive(possibleSyn);
        }
//        System.out.println("received ");
        plainSocket.send(packetManager.makeSynAckPacket(address, port, 1));
        isConnectedToUserReceiver = true;
        seqNumberReceiver = 0;
    }

    public boolean isConnectPacketSent(DatagramPacket p) {
        String ans = new String(p.getData());
        var matcher = connectMsgPattern.matcher(ans);
        return matcher.matches();
    }

    public boolean isDisconnectPacketSent(DatagramPacket p) {
        String ans = new String(p.getData());
        var matcher = disconnectMsgPattern.matcher(ans);
        return matcher.matches();

    }

    private void setRelayingAccordingToAns(DatagramPacket p) {
        if (isConnectPacketSent(p)) {
            isRelaying = true;
        } else if (isDisconnectPacketSent(p)) {
            isRelaying = false;
        }
    }
}

