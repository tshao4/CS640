package edu.wisc.cs.sdn.vnet.sw;

import net.floodlightcontroller.packet.Ethernet;
import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.Runnable;
import java.lang.Thread;

/**
 * @author Aaron Gember-Jacobson
 */
public class Switch extends Device implements Runnable
{	
	private ConcurrentHashMap<String, SwitchEntry> switchMap;
	private Thread thr;
	private final boolean dbg = false;

	public void run() {
		try{
			while (true) {
				if (switchMap != null) {
					for (Map.Entry<String, SwitchEntry> en: switchMap.entrySet()) {
						long timeLeft = System.currentTimeMillis() - en.getValue().getLastUpdate();
						if (timeLeft >= 15000L) {
							switchMap.remove(en.getKey());
							if (dbg) System.out.println("Entry Timeout: " + timeLeft);
						}
					}
				}
				Thread.sleep(500);
			}	
		}catch(InterruptedException e) {
			e.printStackTrace(System.out);
		}
		
	}

	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	public Switch(String host, DumpFile logfile)
	{
		super(host,logfile);
		switchMap = new ConcurrentHashMap<String, SwitchEntry>();
		thr = new Thread(this);
		thr.start();
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
		String srcMAC = etherPacket.getSourceMAC().toString();
		String dstMAC = etherPacket.getDestinationMAC().toString();
		SwitchEntry entry = switchMap.get(dstMAC);
		
		if (entry == null) {
			if (dbg) System.out.println("Broadcasting...");
			for (Iface ifa : interfaces.values()) {
				if (!inIface.equals(ifa)) {
					sendPacket(etherPacket, ifa);
				}
			}
		}else {
			if (dbg) System.out.println("Sending...");
			sendPacket(etherPacket, entry.getOutIface());
		}

		if (!switchMap.containsKey(srcMAC)) {
			if (dbg) System.out.println("Adding new forwarding entry...");
			switchMap.put(srcMAC, new SwitchEntry(System.currentTimeMillis(), inIface));
		}else {
			if (dbg) System.out.println("Updating existing forwarding entry...");
			SwitchEntry newEntry = switchMap.get(srcMAC);
			newEntry.setLastUpdate(System.currentTimeMillis());
			newEntry.setOutIface(inIface);
		}

		/********************************************************************/
	}
}

class SwitchEntry {
	private long lastUpdate;
	private Iface outIface;

	public SwitchEntry(long lastUpdate, Iface outIface) {
		this.lastUpdate = lastUpdate;
		this.outIface = outIface;
	}

	public void setLastUpdate(long lastUpdate) {
		this.lastUpdate = lastUpdate;
	}

	public void setOutIface(Iface outIface) {
		this.outIface = outIface;
	}

	public long getLastUpdate() {
		return this.lastUpdate;
	}

	public Iface getOutIface() {
		return this.outIface;
	}
}