package edu.wisc.cs.sdn.simpledns.packet;

import java.nio.ByteBuffer;

public class DNSRdataBytes implements DNSRdata
{
	private byte[] bytes;
	
	public DNSRdataBytes()
	{ this.bytes = new byte[0]; }
	
	public DNSRdataBytes(byte[] bytes)
	{ this.bytes = bytes; }
	
	public byte[] getBytes()
	{ return this.bytes; }
	
	public void setBytes(byte[] bytes)
	{ this.bytes = bytes; }
	
	public static DNSRdata deserialize(ByteBuffer bb, short len)
	{
		DNSRdataBytes rdata = new DNSRdataBytes();	
		rdata.bytes = new byte[len];
		bb.get(rdata.bytes);
		return rdata;
	}
	
	public byte[] serialize()
	{ return this.bytes; }
	
	public int getLength()
	{ return this.bytes.length; }

	public String toString()
	{
		String result = "";
		for(int i = 0; i < this.bytes.length; i++)
		{ result += String.format("%02X ", this.bytes[i]); }
		return result;
	}
}
