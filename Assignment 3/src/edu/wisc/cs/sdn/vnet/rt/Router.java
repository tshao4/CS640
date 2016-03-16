package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import net.floodlightcontroller.packet.*;

import java.util.Arrays;

/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class Router extends Device
{	
	/** Routing table for the router */
	private RouteTable routeTable;
	
	/** ARP cache for the router */
	private ArpCache arpCache;

	private final boolean debug = true;

	private final int TIME_EXCEEDED = 0;
	private final int DEST_NET_UNREACHABLE = 1;
	private final int DEST_HOST_UNREACHABLE = 2;
	private final int DEST_PORT_UNREACHABLE = 3;
	private final int ICMP_ECHO_REPLY = 4;
	
	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	public Router(String host, DumpFile logfile)
	{
		super(host,logfile);
		this.routeTable = new RouteTable();
		this.arpCache = new ArpCache();
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
		// Ignore all other packet types, for now
		}
		
		/********************************************************************/
	}
	
	private void handleIpPacket(Ethernet etherPacket, Iface inIface)
	{
		final Ethernet originalEtherPacket = (Ethernet) etherPacket.clone();
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
        		if (IPv4.PROTOCOL_ICMP == ipPacket.getProtocol())
        		{

        		}
        		else 
        		{
        			if (debug) System.out.println("DEST_PORT_UNREACHABLE");
        			sendICMP(DEST_PORT_UNREACHABLE, etherPacket, inIface);
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
        	if (debug) System.out.println("DEST_HOST_UNREACHABLE");
        	sendICMP(DEST_HOST_UNREACHABLE, etherPacket, inIface);
        	return; 
        }
        etherPacket.setDestinationMACAddress(arpEntry.getMac().toBytes());
        
        this.sendPacket(etherPacket, outIface);
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
        	return;   }
        Iface outIface = bestMatch.getInterface();

        int nextHop = bestMatch.getGatewayAddress();
        if (0 == nextHop)
        { nextHop = srcAddr; }

        ArpEntry arpEntry = this.arpCache.lookup(nextHop);
        if (null == arpEntry)
        {  	
        	if (debug) System.out.println("arp miss");
        	return;   }

		ether.setEtherType(Ethernet.TYPE_IPv4);
		ether.setSourceMACAddress(outIface.getMacAddress().toBytes());
		ether.setDestinationMACAddress(arpEntry.getMac().toBytes());

		ip.setTtl((byte)64);
		ip.setProtocol(IPv4.PROTOCOL_ICMP);
		ip.setSourceAddress(inIface.getIpAddress());
		ip.setDestinationAddress(ipPacket.getSourceAddress());

		byte[] ipHP = ipPacket.serialize();
		int ipHLength = (int)ipPacket.getHeaderLength() * 4 + 8;

		byte[] iData = new byte[4 + ipHLength];

		for (int i = 4; i < ipHLength; i++) {
			iData[i] = ipHP[i - 4];
		}

		data.setData(iData);

		switch(type) {
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
			default:
				break;
		}

		ether.setPayload(ip);
		ip.setPayload(icmp);
		icmp.setPayload(data);

		if (debug) System.out.println("Send ICMP");
		this.sendPacket(ether, outIface);
	}
}
