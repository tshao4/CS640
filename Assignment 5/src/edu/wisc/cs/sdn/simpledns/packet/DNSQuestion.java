package edu.wisc.cs.sdn.simpledns.packet;

import java.nio.ByteBuffer;

public class DNSQuestion 
{
	private String name;
	private short type;
	private short cls;
	
	public DNSQuestion()
	{
		this.name = new String();
		this.cls = DNS.CLASS_IN;
	}
	
	public DNSQuestion(String name, short type)
	{
		this();
		this.name = name;
		this.type = type;
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
	
	public void setClass(short cls)
	{ this.cls = cls; }
	
	public static DNSQuestion deserialize(ByteBuffer bb)
	{
		DNSQuestion question = new DNSQuestion();

		question.name = DNS.deserializeName(bb);		
		question.type = bb.getShort();
		question.cls = bb.getShort();
		
		return question;
	}
	
	public byte[] serialize()
	{
		byte[] data = new byte[this.getLength()];
		ByteBuffer bb = ByteBuffer.wrap(data);
		
		bb.put(DNS.serializeName(this.name));
		bb.putShort(this.type);
		bb.putShort(this.cls);
		
		return data;
	}
	
	public int getLength()
	{
		return this.name.length() + 1 + (name.length() > 0 ? 1 : 0) + 4;
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
		return String.format("Name: %s, Type: %s, Class: %s", this.name,
				strType, strClass);
	}
}
