package ca.ubc.cs.cs317.dnslookup;

public class PureLabelsDecoder {
    // Parse the buffer index which contains the domain name and converts it to ASCII characters.
    // Finally it returns the domain name as a string
    static String decodingQname(final byte[] buffer,int actualsize) {

        StringBuilder output=new StringBuilder();

        int size=actualsize;
        int index=0;

        while (size > 0) {
            int numOfCharsIntheCurrentLabel= (buffer[index] & 0xff);

            for (int i=index + 1; i <= numOfCharsIntheCurrentLabel + index; i++) {
                output.append(ComputationHelper.asciiconvert(buffer,i));
            }

            output.append(".");

            size-=(numOfCharsIntheCurrentLabel + 1);

            index+=(numOfCharsIntheCurrentLabel + 1);
        }

        // delete "."
        if(actualsize > 1)
            output.deleteCharAt(actualsize - 1);

        return output.toString();
    }
}