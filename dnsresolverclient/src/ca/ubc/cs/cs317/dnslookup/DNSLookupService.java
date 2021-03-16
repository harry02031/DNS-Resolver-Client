package ca.ubc.cs.cs317.dnslookup;


import java.io.ByteArrayOutputStream;
import java.io.Console;
import java.io.IOException;
import java.net.*;
import java.util.*;


public class DNSLookupService {

    private static final int DEFAULT_DNS_PORT=53;
    private static final int MAX_INDIRECTION_LEVEL=10;

    private static InetAddress rootServer;
    private static boolean verboseTracing=false;
    private static DatagramSocket socket;

    private static DNSCache cache=DNSCache.getInstance();

    private static Random random=new Random();

    /**
     * Main function, called when program is first invoked.
     *
     * @param args list of arguments specified in the command line.
     */
    public static void main(String[] args) throws IOException {

        if (args.length != 1) {
            System.err.println("Invalid call. Usage:");
            System.err.println("\tjava -jar DNSLookupService.jar rootServer");
            System.err.println("where rootServer is the IP address (in dotted form) of the root DNS server to start the search at.");
            System.exit(1);
        }

        try {
            rootServer=InetAddress.getByName(args[0]);
            System.out.println("Root DNS server is: " + rootServer.getHostAddress());
        } catch (UnknownHostException e) {
            System.err.println("Invalid root server (" + e.getMessage() + ").");
            System.exit(1);
        }

        try {
            socket=new DatagramSocket();
            socket.setSoTimeout(5000);
        } catch (SocketException ex) {
            ex.printStackTrace();
            System.exit(1);
        }

        Scanner in=new Scanner(System.in);
        Console console=System.console();
        do {
            // Use console if one is available, or standard input if not.
            String commandLine;
            if (console != null) {
                System.out.print("DNSLOOKUP> ");
                commandLine=console.readLine();
            } else
                try {
                    commandLine=in.nextLine();
                } catch (NoSuchElementException ex) {
                    break;
                }
            // If reached end-of-file, leave
            if (commandLine == null) break;

            // Ignore leading/trailing spaces and anything beyond a comment character
            commandLine=commandLine.trim().split("#",2)[0];

            // If no command shown, skip to next command
            if (commandLine.trim().isEmpty()) continue;

            String[] commandArgs=commandLine.split(" ");

            if (commandArgs[0].equalsIgnoreCase("quit") ||
                    commandArgs[0].equalsIgnoreCase("exit"))
                break;
            else if (commandArgs[0].equalsIgnoreCase("server")) {
                // SERVER: Change root nameserver
                if (commandArgs.length == 2) {
                    try {
                        rootServer=InetAddress.getByName(commandArgs[1]);
                        System.out.println("Root DNS server is now: " + rootServer.getHostAddress());
                    } catch (UnknownHostException e) {
                        System.out.println("Invalid root server (" + e.getMessage() + ").");
                        continue;
                    }
                } else {
                    System.out.println("Invalid call. Format:\n\tserver IP");
                    continue;
                }
            } else if (commandArgs[0].equalsIgnoreCase("trace")) {
                // TRACE: Turn trace setting on or off
                if (commandArgs.length == 2) {
                    if (commandArgs[1].equalsIgnoreCase("on"))
                        verboseTracing=true;
                    else if (commandArgs[1].equalsIgnoreCase("off"))
                        verboseTracing=false;
                    else {
                        System.err.println("Invalid call. Format:\n\ttrace on|off");
                        continue;
                    }
                    System.out.println("Verbose tracing is now: " + (verboseTracing ? "ON" : "OFF"));
                } else {
                    System.err.println("Invalid call. Format:\n\ttrace on|off");
                    continue;
                }
            } else if (commandArgs[0].equalsIgnoreCase("lookup") ||
                    commandArgs[0].equalsIgnoreCase("l")) {
                // LOOKUP: Find and print all results associated to a name.
                RecordType type;
                if (commandArgs.length == 2)
                    type=RecordType.A;
                else if (commandArgs.length == 3)
                    try {
                        type=RecordType.valueOf(commandArgs[2].toUpperCase());
                    } catch (IllegalArgumentException ex) {
                        System.err.println("Invalid query type. Must be one of:\n\tA, AAAA, NS, MX, CNAME");
                        continue;
                    }
                else {
                    System.err.println("Invalid call. Format:\n\tlookup hostName [type]");
                    continue;
                }
                findAndPrintResults(commandArgs[1],type);
            } else if (commandArgs[0].equalsIgnoreCase("dump")) {
                // DUMP: Print all results still cached
                cache.forEachNode(DNSLookupService::printResults);
            } else {
                System.err.println("Invalid command. Valid commands are:");
                System.err.println("\tlookup fqdn [type]");
                System.err.println("\ttrace on|off");
                System.err.println("\tserver IP");
                System.err.println("\tdump");
                System.err.println("\tquit");
                continue;
            }

        } while (true);

        socket.close();
        System.out.println("Goodbye!");
    }

