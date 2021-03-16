package ca.ubc.cs.cs317.dnslookup;

public class ComputationHelper {
    // returns the equivalent ASCII character to buffer[index]
    public static char asciiconvert(final byte[] buffer,int index) {
        return (char) (buffer[index] & 0xff);
    }

    // returns the int value by combining two bytes of data of buf[index1] and buf[index2] in this particular order
    public static int twoOctetToInt(int index1,int index2,final byte[] buf) {
        return ((buf[index1] & 0xff) << 8)
                | (buf[index2] & 0xff);
    }

    // returns the int value by combining four bytes of data of buf[index1] and buf[index2]
    // and buf[index3] and buf[index4] in this particular order
    public static int fourOctetToLong(int index1,int index2,int index3,int index4,final byte[] buf) {
        return (buf[index1] & 0xff) << 24
                | (buf[index2] & 0xff) << 16
                | (buf[index3] & 0xff) << 8
                | (buf[index4] & 0xff);
    }
}
