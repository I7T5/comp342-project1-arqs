/**
 * Author:  David Zhang, Wyn Roberts, Ina Tang
 * Course:  COMP 342 Data Communications and Networking
 * Date:    23 March 2025
 * Description: Stop-and-Wait ARQ Sender
 */

package protocols;

import java.io.IOException;
import java.util.List;

public class StopAndWaitARQ_Sender {
    private static final byte ACK = 0x06;
    private static final byte NAK = 0X21;
    private final NetworkSender sender;
    private char currSeqNumber = 0; // 0 - 255

    public StopAndWaitARQ_Sender(NetworkSender sender){
        this.sender = sender;
        this.currSeqNumber = 0;
    }

    public void transmit(List<BISYNCPacket> packets) throws IOException {
        for (int i = 0; i < packets.size(); i++) {
            BISYNCPacket packet = packets.get(i);
            boolean packetAcknowledged = false;
            boolean isLastPacket = (i == packets.size() - 1);

            while (!packetAcknowledged) { // keep retrying until ACK
                System.out.println("Sending packet " + currSeqNumber);
                sender.sendPacketWithError(packet, currSeqNumber, isLastPacket);

                char[] response = sender.waitForResponse();

                if (response != null && response.length >= 2) { // Validate response
                    if (response[0] == ACK) {
                        System.out.println("Received ACK for packet " + currSeqNumber);
                        currSeqNumber = response[1]; // update seq number
                        packetAcknowledged = true;
                    } else if (response[0] == NAK) {
                        System.out.println("Received NAK for packet " + currSeqNumber + ", resending...");
                        // resend same packet, don't increment i
                    } else {
                        System.out.println("Unknown response, resending packet " + currSeqNumber + "...");
                        // treat as NAK, resend
                    }
                } else {
                    System.out.println("No response or invalid response, resending packet " + currSeqNumber + "...");
                    // treat as NAK, resend
                }
            }
        }
        System.out.println("All packets sent and acknowledged!");
    }
}