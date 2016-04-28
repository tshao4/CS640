package edu.wisc.cs.sdn.simpledns.packet;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

public class DNSRdataAddress implements DNSRdata 
{
	private InetAddress address;
	
	public DNSRdataAddress()
	{ }
	
	public DNSRdataAddress(InetAddress address)
	{ this.address = address; }
	
	public InetAddress getAddress()
	{ return this.address; }
	
	public void setAddress(InetAddress address)
	{ this.address = address; }
	
	public static DNSRdata deserialize(ByteBuffer bb, short len)
	{
		DNSRdataAddress rdata = new DNSRdataAddress();		
		byte[] addrBytes = new byte[len];
		bb.get(addrBytes);
		try
		{
			if (4 == len)
			{ rdata.address = Inet4Address.getByAddress(addrBytes); } 
			else if (16 == len)
			{ rdata.address = Inet6Address.getByAddress(addrBytes); } 
		}
		catch (UnknownHostException e) 
		{ e.printStackTrace(); }
		return rdata;
	}
	
	public byte[] serialize()
	{
		byte[] data = new byte[this.getLength()];
		ByteBuffer bb = ByteBuffer.wrap(data);
		bb.put(this.address.getAddress());
		return data;
	}
	
	public int getLength()
	{
		if (this.address instanceof Inet4Address)
		{ return 4; }
		else if (this.address instanceof Inet6Address)
		{ return 16; }
		System.err.println("SHOULD NOT GET HERE!!");
		return 0;
	}
	
	public String toString()
	{ return this.address.getHostAddress(); }
}
