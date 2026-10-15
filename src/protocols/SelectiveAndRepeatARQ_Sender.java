/**
 * Author:  David Zhang, Wyn Roberts, Ina Tang
 * Course:  COMP 342 Data Communications and Networking
 * Date:    23 March 2025
 * Description: SelectiveRepeat ARQ Sender
 */


package protocols;


import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SelectiveAndRepeatARQ_Sender {

    private static final byte ACK = 0x06; // ACK
    private static final byte NAK = 0X21; // NAK
    private static final char MAX_SEQ_NUM = 255;
    private static final char TOTAL_SEQ_NUM = (MAX_SEQ_NUM+1);
    private final NetworkSender sender;
    private int winBase = 0;
    private int winSize = 0;

    public SelectiveAndRepeatARQ_Sender(NetworkSender sender, int winSize){
        this.sender = sender;
        // Sliding window
        this.winBase = 0;
        this.winSize = winSize;
    }

    public void transmit(List<BISYNCPacket> packets) throws IOException {

        // Handshake
        int N = packets.size();
        sender.sendHandshakeRequest(N, winSize);
        char[] response = sender.waitForResponse();
        if (response[0] != ACK) {
            System.out.println("Handshake failed, exit");
        } else {
            System.out.println("Handshake succeed, proceed!");
        }

        Boolean finished = false;

        // TODO: Task 3.a, Your code below

        Set<Integer> sentPackets = new HashSet<>();
        Set<Integer> ackPackets = new HashSet<>();
        int ackLoopCount = 0;  // how many times ACK's been moded

        int prevKNum = 0;  // ack or nak
        while (!finished) {

            try {
                for (int i = winBase; i < Math.min(winBase+winSize, N); i++) {
                    char packetIndex = (char) (i % TOTAL_SEQ_NUM);  // currentSeqNumber
                    // FEATURE: known issue: if window size is too small we might lose all packets in window and get stuck...
                    if (sentPackets.contains(i) || ackPackets.contains(i)) continue;  // avoid resending packets
//                    if (ackPackets.contains(i)) continue;  // if all frames within windows are lost  // duplicates don't work well with my ackLoopCount code on Receiver's side
                    boolean isLastPacket = (i == N - 1); 
                    if (isLastPacket) {
                        sender.sendPacket(packets.get(i).getPacket(), packetIndex, isLastPacket); 
                        System.out.println("Sending LAST packet " + i);
                    } else {
                        sender.sendPacketWithLost(packets.get(i), packetIndex, isLastPacket); 
                        System.out.println("Sending packet " + i);
                    }
                    sentPackets.add(i); 
                }

                response = sender.waitForResponse(); 

                if (response[0] == ACK) {
                    int ackNum = (int) response[1]; 
                    System.out.println("Received ACK " + ackNum);

                    // Credits Dr. Zhang
                    if (prevKNum > ackNum) {
                        ackLoopCount++;
                        System.out.println("ackLoopCount: " + ackLoopCount);
                    }

                    for (int i = prevKNum+(ackLoopCount-1)*TOTAL_SEQ_NUM; i < ackNum+ackLoopCount*TOTAL_SEQ_NUM; i++) ackPackets.add(i);  // keep track of ackedPackets

                    winBase = ackNum + ackLoopCount * TOTAL_SEQ_NUM;
                    System.out.println("winBase: " + winBase);
                    prevKNum = ackNum;

                } else if (response[0] == NAK) {
                    int nakNum = (int) response[1]; 
                    System.out.println("Received NAK " + nakNum);
                    int nakPacketIndex = nakNum + (ackLoopCount * TOTAL_SEQ_NUM);
                    byte[] packet = packets.get(nakPacketIndex).getPacket(); 
                    sender.sendPacket(packet, (char) nakNum, false);  // last frame won't be missing

                    if (prevKNum > nakNum) {
                        ackLoopCount++;
                        System.out.println("ackLoopCount: " + ackLoopCount);
                    }
                    prevKNum = nakNum;
                }
            } catch (IOException e) {
               System.err.println("Error transmitting packet: " + e.getMessage());
               return;
            }
        }
    }

}