    /**
     * Finds all results for a host name and type and prints them on the standard output.
     *
     * @param hostName Fully qualified domain name of the host being searched.
     * @param type     Record type for search.
     */
    private static void findAndPrintResults(String hostName,RecordType type) throws IOException {

        DNSNode node=new DNSNode(hostName,type);
        try {
            printResults(node,getResults(node,0));
        } catch (TimeoutTwiceException e) {
            printResults(node,Collections.emptySet());
        }
    }

    /**
     * Finds all the result for a specific node.
     *
     * @param node             Host and record type to be used for search.
     * @param indirectionLevel Control to limit the number of recursive calls due to CNAME redirection.
     *                         The initial call should be made with 0 (zero), while recursive calls for
     *                         regarding CNAME results should increment this value by 1. Once this value
     *                         reaches MAX_INDIRECTION_LEVEL, the function prints an error message and
     *                         returns an empty set.
     * @return A set of resource records corresponding to the specific query requested.
     */
    private static Set<ResourceRecord> getResults(DNSNode node,int indirectionLevel) throws IOException {

        if (indirectionLevel > MAX_INDIRECTION_LEVEL) {
            System.err.println("Maximum number of indirection levels reached.");
            return Collections.emptySet();
        }

        // Memoization: first look up cache with the specified type
        Set<ResourceRecord> wantedResourceRecords=cache.getCachedResults(node);

        // If the set is non empty means we have answers of this query, return it
        if (!wantedResourceRecords.isEmpty()) {
            return wantedResourceRecords;
        }

        // Perform a DNS lookup
        retrieveResultsFromServer(node,rootServer);

        // Get result again
        wantedResourceRecords=cache.getCachedResults(node);

        // If it is still empty, it might be : 1. we found a CNAME answer in the previous retrieveResultsFromServer
        // 2. we found nothing, we cannot resolve it(return empty).
        if (wantedResourceRecords.isEmpty()) {

            // (we continue a lookup by making a recursive call on CNAME's hostname but initial query type i.e. A/AAAA)

            // First make a new query node with initial hostname with CNAME RR type
            DNSNode sameHostNameButCNAMETypeQuery=new DNSNode(node.getHostName(),RecordType.CNAME);

            // Lookup this new node in cache
            Set<ResourceRecord> sameHostNameButCNAMETypeQueryRRs=cache.getCachedResults(sameHostNameButCNAMETypeQuery);

            // For all the alias names of the original hostname we want to look up to
            for (ResourceRecord CNAMEresourceRecord : sameHostNameButCNAMETypeQueryRRs) {

                // Make a CNAMEHostNameButTargetType node, do it again, attach results to the node that we are really interested in.
                DNSNode CNAMEHostNameButTargetType=new DNSNode(CNAMEresourceRecord.getTextResult(),node.getType());

                // But this CNAMEHostNameButTargetType may be queried before as well!! If it is empty we do a recursive call otherwise we can use the cached results.
                Set<ResourceRecord> CNAMEHostNameButTargetTypeRRs=cache.getCachedResults(CNAMEHostNameButTargetType).isEmpty() ? getResults(CNAMEHostNameButTargetType,indirectionLevel + 1)
                        : cache.getCachedResults(CNAMEHostNameButTargetType);

                // Append new results of alias, to the original host name
                for (ResourceRecord CNAMEHostNameButTargetTypeRR : CNAMEHostNameButTargetTypeRRs) {
                    ResourceRecord newRR=new ResourceRecord(node.getHostName(),node.getType(),CNAMEHostNameButTargetTypeRR.getTTL(),CNAMEHostNameButTargetTypeRR.getInetResult());
                    cache.addResult(newRR);
                }
            }

            // Fetch again
            wantedResourceRecords=cache.getCachedResults(node);
        }
        return wantedResourceRecords;

    }

