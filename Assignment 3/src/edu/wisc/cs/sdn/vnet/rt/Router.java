package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import net.floodlightcontroller.packet.*;

import java.util.*;
import java.util.concurrent.*;
import java.nio.ByteBuffer;
/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class Router extends Device
{	
	/** Routing table for the router */
	private RouteTable routeTable;
	
	/** ARP cache for the router */
	private ArpCache arpCache;

	private Map<Integer, List<Ethernet>> arpQueues;

	private final boolean debug = true;

	private final int TIME_EXCEEDED = 0;
	private final int DEST_NET_UNREACHABLE = 1;
	private final int DEST_HOST_UNREACHABLE = 2;
	private final int DEST_PORT_UNREACHABLE = 3;
	private final int ICMP_ECHO_REPLY = 4;

	private final int ARP_REQUEST = 0;
	private final int ARP_REPLY = 1;
	
	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	public Router(String host, DumpFile logfile)
	{
		super(host,logfile);
		this.routeTable = new RouteTable();
		this.arpCache = new ArpCache();
		this.arpQueues = new ConcurrentHashMap<Integer, List<Ethernet>>();
	}
	
	/**
	 * @return routing table for the router
	 */
	public RouteTable getRouteTable()
	{ return this.routeTable; }
	
	/**
	 * Load a new routing table from a file.
	 * @param routeTableFile the name of the file containing the routing table
	 */
	public void loadRouteTable(String routeTableFile)
	{
		if (!routeTable.load(routeTableFile, this))
		{
			System.err.println("Error setting up routing table from file "
					+ routeTableFile);
			System.exit(1);
		}
		
		System.out.println("Loaded static route table");
		System.out.println("-------------------------------------------------");
		System.out.print(this.routeTable.toString());
		System.out.println("-------------------------------------------------");
	}
	
	/**
	 * Load a new ARP cache from a file.
	 * @param arpCacheFile the name of the file containing the ARP cache
	 */
	public void loadArpCache(String arpCacheFile)
	{
		if (!arpCache.load(arpCacheFile))
		{
			System.err.println("Error setting up ARP cache from file "
					+ arpCacheFile);
			System.exit(1);
		}
		
		System.out.println("Loaded static ARP cache");
		System.out.println("----------------------------------");
		System.out.print(this.arpCache.toString());
		System.out.println("----------------------------------");
	}

	/**
	 * Handle an Ethernet packet received on a specific interface.
	 * @param etherPacket the Ethernet packet that was received
	 * @param inIface the interface on which the packet was received
	 */
	public void handlePacket(Ethernet etherPacket, Iface inIface)
	{
		System.out.println("*** -> Received packet: " +
                etherPacket.toString().replace("\n", "\n\t"));
		
		/********************************************************************/
		/* TODO: Handle packets                                             */
		
		switch(etherPacket.getEtherType())
		{
			case Ethernet.TYPE_IPv4:
				this.handleIpPacket(etherPacket, inIface);
				break;
			case Ethernet.TYPE_ARP:
				this.handleArpPacket(etherPacket, inIface);
		// Ignore all other packet types, for now
		}
		
		/********************************************************************/
	}

	private void handleArpPacket(Ethernet etherPacket, Iface inIface)
	{
		ARP arpPacket = (ARP)etherPacket.getPayload();
		int targetIp = ByteBuffer.wrap(arpPacket.getTargetProtocolAddress()).getInt();

		for (Iface iface : this.interfaces.values())
        {
        	if (targetIp == iface.getIpAddress()) 
        	{
        		if (ARP.OP_REQUEST == arpPacket.getOpCode()) 
        		{
        			if (debug) System.out.println("ArpRequest received");
        			sendArp(0, ARP_REPLY, etherPacket, inIface, inIface);
        			break;
        		}
        		else if (ARP.OP_REPLY == arpPacket.getOpCode()) 
        		{
        			if (debug) System.out.println("ArpReply received");

        			MACAddress mac = MACAddress.valueOf(arpPacket.getSenderHardwareAddress());
        			int ip = ByteBuffer.wrap(arpPacket.getSenderProtocolAddress()).getInt();
        			arpCache.insert(mac, ip);
        			List<Ethernet> queue = arpQueues.remove(ip);
        			if (queue != null) 
        			{
	        			for (Ethernet ether : queue) 
	        			{
	        				ether.setDestinationMACAddress(mac.toBytes());
	        				sendPacket(ether, inIface);
	        			}
	        		}
        		}
        	}
    	}
	}

	private void sendArp(int ip, int type, Ethernet etherPacket, Iface inIface, Iface outIface)
	{
		Ethernet ether = new Ethernet();
		ARP arp = new ARP();

		ether.setEtherType(Ethernet.TYPE_ARP);
		ether.setSourceMACAddress(inIface.getMacAddress().toBytes());

		arp.setHardwareType(ARP.HW_TYPE_ETHERNET);
		arp.setProtocolType(ARP.PROTO_TYPE_IP);
		arp.setHardwareAddressLength((byte)Ethernet.DATALAYER_ADDRESS_LENGTH);
		arp.setProtocolAddressLength((byte)4);
		arp.setSenderHardwareAddress(inIface.getMacAddress().toBytes());
		arp.setSenderProtocolAddress(inIface.getIpAddress());

		switch(type) 
		{
			case ARP_REQUEST:
				ether.setDestinationMACAddress("ff:ff:ff:ff:ff:ff");
				arp.setOpCode(ARP.OP_REQUEST);
				arp.setTargetHardwareAddress(Ethernet.toMACAddress("00:00:00:00:00:00"));
				arp.setTargetProtocolAddress(ip);
				break;
			case ARP_REPLY:
				ARP arpPacket = (ARP)etherPacket.getPayload();
				ether.setDestinationMACAddress(etherPacket.getSourceMACAddress());
				arp.setOpCode(ARP.OP_REPLY);
				arp.setTargetHardwareAddress(arpPacket.getSenderHardwareAddress());
				arp.setTargetProtocolAddress(arpPacket.getSenderProtocolAddress());
				break;
			default:
				return;
		}

		ether.setPayload(arp);

		if (debug) System.out.println("Send ARP Packet");
		this.sendPacket(ether, outIface);
	}
	
	private void handleIpPacket(Ethernet etherPacket, Iface inIface)
	{
		// Make sure it's an IP packet
		if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4)
		{ return; }
		
		// Get IP header
		IPv4 ipPacket = (IPv4)etherPacket.getPayload();
        System.out.println("Handle IP packet");

        // Verify checksum
        short origCksum = ipPacket.getChecksum();
        ipPacket.resetChecksum();
        byte[] serialized = ipPacket.serialize();
        ipPacket.deserialize(serialized, 0, serialized.length);
        short calcCksum = ipPacket.getChecksum();
        if (origCksum != calcCksum)
        { return; }
        
        // Check TTL
        ipPacket.setTtl((byte)(ipPacket.getTtl()-1));
        if (0 == ipPacket.getTtl())
        { 
        	if (debug) System.out.println("TIME_EXCEEDED");
        	sendICMP(TIME_EXCEEDED, etherPacket, inIface);
        	return; 
        }
        
        // Reset checksum now that TTL is decremented
        ipPacket.resetChecksum();
        
        // Check if packet is destined for one of router's interfaces
        for (Iface iface : this.interfaces.values())
        {
        	if (ipPacket.getDestinationAddress() == iface.getIpAddress()) 
        	{
				byte protocol = ipPacket.getProtocol();

                if(protocol == IPv4.PROTOCOL_TCP || protocol == IPv4.PROTOCOL_UDP) 
                {
                	if (debug) System.out.println("DEST_PORT_UNREACHABLE");
                    sendICMP(DEST_PORT_UNREACHABLE ,etherPacket, inIface);
                } 
                else if (protocol == IPv4.PROTOCOL_ICMP) 
                {
                    ICMP icmpPacket = (ICMP) ipPacket.getPayload();

                    if(icmpPacket.getIcmpType() == ICMP.TYPE_ECHO_REQUEST) 
                    {
                    	if (debug) System.out.println("ICMP_ECHO_REPLY");
                        sendICMP(ICMP_ECHO_REPLY ,etherPacket, inIface);
                    }
                }
                return;
            }
        }
		
        // Do route lookup and forward
        this.forwardIpPacket(etherPacket, inIface);
	}

    private void forwardIpPacket(Ethernet etherPacket, Iface inIface)
    {
        // Make sure it's an IP packet
		if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4)
		{ return; }
        System.out.println("Forward IP packet");
		
		// Get IP header
		IPv4 ipPacket = (IPv4)etherPacket.getPayload();
        int dstAddr = ipPacket.getDestinationAddress();

        // Find matching route table entry 
        RouteEntry bestMatch = this.routeTable.lookup(dstAddr);

        // If no entry matched, do nothing
        if (null == bestMatch)
        { 
        	if (debug) System.out.println("DEST_NET_UNREACHABLE");
        	sendICMP(DEST_NET_UNREACHABLE, etherPacket, inIface);
        	return; 
        }

        // Make sure we don't sent a packet back out the interface it came in
        Iface outIface = bestMatch.getInterface();
        if (outIface == inIface)
        { return; }

        // Set source MAC address in Ethernet header
        etherPacket.setSourceMACAddress(outIface.getMacAddress().toBytes());

        // If no gateway, then nextHop is IP destination
        int nextHop = bestMatch.getGatewayAddress();
        if (0 == nextHop)
        { nextHop = dstAddr; }

        // Set destination MAC address in Ethernet header
        ArpEntry arpEntry = this.arpCache.lookup(nextHop);
        if (null == arpEntry)
        { 
        	if (debug) System.out.println("arp miss ip");
        	handleArpMiss(nextHop, etherPacket, inIface, outIface);
        	return; 
        }
        etherPacket.setDestinationMACAddress(arpEntry.getMac().toBytes());
        
        this.sendPacket(etherPacket, outIface);
    }

    private void handleArpMiss(final int ip, final Ethernet etherPacket, final Iface inIface, final Iface outIface) 
    {
    	IPv4 ipPacket = (IPv4)etherPacket.getPayload();
    	final Integer dstAddr = new Integer(ipPacket.getDestinationAddress());
    	if (arpQueues.containsKey(dstAddr))
    	{
    		List<Ethernet> queue = arpQueues.get(dstAddr);
    		queue.add(etherPacket);
    		arpQueues.put(dstAddr, queue);
    	}
    	else 
    	{
    		List<Ethernet> queue = new ArrayList<Ethernet>();
    		queue.add(etherPacket);
    		arpQueues.put(dstAddr, queue);
    		TimerTask task = new TimerTask()
	    	{
	    		int counter = 0;
	    		public void run()
	    		{
	    			if (!arpQueues.containsKey(dstAddr)) 
	    			{ 
	    				this.cancel(); 
	    			}
	    			else 
	    			{
	    				if (counter > 2) 
		    			{
		    				if (debug) System.out.println("TimeOut");
		    				arpQueues.remove(dstAddr);
		    				sendICMP(DEST_HOST_UNREACHABLE, etherPacket, inIface);
		    				this.cancel();
		    			} 
		    			else 
		    			{
		    				if (debug) System.out.println("Timer  " + counter);
		    				sendArp(ip, ARP_REQUEST, etherPacket, inIface, outIface);
		    				counter++;
		    			}
	    			}
	    		}
	    	};
	    	Timer timer = new Timer(true);
	    	timer.schedule(task, 0, 1000);
	    }
    }

    private void sendICMP(int type, Ethernet etherPacket, Iface inIface)
	{
		Ethernet ether = new Ethernet();
		IPv4 ip = new IPv4();
		ICMP icmp = new ICMP();
		Data data = new Data();


		IPv4 ipPacket = (IPv4)etherPacket.getPayload();
        int srcAddr = ipPacket.getSourceAddress();
        RouteEntry bestMatch = this.routeTable.lookup(srcAddr);
        if (null == bestMatch)
        {  	
        	if (debug) System.out.println("No best match");
        	return;   
        }

        int nextHop = bestMatch.getGatewayAddress();
        if (0 == nextHop)
        { nextHop = srcAddr; }

        ArpEntry arpEntry = this.arpCache.lookup(nextHop);
        if (null == arpEntry)
        {  	
        	if (debug) System.out.println("arp miss icmp");
        	handleArpMiss(nextHop, etherPacket, inIface, inIface);
        	return;   
        }

		ether.setEtherType(Ethernet.TYPE_IPv4);
		ether.setSourceMACAddress(inIface.getMacAddress().toBytes());
		ether.setDestinationMACAddress(arpEntry.getMac().toBytes());

		ip.setTtl((byte)64);
		ip.setProtocol(IPv4.PROTOCOL_ICMP);
		ip.setDestinationAddress(ipPacket.getSourceAddress());

		byte[] iData;

		if (ICMP_ECHO_REPLY != type) 
		{
			ip.setSourceAddress(inIface.getIpAddress());

			byte[] ipHP = ipPacket.serialize();
			int ipHLength = ipPacket.getHeaderLength() * 4;

			iData = new byte[4 + ipHLength + 8];

			Arrays.fill(iData, 0, 4, (byte)0);

			for (int i = 0; i < ipHLength + 8; i++) 
			{ iData[i + 4] = ipHP[i]; }
		}
		else
		{ 
			ip.setSourceAddress(ipPacket.getDestinationAddress());
			iData = ((ICMP)ipPacket.getPayload()).getPayload().serialize();
		}
		

		switch(type) 
		{
			case TIME_EXCEEDED:
				icmp.setIcmpType((byte)11);
				icmp.setIcmpCode((byte)0);
				break;
			case DEST_NET_UNREACHABLE:
				icmp.setIcmpType((byte)3);
				icmp.setIcmpCode((byte)0);
				break;
			case DEST_HOST_UNREACHABLE:
				icmp.setIcmpType((byte)3);
				icmp.setIcmpCode((byte)1);
				break;
			case DEST_PORT_UNREACHABLE:
				icmp.setIcmpType((byte)3);
				icmp.setIcmpCode((byte)3);
				break;
			case ICMP_ECHO_REPLY:
				icmp.setIcmpType((byte)0);
				icmp.setIcmpCode((byte)0);
				break;
			default:
				return;
		}

		data.setData(iData);
		ether.setPayload(ip);
		ip.setPayload(icmp);
		icmp.setPayload(data);

		if (debug) System.out.println("Send ICMP");
		this.sendPacket(ether, inIface);
	}
}
