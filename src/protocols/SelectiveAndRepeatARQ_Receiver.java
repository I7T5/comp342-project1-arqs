/**
 * Author:  David Zhang, Wyn Roberts, Ina Tang
 * Course:  COMP 342 Data Communications and Networking
 * Date:    23 March 2025
 * Description: SelectiveRepeat ARQ Receiver
 */

package protocols;


import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SelectiveAndRepeatARQ_Receiver {

    private static final byte ACK = 0x06; // ACK
    private static final byte NAK = 0X21; // NAK
    private static final char MAX_SEQ_NUM = 255;
    private static final char TOTAL_SEQ_NUM = (MAX_SEQ_NUM + 1);
    private final int port;
    private final String outputFile;
    private ServerSocket serverSocket;
    private volatile boolean running;
    private final List<byte[]> receivedData;
    private int totalPacketsReceived;

    public SelectiveAndRepeatARQ_Receiver(int port, int winSize, String outputFile) {
        this.port = port;
        this.outputFile = outputFile;
        this.running = false;
        this.receivedData = new ArrayList<>();
        this.totalPacketsReceived = 0;
    }

    private void ensureCapacity(int N) {
        while (receivedData.size() <= N) {
            receivedData.add(null);
        }
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        System.out.println("Receiver listening on port " + port);
        Socket clientSocket = serverSocket.accept();
        DataInputStream in = new DataInputStream(clientSocket.getInputStream());
        DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());

        // only resend nak once for each lost packet
        Set<Integer> nak_packets = new HashSet<>();

        // Handshake
        int N = in.readInt();
        int winSize = in.readInt();
        out.writeChar(ACK); // 1 is ACK
        out.writeChar(0); // any number should be good at this moment
        int winBase = 0;
        System.out.println("Receiver handshake, N: " + N + " winSize: " + winSize);
        Boolean[] flags = new Boolean[N]; // flags[i] indicate whether the packet i has been received
        for (int i = 0; i < N; i++) {
            flags[i] = false;
        }
        int prevPacketIndex = 0;  // NEW
//        int prevKNum = 0; // doesn't work in e.g. ACK 254, received 1
        int ackLoopCount = 0;  // NEW

        ensureCapacity(N);

        while (running) {
            try {
                // Read packet metadata
                int packetLength = in.readInt();
                char packetIndex = in.readChar();
                boolean isLastPacket = in.readBoolean();

                System.out.println("packetIndex : " + (int) (packetIndex));

                // Read packet data
                byte[] packetData = new byte[packetLength];
                in.readFully(packetData);
                BISYNCPacket packet = new BISYNCPacket(packetData, true);

                // TODO: Task 3.b, Your code below
                // ackLoopCount code
                if (prevPacketIndex > ((int)(packetIndex)+winSize)) {  // if prevKNum > packetIndex +
                    // e.g. ACK 254, lost 255, received 0
                    // want ackLoopCount++ so 0 is treated as 256
                    ackLoopCount++;
//                    System.out.println("prevPacketIndex: " + prevPacketIndex);
                    System.out.println("ackLoopCount: " + ackLoopCount);
                } else if ((prevPacketIndex+winSize+2) < (int)(packetIndex)) {  // +4 to account for speed difference stuff
                    // e.g. if we lost 255, received 0, then received 255
                    // still want the loopCount to be before increment
                    // next round it will be back again
                    ackLoopCount--;
//                    System.out.println("prevPacketIndex: " + prevPacketIndex);
                    System.out.println("ackLoopCount: " + ackLoopCount);
                }

                int packetIndexInt = ((int)(packetIndex)) + ackLoopCount*TOTAL_SEQ_NUM;
//                System.out.println("packetIndexInt: " + packetIndexInt);

                // if packet is in window, keep it and update winBase
                if (packetIndexInt < (winBase+winSize)) {
                    flags[packetIndexInt] = true;
                    receivedData.set(packetIndexInt, packet.getData());
                    totalPacketsReceived++;
//                    System.out.print("totalPacketsReceived: " + totalPacketsReceived + " ");
                    // Loop through flags to update winBase
                    while (flags[winBase]) {
                        winBase++;
                        if (winBase == N) {
                            running = false;
                            break;
                        }
                    }

                    if (!running) break;
                }
//                System.out.print("winBase: " + winBase + " ");
                boolean inOrder = winBase == totalPacketsReceived;

                if (inOrder) {
                    int ackNum = winBase % TOTAL_SEQ_NUM;
                    System.out.println("Sending ACK " + ackNum + " (" + winBase + ")");
                    out.writeChar(ACK);
                    out.writeChar(ackNum);
                    out.flush();
                } else {  // out-of-order
                    // send NAK for each missing frame in window before currentPacket
                    for (int i = winBase; i < Math.min(Math.min(winBase+winSize, packetIndexInt), N); i++) {
                        if (flags[i] || nak_packets.contains(i)) continue;  // send NAK only once for each frame

                        System.out.println("Sending NAK " + i % TOTAL_SEQ_NUM + " (" + i + ")");
                        out.writeChar(NAK);
                        out.writeChar(i % TOTAL_SEQ_NUM);
                        out.flush();
                        nak_packets.add(i);
                    }
                }

                // if we've received everything
                if (totalPacketsReceived == N || winBase == N) {
                    running = false;
                }

                prevPacketIndex = (int) packetIndex;

            } catch (IOException e) {
                if (running) {
                    System.err.println("Error handling client: " + e.getMessage());
                    return;
                }
            }
        }
        // finish receiving all the data
        System.out.println("receiver: finish receiving all packets, now save into file!");
        saveFile();
        stop();
    }

    private void saveFile() {
        try {
            // Calculate total size
            int totalSize = 0;
            for (byte[] data : receivedData) {
                if (data != null) {
                    totalSize += data.length;
                }
            }

            // Combine all packets
            byte[] completeFile = new byte[totalSize];
            int offset = 0;
            for (byte[] data : receivedData) {
                if (data != null) {
                    System.arraycopy(data, 0, completeFile, offset, data.length);
                    offset += data.length;
                }
            }
            // saveBytesAsIntegers(completeFile, "mytest3.log");
            // Write to file
            Files.write(Paths.get(outputFile), completeFile);
            System.out.println("Video file saved successfully: " + outputFile);
        } catch (IOException e) {
            System.err.println("Error saving video file: " + e.getMessage());
        }
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing server: " + e.getMessage());
        }
    }
}