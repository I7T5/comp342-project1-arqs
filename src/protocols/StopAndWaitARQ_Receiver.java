/**
 * Author:  David Zhang, Wyn Roberts, Ina Tang
 * Course:  COMP 342 Data Communications and Networking
 * Date:    23 March 2025
 * Description: Stop-and-Wait ARQ Receiver
 */


package protocols;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class StopAndWaitARQ_Receiver {

    private static final byte ACK = 0x06;
    private static final byte NAK = 0X21;
    private static final char MAX_SEQ_NUM = 255;
    private static final char TOTAL_SEQ_NUM = (MAX_SEQ_NUM + 1);
    private final int port;
    private final String outputFile;
    private ServerSocket serverSocket;
    private volatile boolean running;
    private final List<byte[]> receivedData;
    private int totalPacketsReceived;
    private int currentPacketIndex;

    public StopAndWaitARQ_Receiver(int port, String outputFile) {
        this.port = port;
        this.outputFile = outputFile;
        this.running = false;
        this.receivedData = new ArrayList<>();
        this.totalPacketsReceived = 0;
        this.currentPacketIndex = 0;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        System.out.println("Receiver listening on port " + port);
        Socket clientSocket = serverSocket.accept();
        DataInputStream in = new DataInputStream(clientSocket.getInputStream());
        DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());

        boolean isLastPacket = false; // To track last packet

        while (running && !isLastPacket) { // Keep running until last packet is received
            try {
                // Read packet metadata
                int packetLength = in.readInt();
                char packetIndex = in.readChar();
                isLastPacket = in.readBoolean(); // Update tracking of last packet

                // Read packet data
                byte[] packetData = new byte[packetLength];
                in.readFully(packetData);
                BISYNCPacket packet = new BISYNCPacket(packetData, true);

                // Check if packet is valid
                if (packet.getData() != null && packet.isValid()) {
                    while (receivedData.size() <= packetIndex) {
                        receivedData.add(null);
                    }

                    // Store packet data at correct index
                    receivedData.set(packetIndex, packet.getData());

                    // Send ACK with next expected sequence number
                    out.writeChar(ACK);
                    out.writeChar((packetIndex + 1) % TOTAL_SEQ_NUM);
                    out.flush(); // Ensure ACK is sent properly

                    totalPacketsReceived++;
                    currentPacketIndex++;

                } else {
                    // Invalid packet: Send NAK for same index
                    out.writeChar(NAK);
                    out.writeChar(packetIndex);
                    out.flush(); // Ensure NAK is sent properly
                }

            } catch (IOException e) {
                if (running) {
                    System.err.println("Error handling client: " + e.getMessage());
                }
            }
        }

        // After receiving all packets, save them into the output file
        saveFile();
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

            // Write to file
            Files.write(Paths.get(outputFile), completeFile);
            System.out.println("File saved successfully: " + outputFile);
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
