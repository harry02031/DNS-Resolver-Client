package ca.ubc.cs.cs317.dnslookup;

import java.net.InetAddress;

public class SingleQueryResult {

    private String nexthosttolookup; //might be dns server host name with no additional info in which case we need to switch gear.
    private InetAddress nextserverIPaddress;
    private boolean isAA;

    public SingleQueryResult() {
    }

    public String getNexthosttolookup() {

        return nexthosttolookup;
    }

    public void setNexthosttolookup(String nexthosttolookup) {
        this.nexthosttolookup = nexthosttolookup;
    }

    public InetAddress getNextserverIPaddress() {
        return nextserverIPaddress;
    }

    public void setNextserverIPaddress(InetAddress nextserverIPaddress) {
        this.nextserverIPaddress = nextserverIPaddress;
    }

    public boolean isAA() {
        return isAA;
    }

    public void setAA(boolean AA) {
        isAA = AA;
    }


}