    /**
     * Retrieves DNS results from a specified DNS server. Queries are sent in iterative mode,
     * and the query is repeated with a new server if the provided one is non-authoritative.
     * Results are stored in the cache.
     *
     * @param node   Host name and record type to be used for the query.
     * @param server Address of the server to be used for the query.
     */
    private static void retrieveResultsFromServer(DNSNode node,InetAddress server) throws IOException {
        // Send a data packet to server, on port 53
        while (true) {

            // Timeout 5 second
            socket.setSoTimeout(5000);

            // UDP
            DatagramPacket receivedPacket=getQueryResult(node,server);

            // Parse the result and get hint on what to do next.
            // We abstract the hints in a new class SingleQueryResult
            SingleQueryResult parseResult=parseAndCacheResult(node,receivedPacket);

            // If it is a AA, we finish since we find some "answers" although they might be CNAME.
            if (parseResult.isAA()) {
                return;
            } else {
                // else we still need to do it.
                String nextHostToLookup=parseResult.getNexthosttolookup();
                // Process ".ca" case
                if (nextHostToLookup == null) break;
                if (!nextHostToLookup.equalsIgnoreCase(node.getHostName())) {
                    // in this branch, nextHostToLookup must be a name server's hostname which we don't know it IP, thus
                    // we need to make a recursive call to find its IP, once we found this IP, we can go back to previous
                    // query
                    if (cache.getCachedResults(new DNSNode(nextHostToLookup,RecordType.A)).isEmpty()) {
                        retrieveResultsFromServer(new DNSNode(nextHostToLookup,RecordType.A),rootServer);
                    }
                    server=getATypeIPAddressByHostNameFromCache(nextHostToLookup);
                    assert server != null;
                } else {
                    // no need to switch gear
                    server=parseResult.getNextserverIPaddress();
                }
            }
        }
    }

    // Helper, find a valid IP of name server.
    private static InetAddress getATypeIPAddressByHostNameFromCache(String nextHostToLookup) {
        Set<ResourceRecord> resourceRecords=cache.getCachedResults(new DNSNode(nextHostToLookup,RecordType.A));
        for (ResourceRecord rr : resourceRecords) {
            return rr.getInetResult();
        }
        return null;
    }


    // Encode a packet, send it and receive the response packet.
    private static DatagramPacket getQueryResult(DNSNode node,InetAddress server) {

        int queryID, resID;
        DatagramPacket respPacket;
        int timeOutOccur=0;
        do {
            // Try to send query to server, it returns a int value: queryID
            try {
                queryID=sendQuery(node,server);
            } catch (IOException e) {
                throw new OtherIOException();
            }

            // response buffer
            byte[] resbuf1024=new byte[0x400];
            respPacket=new DatagramPacket(resbuf1024,resbuf1024.length);

            // receive
            try {
                socket.receive(respPacket);
            } catch (SocketTimeoutException e) {
                timeOutOccur++;
                if (timeOutOccur == 2) {
                    // If it timeout twice, we throw a customized exception
                    throw new TimeoutTwiceException();
                }
            } catch (IOException e) {
                throw new OtherIOException();
            }

            //extrace responseID to be compared with sent packet's queryID since UDP doesn't promise they can be the same
            byte[] data=respPacket.getData();
            // check if identifier is the same
            resID=(data.length == 0 || data == null) ? -1 : ComputationHelper.twoOctetToInt(0,1,data);
        } while (queryID != resID);

        System.out.print("Response ID: " + resID + " " + "Authoritative = ");

        return respPacket;
    }

    private static int sendQuery(DNSNode node,InetAddress server) throws IOException {
        ByteArrayOutputStream bufBuilder=new ByteArrayOutputStream();

        int queryID=random.nextInt(0xffff) + 1;

        // ENCODE
        DatagramPacket packet=QueryPacketBuilder.formRequestPacketFromNode(queryID,node,server,bufBuilder,DEFAULT_DNS_PORT);
        socket.send(packet);

        System.out.println();
        System.out.println();
        System.out.println("Query ID     " + queryID + " " + node.getHostName() + "  " + node.getType().toString() + " " + "--> " + server.getHostAddress());
        return queryID;
    }


