package edu.wisc.cs.sdn.simpledns.packet;

import java.nio.ByteBuffer;

public class DNSResourceRecord 
{
	private String name;
	private short type;
	private short cls;
	private int ttl;
	private DNSRdata data;
	
	public DNSResourceRecord()
	{
		this.name = new String();
		this.cls = DNS.CLASS_IN;
		this.data = new DNSRdataBytes();
	}
	
	public DNSResourceRecord(String name, short type, DNSRdata data)
	{
		this();
		this.name = name;
		this.type = type;
		this.data = data;
	}
	
	public String getName()
	{ return this.name; }
	
	public void setName(String name)
	{ this.name = name; }
	
	public short getType()
	{ return this.type; }
	
	public void setType(short type)
	{ this.type = type; }
	
	public short getCls()
	{ return this.cls; }
	
	public int getTtl()
	{ return this.ttl; }
	
	public void setTtl(int ttl)
	{ this.ttl = ttl; }
	
	public DNSRdata getData()
	{ return this.data; }
	
	public void setData(DNSRdata data)
	{ this.data = data; }
	
	public static DNSResourceRecord deserialize(ByteBuffer bb)
	{
		DNSResourceRecord record = new DNSResourceRecord();		
		
		record.name = DNS.deserializeName(bb);
		record.type = bb.getShort();
		record.cls = bb.getShort();
		record.ttl = bb.getInt();
		
		// Read record data
		short rdataLength = bb.getShort();
		if (rdataLength > 0)
		{
			switch (record.type)
			{
			case DNS.TYPE_A:
			case DNS.TYPE_AAAA:
				record.data = DNSRdataAddress.deserialize(bb, rdataLength);
				break;
			case DNS.TYPE_NS:
			case DNS.TYPE_CNAME:
				record.data = DNSRdataName.deserialize(bb);
				break;
			default:
				record.data = DNSRdataBytes.deserialize(bb, rdataLength);
			}
		}
		
		return record;
	}
	
	public byte[] serialize()
	{
		byte[] data = new byte[this.getLength()];
		ByteBuffer bb = ByteBuffer.wrap(data);
		
		bb.put(DNS.serializeName(this.name));	
		bb.putShort(this.type);
		bb.putShort(this.cls);
		bb.putInt(this.ttl);
		bb.putShort((short)(this.data.getLength()));
		bb.put(this.data.serialize());
		
		return data;
	}
	
	public int getLength()
	{
		return 1 + this.name.length() + (this.name.length() > 0 ? 1 : 0)
				+ 10 + this.data.getLength();
	}
	
	public String toString()
	{
		String strType;
		switch(this.type)
		{
		case DNS.TYPE_A:
			strType = "A";
			break;
		case DNS.TYPE_NS:
			strType = "NS";
			break;
		case DNS.TYPE_CNAME:
			strType = "CNAME";
			break;
		case DNS.TYPE_AAAA:
			strType = "AAAA";
			break;
		case DNS.TYPE_CDN:
			strType = "CDN";
			break;
		case DNS.TYPE_EC2:
			strType = "EC2";
			break;
		default:
			strType = String.format("Unknown (%d)", this.type);
			break;
		}
		
		String strClass;
		switch(this.cls)
		{
		case DNS.TYPE_A:
			strClass = "IN";
			break;
		default:
			strClass = String.format("Unknown (%d)", this.cls);
			break;
		}
		
		return String.format("Name: %s, Type: %s, Class: %s, TTL: %d, Data: %s",
				this.name, strType, strClass, this.ttl, this.data.toString());
	}
}
