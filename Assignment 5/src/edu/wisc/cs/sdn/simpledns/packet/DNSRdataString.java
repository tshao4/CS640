package edu.wisc.cs.sdn.simpledns.packet;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class DNSRdataString implements DNSRdata
{
	private String string;
	
	public DNSRdataString()
	{ this.string = new String(); }
	
	public DNSRdataString(String string)
	{ this.string = string; }
	
	public String getString()
	{ return this.string; }
	
	public void setString(String string)
	{ this.string = string; }
	
	public static DNSRdata deserialize(ByteBuffer bb, short len)
	{
		DNSRdataString rdata = new DNSRdataString();
		int strLength = bb.get();
		byte[] strBytes = new byte[strLength];
		bb.get(strBytes);
		rdata.string = new String(strBytes, StandardCharsets.US_ASCII);
		return rdata;
	}
	
	public byte[] serialize()
	{ 
		byte[] data = new byte[this.string.length() + 1];
		ByteBuffer bb = ByteBuffer.wrap(data);
		bb.put((byte)(this.string.length()));
		bb.put(this.string.getBytes(StandardCharsets.US_ASCII));
		return data; 
	}
	
	public int getLength()
	{ return this.string.length() + 1; }

	public String toString()
	{ return this.string; }
}