    static SingleQueryResult parseAndCacheResult(DNSNode node,DatagramPacket receivedResponsePacket) throws UnknownHostException {
        final byte[] data=receivedResponsePacket.getData();
        // parse response query ID, is authoritative or not, and print these in one line like :
        // e.g. Response ID: 54836 Authoritative = false

        int AA=(data[2] & 0x4) >>> 2;
        System.out.print(AA == 1 ? "true\n" : "false\n");

        int answerRRCount=ComputationHelper.twoOctetToInt(6,7,data);
        int authoritativeRRCount=ComputationHelper.twoOctetToInt(8,9,data);
        int additionalRRCount=ComputationHelper.twoOctetToInt(10,11,data);

        List<ResourceRecord> answerRRs=new ArrayList<>();
        List<ResourceRecord> authoritativeRRs=new ArrayList<>();
        List<ResourceRecord> addtionalRRs=new ArrayList<>();

        ByteArrayOutputStream bufBuilder=new ByteArrayOutputStream();

        int index;
        for (index=12; index < data.length; index++) {
            if (data[index] == 0x00) {
                break;
            }
            bufBuilder.write(data[index]);
        }

        index+=5;

        String hostname=PureLabelsDecoder.decodingQname(bufBuilder.toByteArray(),bufBuilder.size());

        assert hostname == node.getHostName();

        int startPos=index;


        RRConstructor rrConstructor=new RRConstructor();
        SingleQueryResult singleQueryResult=new SingleQueryResult();

        // Parse and print answers, move startPos accordingly
        startPos=parseRRsOfThreeCategories("Answers",data,answerRRCount,answerRRs,startPos,rrConstructor,AA);

        // Parse and print authoritative rrs, move startPos accordingly
        startPos=parseRRsOfThreeCategories("Nameservers",data,authoritativeRRCount,authoritativeRRs,startPos,rrConstructor,AA);

        // Parse and print additional rrs, move startPos accordingly
        startPos=parseRRsOfThreeCategories("Additional Information",data,additionalRRCount,addtionalRRs,startPos,rrConstructor,AA);


        //Start creating answer;
        //simplest answer, found authorative answer
        if (AA == 1) {
            singleQueryResult.setAA(true);
        } else {
            if (additionalRRCount > 0) {
                // host is the same as before, we only need to switch to another NS IP
                singleQueryResult.setNexthosttolookup(node.getHostName());
                singleQueryResult.setNextserverIPaddress(getAnATypeIPAddressFromAddtionalRRs(addtionalRRs));
                assert singleQueryResult.getNextserverIPaddress() != null;
            } else if (authoritativeRRCount > 0) {
                singleQueryResult.setNexthosttolookup(authoritativeRRs.get(0).getTextResult());
                // nextServerIP becomes rootServer again!
            } else {
                singleQueryResult.setNextserverIPaddress(null);
                singleQueryResult.setNexthosttolookup(null);
            }
        }

        return singleQueryResult;

    }

    private static InetAddress getAnATypeIPAddressFromAddtionalRRs(List<ResourceRecord> addtionalRRs) {
        for (ResourceRecord rr : addtionalRRs) {
            if (rr.getType() == RecordType.A) {
                return rr.getInetResult();
            }
        }
        return null;
    }

    private static int parseRRsOfThreeCategories(String category,byte[] data,int rrCounts,List<ResourceRecord> resourceRecordList,int startPos,RRConstructor rrConstructor,int AA) throws UnknownHostException {
        RRConstructionOutcome outcome;
        if (verboseTracing) {
            System.out.println("  " + category + " (" + rrCounts + ")");
        }
        for (int i=1; i <= rrCounts; i++) {
            outcome=rrConstructor.constructingRR(data,startPos);
            resourceRecordList.add(outcome.getRr());
            if (category.equalsIgnoreCase("Additional Information") || (category.equalsIgnoreCase("Answers") && AA == 1))
                cache.addResult(outcome.getRr());
            if (verboseTracing)
                verbosePrintResourceRecord(outcome.getRr(),outcome.getRtype());
            startPos=outcome.getNewOffset();
        }
        return startPos;
    }

    private static void verbosePrintResourceRecord(ResourceRecord record,int rtype) {
        if (verboseTracing)
            System.out.format("       %-30s %-10d %-4s %s\n",record.getHostName(),
                    record.getTTL(),
                    record.getType() == RecordType.OTHER ? rtype : record.getType(),
                    record.getTextResult());
    }

    /**
     * Prints the result of a DNS query.
     *
     * @param node    Host name and record type used for the query.
     * @param results Set of results to be printed for the node.
     */
    private static void printResults(DNSNode node,Set<ResourceRecord> results) {
        if (results.isEmpty())
            System.out.printf("%-30s %-5s %-8d %s\n",node.getHostName(),
                    node.getType(),-1,"0.0.0.0");
        for (ResourceRecord record : results) {
            System.out.printf("%-30s %-5s %-8d %s\n",node.getHostName(),
                    node.getType(),record.getTTL(),record.getTextResult());
        }
    }
}