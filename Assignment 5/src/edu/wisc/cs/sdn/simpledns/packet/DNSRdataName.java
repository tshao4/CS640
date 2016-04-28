package edu.wisc.cs.sdn.simpledns.packet;

import java.nio.ByteBuffer;

public class DNSRdataName implements DNSRdata 
{
	private String name;
	
	public DNSRdataName()
	{ this.name = new String(); }
	
	public DNSRdataName(String name)
	{ this.name = name; }
	
	public String getName()
	{ return this.name; }
	
	public void setName(String name)
	{ this.name = name; }
	
	public static DNSRdata deserialize(ByteBuffer bb)
	{
		DNSRdataName rdata = new DNSRdataName();		
		rdata.name = DNS.deserializeName(bb);
		return rdata;
	}
	
	public byte[] serialize()
	{ return DNS.serializeName(this.name); }
	
	public int getLength()
	{ return this.name.length() + (this.name.length() > 0 ? 2 : 0); }
	
	public String toString()
	{ return this.name; }
}
