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
	private Map<Integer, LocalRipEntry> ripMap;

	private final boolean debug_ARP = false;
	private final boolean debug_RIP = false;

	private final int TIME_EXCEEDED = 0;
	private final int DEST_NET_UNREACHABLE = 1;
	private final int DEST_HOST_UNREACHABLE = 2;
	private final int DEST_PORT_UNREACHABLE = 3;
	private final int ICMP_ECHO_REPLY = 4;

	private final int ARP_REQUEST = 0;
	private final int ARP_REPLY = 1;

	private final int RIP_REQUEST = 0;
	private final int RIP_RESPONSE = 1;
	private final int RIP_UNSOL = 2;

	private final String MAC_BROADCAST = "ff:ff:ff:ff:ff:ff";
	private final String MAC_ZERO = "00:00:00:00:00:00";
	private final String IP_RIP_MULTICAST = "224.0.0.9";
	
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
		this.ripMap = new ConcurrentHashMap<Integer, LocalRipEntry>();
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
		/* TODO: Handle packets											 */
		
		switch(etherPacket.getEtherType())
		{
			case Ethernet.TYPE_IPv4:
				IPv4 ip = (IPv4)etherPacket.getPayload();
				if (IPv4.toIPv4Address(IP_RIP_MULTICAST) == ip.getDestinationAddress())
				{
					if (IPv4.PROTOCOL_UDP == ip.getProtocol()) 
					{
						UDP udp = (UDP)ip.getPayload();
						if (UDP.RIP_PORT == udp.getDestinationPort())
						{ 
							RIPv2 rip = (RIPv2)udp.getPayload();
							this.handleRipPacket(rip.getCommand(), etherPacket, inIface);
							break;
						}
					}
				}

				this.handleIpPacket(etherPacket, inIface);
				break;
			case Ethernet.TYPE_ARP:
				this.handleArpPacket(etherPacket, inIface);
		// Ignore all other packet types, for now
		}
		
		/********************************************************************/
	}

	/*******************************************************************************
	***********************				  RIP 				************************
	*******************************************************************************/

	class LocalRipEntry
	{
		protected int addr, mask, nextHop, metric;
		protected long timestamp;
		public LocalRipEntry(int addr, int mask, int nextHop, int metric, long timestamp) {
			this.addr = addr;
			this.mask = mask;
			this.nextHop = nextHop;
			this.metric = metric;
			this.timestamp = timestamp;
		}
	}

	public void ripInit()  
	{
		for (Iface iface : this.interfaces.values())
		{
			int mask = iface.getSubnetMask();
			int netAddr = mask & iface.getIpAddress();
			ripMap.put(netAddr, new LocalRipEntry(netAddr, mask, 0, 0, -1));
			routeTable.insert(netAddr, 0, mask, iface);
			sendRip(RIP_REQUEST, null, iface);
		}

		TimerTask unsolTask = new TimerTask()
		{
			public void run()
			{
				if (debug_RIP) System.out.println("Send unsolicited RIP response");
				for (Iface iface : interfaces.values())
				{ sendRip(RIP_UNSOL, null, iface); }
			}
		};
		TimerTask toTask = new TimerTask()
		{
			public void run()
			{
				for (LocalRipEntry entry : ripMap.values()) {
					if (entry.timestamp != -1 && System.currentTimeMillis() - entry.timestamp >= 30000)
					{	
						if (debug_RIP) System.out.println("Table entry timeout: " + IPv4.fromIPv4Address(entry.addr));
						ripMap.remove(entry.addr & entry.mask);
						routeTable.remove(entry.addr, entry.mask);
					}
				}
			}
		};

		Timer timer = new Timer(true);
		timer.schedule(unsolTask, 0, 10000);
		timer.schedule(toTask, 0, 1000);
	}

	private void handleRipPacket(byte type, Ethernet etherPacket, Iface inIface) 
	{
		switch(type)
		{
			case RIPv2.COMMAND_REQUEST:
				if (debug_RIP) System.out.println("Send RIP response");
				sendRip(RIP_RESPONSE, etherPacket, inIface);
				break;
			case RIPv2.COMMAND_RESPONSE:
				IPv4 ip = (IPv4)etherPacket.getPayload();
				UDP udp = (UDP)ip.getPayload();
				RIPv2 rip = (RIPv2)udp.getPayload();

				if (debug_RIP) System.out.println("Handle RIP response from " + IPv4.fromIPv4Address(ip.getSourceAddress()));

				List<RIPv2Entry> entries = rip.getEntries();
				for (RIPv2Entry entry : entries) 
				{
					int ipAddr = entry.getAddress();
					int mask = entry.getSubnetMask();
					int nextHop = ip.getSourceAddress();
					int metric = entry.getMetric() + 1;
					if (metric >= 17) 
					{ metric = 16; }
					int netAddr = ipAddr & mask;

					synchronized(this.ripMap)
					{
						if (ripMap.containsKey(netAddr))
						{
							LocalRipEntry localEntry = ripMap.get(netAddr);
							localEntry.timestamp = System.currentTimeMillis();
							if (metric < localEntry.metric)
							{
								localEntry.metric = metric;
								if (debug_RIP) System.out.println("Update RouteEntry " +
								IPv4.fromIPv4Address(ipAddr) + " " + IPv4.fromIPv4Address(nextHop) + " " + IPv4.fromIPv4Address(mask) + " " + inIface.toString());
								this.routeTable.update(ipAddr, mask, nextHop, inIface);
							}
	
							if (metric >= 16) 
							{
								RouteEntry bestMatch = this.routeTable.lookup(ipAddr);
								if (inIface.equals(bestMatch.getInterface()))
								{
									localEntry.metric = 16;
									if (null != bestMatch) 
									{this.routeTable.remove(ipAddr, mask);}
								}
							}
						}
						else
						{
							ripMap.put(netAddr, new LocalRipEntry(ipAddr, mask, nextHop, metric, System.currentTimeMillis()));
							if (metric < 16)
							{
								if (debug_RIP) System.out.println("Insert new RouteEntry " +
								IPv4.fromIPv4Address(ipAddr) + " " + IPv4.fromIPv4Address(nextHop) + " " + IPv4.fromIPv4Address(mask) + " " + inIface.toString());
								this.routeTable.insert(ipAddr, nextHop, mask, inIface);
							}
						}
					}
				}
				break;
			default:
				break;
		}
	}

	private void sendRip(int type, Ethernet etherPacket, Iface iface) 
	{
		Ethernet ether = new Ethernet();
		IPv4 ip = new IPv4();
		UDP udp = new UDP();
		RIPv2 rip = new RIPv2();

		ether.setSourceMACAddress(iface.getMacAddress().toBytes());
		ether.setEtherType(Ethernet.TYPE_IPv4);

		ip.setTtl((byte)64);
		ip.setProtocol(IPv4.PROTOCOL_UDP);
		ip.setSourceAddress(iface.getIpAddress());

		udp.setSourcePort(UDP.RIP_PORT);
		udp.setDestinationPort(UDP.RIP_PORT);

		switch(type)
		{
			case RIP_UNSOL:
				rip.setCommand(RIPv2.COMMAND_RESPONSE);
				ether.setDestinationMACAddress(MAC_BROADCAST);
				ip.setDestinationAddress(IPv4.toIPv4Address(IP_RIP_MULTICAST));
				break;
			case RIP_REQUEST:
				rip.setCommand(RIPv2.COMMAND_REQUEST);
				ether.setDestinationMACAddress(MAC_BROADCAST);
				ip.setDestinationAddress(IPv4.toIPv4Address(IP_RIP_MULTICAST));
				break;
			case RIP_RESPONSE:
				IPv4 ipPacket = (IPv4)etherPacket.getPayload();

				rip.setCommand(RIPv2.COMMAND_RESPONSE);
				ether.setDestinationMACAddress(ether.getSourceMACAddress());
				ip.setDestinationAddress(ipPacket.getSourceAddress());
				break;
			default:
				break;
		}

		List<RIPv2Entry> entries = new ArrayList<RIPv2Entry>();
		synchronized(this.ripMap)
		{
			for (LocalRipEntry localEntry : ripMap.values())
			{
				RIPv2Entry entry = new RIPv2Entry(localEntry.addr, localEntry.mask, localEntry.metric);
				entries.add(entry);
			}
		}

		ether.setPayload(ip);
		ip.setPayload(udp);
		udp.setPayload(rip);
		rip.setEntries(entries);

		if (debug_RIP) System.out.println("Sending RIP packet to " + IPv4.fromIPv4Address(ip.getDestinationAddress()));
		sendPacket(ether, iface);
	}

	/*******************************************************************************
	***********************				  IP 				************************
	*******************************************************************************/

	private void handleIpPacket(Ethernet etherPacket, Iface inIface)
	{
			// Make sure it's an IP packet
		if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4)
			{ return; }

		for (Iface iface : this.interfaces.values()) {
			arpCache.insert(iface.getMacAddress(), iface.getIpAddress());
		}

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
			if (debug_ARP) System.out.println("TIME_EXCEEDED");
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
					if (debug_ARP) System.out.println("DEST_PORT_UNREACHABLE");
					sendICMP(DEST_PORT_UNREACHABLE ,etherPacket, inIface);
				} 
				else if (protocol == IPv4.PROTOCOL_ICMP) 
				{
					ICMP icmpPacket = (ICMP) ipPacket.getPayload();

					if(icmpPacket.getIcmpType() == ICMP.TYPE_ECHO_REQUEST) 
					{
						if (debug_ARP) System.out.println("ICMP_ECHO_REPLY");
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
			if (debug_ARP) System.out.println("DEST_NET_UNREACHABLE");
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
			if (debug_ARP) System.out.println("arp miss ip");
			handleArpMiss(nextHop, etherPacket, inIface, outIface);
			return; 
		}
		etherPacket.setDestinationMACAddress(arpEntry.getMac().toBytes());

		this.sendPacket(etherPacket, outIface);
	}

	/*******************************************************************************
	***********************				  ARP 				************************
	*******************************************************************************/

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
					if (debug_ARP) System.out.println("ArpRequest received");
					sendArp(0, ARP_REPLY, etherPacket, inIface, inIface);
					break;
				}
				else if (ARP.OP_REPLY == arpPacket.getOpCode()) 
				{
					if (debug_ARP) System.out.println("ArpReply received");

					MACAddress mac = MACAddress.valueOf(arpPacket.getSenderHardwareAddress());
					int ip = ByteBuffer.wrap(arpPacket.getSenderProtocolAddress()).getInt();
					arpCache.insert(mac, ip);

					if (debug_ARP) System.out.println("Insert arp entry \n" + arpCache.toString());
					synchronized(arpQueues)
					{
						if (debug_ARP) {
							for (Map.Entry<Integer, List<Ethernet>> qEntry: arpQueues.entrySet())
								System.out.println(IPv4.fromIPv4Address(qEntry.getKey()) + " :: " + IPv4.fromIPv4Address(ip) + " :: " + qEntry.getValue().size());
						}
						List<Ethernet> queue = arpQueues.remove(ip);
						if (queue != null) 
						{
							if (debug_ARP) System.out.println("Send pending packets");
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
			ether.setDestinationMACAddress(MAC_BROADCAST);
			arp.setOpCode(ARP.OP_REQUEST);
			arp.setTargetHardwareAddress(Ethernet.toMACAddress(MAC_ZERO));
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

		if (debug_ARP) System.out.println("Send ARP Packet");
		this.sendPacket(ether, outIface);
	}

	private void handleArpMiss(final int ip, final Ethernet etherPacket, final Iface inIface, final Iface outIface) 
	{
		IPv4 ipPacket = (IPv4)etherPacket.getPayload();
		final Integer dstAddr = new Integer(ipPacket.getDestinationAddress());
		RouteEntry bestMatch = this.routeTable.lookup(dstAddr);

		if (null == bestMatch)
		{ return; }

		int temp = bestMatch.getGatewayAddress();
		if (0 == temp)
		{ temp = dstAddr; }
		final int nextHop = temp;
		synchronized(arpQueues)
		{
			if (arpQueues.containsKey(nextHop))
			{
				List<Ethernet> queue = arpQueues.get(nextHop);
				queue.add(etherPacket);
			}
			else 
			{
				List<Ethernet> queue = new ArrayList<Ethernet>();
				queue.add(etherPacket);
				arpQueues.put(nextHop, queue);
				TimerTask task = new TimerTask()
				{
					int counter = 0;
					public void run()
					{
						if (null != arpCache.lookup(nextHop)) 
						{ 
							this.cancel(); 
						}
						else 
						{
							if (counter > 2) 
							{
								if (debug_ARP) System.out.println("TimeOut\n" + arpCache.toString());
								arpQueues.remove(nextHop);
								sendICMP(DEST_HOST_UNREACHABLE, etherPacket, inIface);
								this.cancel();
							} 
							else 
							{
								if (debug_ARP) System.out.println("Timer  " + counter);
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
	}

	/*******************************************************************************
	***********************				  ICMP 				************************
	*******************************************************************************/

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
			if (debug_ARP) System.out.println("No best match");
			return;   
		}

		int nextHop = bestMatch.getGatewayAddress();
		if (0 == nextHop)
			{ nextHop = srcAddr; }

		ArpEntry arpEntry = this.arpCache.lookup(nextHop);
		if (null == arpEntry)
		{  	
			if (debug_ARP) System.out.println("arp miss icmp");
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

		if (debug_ARP) System.out.println("Send ICMP");
		this.sendPacket(ether, inIface);
	}
}
