package ca.ubc.cs.cs317.dnslookup;

import java.io.ByteArrayOutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;

public class QueryPacketBuilder {
    //This function constructs the entire DNS query message and writes it to ByteArrayOutPutStream buf
    // and converts buf to a byte array queryBytes
    // Finally, the function returns a datagram packet with the following values: the byte array queryBytes, 0 offset,
    // the length of QueryBytes, the server and the defaultDnsport
    static DatagramPacket formRequestPacketFromNode(int queryID,DNSNode node,InetAddress server,ByteArrayOutputStream buf,int defaultDnsPort) {
        // SET HEADER

        // Use random to generate query ID
        // covert integer queryID to bytes, in big endian

        byte firstByte=(byte) ((queryID & 0xff00) >>> 8);
        buf.write(firstByte);

        byte secondByte=(byte) queryID;
        buf.write(secondByte);

        // |QR|   Opcode  |AA|TC|RD| byte setup
        // QR bit will be 0 since we are sending the query
        // opcode is 0 since we are sending standard query
        // AA is zero
        // TC and RD are both zero as per the assignment
        buf.write(0x0);

        // |RA|   Z    |   RCODE   | byte setup
        buf.write(0x0);

        buf.write(0x0);
        buf.write(0x1);

        // remaining fields setup in header part
        //|                    ANCOUNT                    |
        //+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
        //|                    NSCOUNT                    |
        //+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
        //|                    ARCOUNT                    |
        buf.write(0x0);
        buf.write(0x0);
        buf.write(0x0);
        buf.write(0x0);
        buf.write(0x0);
        buf.write(0x0);

        //SET question QNAME
        String hostName=node.getHostName();
        String[] labels=hostName.split("\\.");
        for (String a : labels) {
            if(a.length() > 0){
                buf.write(a.length());
                char[] chars=a.toCharArray();
                for (char ch : chars)
                    buf.write(ch);
            }
        }
        buf.write(0x0);

        //SET QTYPE
        buf.write(0x0);
        if (node.getType() == RecordType.A)
            buf.write(0x1);
        else
            //28 is specified in RFC 3596
            buf.write(28);

        //SET QCLASS
        buf.write(0x0);
        buf.write(0x1);

        byte[] queryBytes=buf.toByteArray();
        return new DatagramPacket(queryBytes,0,queryBytes.length,server,defaultDnsPort);
    }
}