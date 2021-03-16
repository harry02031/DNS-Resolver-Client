package ca.ubc.cs.cs317.dnslookup;

import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class RRConstructor {


    private static String getRR_Result(final byte[] buffer,int newOffset,RecordType rt,int rdataLength) {
        if (rt == RecordType.A) {
            assert rdataLength == 4;
            StringBuilder builder=new StringBuilder();
            for (int i=0; i < rdataLength; i++) {
                builder.append(buffer[newOffset + i] & 0xff);
                if (i < rdataLength - 1)
                    builder.append(".");
            }
            return builder.toString();
        } else if (rt == RecordType.AAAA) {
            assert rdataLength == 16;
            StringBuilder builder=new StringBuilder();
            for (int i=0; i < rdataLength; i+=2) {
                builder.append(String.format("%02x",buffer[newOffset + i]));
                builder.append(String.format("%02x",buffer[newOffset + i + 1]));
                if (i < rdataLength - 2)
                    builder.append(":");
            }
            return builder.toString();
        } else {
            return DecodeDomainName(buffer,newOffset);
        }
    }

    static String DecodeDomainName(final byte[] buffer,int index) {
        // a domain name can be encoded in either a sequence of labels terminated by a NULL label, or a pointer,
        // or a sequence of labels followed by a pointer

        // check if the two highest bit is 1 : compression
        if ((buffer[index] & 0xff) >= 0xc0) {
            int decimal=ComputationHelper.twoOctetToInt(index,index + 1,buffer);

            int pointeeoffset=decimal - 0xc000;

            return DecodeDomainName(buffer,pointeeoffset);
        }
        //check if it is sequence of labels followed by a pointer / all labels
        else {
            ByteArrayOutputStream bufBuilder=new ByteArrayOutputStream();
            for (int i=index; i < buffer.length; i++) {

                if ((buffer[i] & 0xff) >= 0xc0) {
                    int decimal=ComputationHelper.twoOctetToInt(i,i + 1,buffer);
                    int pointeeoffset=decimal - 0xc000;
                    return bufBuilder.size() == 0 ? DecodeDomainName(buffer,pointeeoffset)
                            : PureLabelsDecoder.decodingQname(bufBuilder.toByteArray(),bufBuilder.size()) + "." + DecodeDomainName(buffer,pointeeoffset);
                }
                if (buffer[i] == 0x00) {
                    break;
                }

                bufBuilder.write(buffer[i]);
            }

            byte[] Bytearray=bufBuilder.toByteArray();
            return PureLabelsDecoder.decodingQname(Bytearray,bufBuilder.size());
        }
    }

    public RRConstructionOutcome constructingRR(final byte[] buffer,int startPosition) throws UnknownHostException {
        // Offset means where do we start the parsing for RR.

        // Calculate offset increment caused by domain name information
        int newOffset=startPosition;

        boolean terminateWithPointer=false;
        while (buffer[newOffset] != 0x00) {
            if ((buffer[newOffset] & 0xff) < 0xc0) {
                //label
                newOffset++;
            } else {
                //ptr
                newOffset+=2;
                terminateWithPointer=true;
                break;
            }
        }
        if (!terminateWithPointer)
            newOffset++;

        String hostname=DecodeDomainName(buffer,startPosition);

        int rtype=ComputationHelper.twoOctetToInt(newOffset,newOffset + 1,buffer);
        RecordType rt;
        if (rtype == 1) {
            rt=RecordType.A;
        } else if (rtype == 28) {
            rt=RecordType.AAAA;
        } else if (rtype == 2) {
            rt=RecordType.NS;
        } else if (rtype == 5) {
            rt=RecordType.CNAME;
        } else if (rtype == 6) {
            rt=RecordType.SOA;
        } else if (rtype == 15) {
            rt=RecordType.MX;
        } else {
            rt=RecordType.OTHER;
        }
        newOffset+=2;

        //increase 2 for class info
        newOffset+=2;

        int ttl=ComputationHelper.fourOctetToLong(newOffset,newOffset + 1,newOffset + 2,newOffset + 3,buffer);
        newOffset+=4;

        int rdataLength=ComputationHelper.twoOctetToInt(newOffset,newOffset + 1,buffer);
        newOffset+=2;

        String rdata=getRR_Result(buffer,newOffset,rt,rdataLength);
        if(rt == RecordType.SOA || rt == RecordType.MX || rt == RecordType.OTHER)
            rdata = "----";

        newOffset+=rdataLength;

        ResourceRecord parsedRecord=null;

        if (rt == RecordType.A || rt == RecordType.AAAA) {
            parsedRecord=new ResourceRecord(hostname,rt,ttl,InetAddress.getByName(rdata));
        } else {
            parsedRecord=new ResourceRecord(hostname,rt,ttl,rdata);
        }
        return new RRConstructionOutcome(parsedRecord,newOffset,rtype);
    }
}