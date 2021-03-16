package ca.ubc.cs.cs317.dnslookup;

public class RRConstructionOutcome {
    private ResourceRecord rr;
    private int newOffset;
    private int rtype;

    public ResourceRecord getRr() {
        return rr;
    }

    public void setRr(ResourceRecord rr) {
        this.rr=rr;
    }

    public int getNewOffset() {
        return newOffset;
    }

    public void setNewOffset(int newOffset) {
        this.newOffset=newOffset;
    }

    public int getRtype() {
        return rtype;
    }

    public void setRtype(int rtype) {
        this.rtype=rtype;
    }

    public RRConstructionOutcome(ResourceRecord rr,int newOffset,int rtype) {
        this.rr=rr;
        this.newOffset=newOffset;
        this.rtype=rtype;
    }
}
