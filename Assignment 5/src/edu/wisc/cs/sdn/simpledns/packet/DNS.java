package edu.wisc.cs.sdn.simpledns.packet;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class DNS 
{
	public static final short TYPE_A = 1;
	public static final short TYPE_NS = 2;
	public static final short TYPE_CNAME = 5;
	public static final short TYPE_AAAA = 28;
	public static final short TYPE_CDN = 258;
	public static final short TYPE_EC2 = 259;
	
	public static final short CLASS_IN = 1;
	
	public static final byte OPCODE_STANDARD_QUERY = 0;
	
	public static final byte RCODE_NO_ERROR = 0;
	public static final byte RCODE_FORMAT_ERROR = 1;
	public static final byte RCODE_NOT_IMPLEMENTED = 4;
	
	private short id;
	private boolean query;
	private byte opcode;
	private boolean authoritative;
	private boolean truncated;
	private boolean recursionDesired;
	private boolean recursionAvailable;
	private boolean authenticated;
	private boolean checkingDisabled;
	private byte rcode;
	
	private List<DNSQuestion> questions;
	private List<DNSResourceRecord> answers;
	private List<DNSResourceRecord> authorities;
	private List<DNSResourceRecord> additional;
	
	public DNS()
	{
		this.recursionAvailable = true;
		this.questions = new ArrayList<DNSQuestion>();
		this.answers = new ArrayList<DNSResourceRecord>();
		this.authorities = new ArrayList<DNSResourceRecord>();
		this.additional = new ArrayList<DNSResourceRecord>();
	}
	
	public short getId()
	{ return this.id; }
	
	public void setId(short id)
	{ this.id = id; }
	
	public byte getOpcode()
	{ return this.opcode; }
	
	public void setOpcode(byte opcode)
	{ this.opcode = opcode; }
	
	public byte getRcode()
	{ return this.rcode; }
	
	public void setRcode(byte rcode)
	{ this.rcode = rcode; }
	
	public boolean isQuery()
	{ return this.query; }
	
	public void setQuery(boolean query)
	{ this.query = query; }
	
	public boolean isAuthoritate()
	{ return this.authoritative; }
	
	public void setAuthoritative(boolean authoritative)
	{ this.authoritative = authoritative; }
	
	public boolean isTruncated()
	{ return this.truncated; }
	
	public void setTruncated(boolean truncated)
	{ this.truncated = truncated; }
	
	public boolean isRecursionDesired()
	{ return this.recursionDesired; }
	
	public void setRecursionDesired(boolean recursionDesired)
	{ this.recursionDesired = recursionDesired; }
	
	public boolean isRecursionAvailable()
	{ return this.recursionAvailable; }
	
	public void setRecursionAvailable(boolean recursionAvailable)
	{ this.recursionAvailable = recursionAvailable; }
	
	public boolean isAuthenticated()
	{ return this.authenticated; }
	
	public void setAuthenicated(boolean authenticated)
	{ this.authenticated = authenticated; }
	
	public boolean isCheckingDisabled()
	{ return this.checkingDisabled; }
	
	public void setCheckingDisabled(boolean checkingDisabled)
	{ this.checkingDisabled = checkingDisabled; }
	
	public List<DNSQuestion> getQuestions()
	{ return this.questions; }
	
	public void setQuestions(List<DNSQuestion> questions)
	{ this.questions = questions; }
	
	public void addQuestion(DNSQuestion question)
	{ this.questions.add(question); }
	
	public void removeQuestion(DNSQuestion question)
	{ this.questions.remove(question); }
	
	public List<DNSResourceRecord> getAnswers()
	{ return this.answers; }
	
	public void setAnswers(List<DNSResourceRecord> answers)
	{ this.answers = answers; }
	
	public void addAnswer(DNSResourceRecord answer)
	{ this.answers.add(answer); }
	
	public void removeAnswer(DNSResourceRecord answer)
	{ this.answers.remove(answer); }
	
	public List<DNSResourceRecord> getAuthorities()
	{ return this.authorities; }
	
	public void setAuthorities(List<DNSResourceRecord> authorities)
	{ this.authorities = authorities; }
	
	public void addAuthority(DNSResourceRecord authority)
	{ this.authorities.add(authority); }
	
	public void removeAuthority(DNSResourceRecord authority)
	{ this.authorities.remove(authority); }
	
	public List<DNSResourceRecord> getAdditional()
	{ return this.additional; }
	
	public void setAdditional(List<DNSResourceRecord> additional)
	{ this.additional = additional; }
	
	public void addAdditional(DNSResourceRecord additional)
	{ this.additional.add(additional); }
	
	public void removeAdditional(DNSResourceRecord additional)
	{ this.additional.remove(additional); }
	
	public static DNS deserialize(byte[] data, int length)
	{
		DNS dns = new DNS();
		ByteBuffer bb = ByteBuffer.wrap(data, 0, length);
		
		dns.id = bb.getShort();
		
		byte tmp = bb.get();
		dns.query = (((tmp & 0b10000000) >> 7) == 0);
		dns.opcode = (byte)((tmp & 0b01111000) >> 3);
		dns.authoritative = (((tmp & 0b00000100) >> 2) == 1);
		dns.truncated = (((tmp & 0b00000010) >> 1) == 1);
		dns.recursionDesired = ((tmp & 0b00000001) == 1);
		
		tmp = bb.get();
		dns.recursionAvailable = (((tmp & 0b10000000) >> 7) == 1);
		dns.authenticated = (((tmp & 0b00100000) >> 5) == 1);
		dns.checkingDisabled = (((tmp & 0b00010000) >> 4) == 1);
		dns.rcode = (byte)(tmp & 0b00001111);
		
		short totalQuestions = bb.getShort();
		short totalAnswers = bb.getShort();
		short totalAuthority = bb.getShort();
		short totalAdditional = bb.getShort();
		
		for (int i = 0; i < totalQuestions; i++)
		{ dns.questions.add(DNSQuestion.deserialize(bb)); }
		
		for (int i = 0; i < totalAnswers; i++)
		{ dns.answers.add(DNSResourceRecord.deserialize(bb)); }
		
		for (int i = 0; i < totalAuthority; i++)
		{ dns.authorities.add(DNSResourceRecord.deserialize(bb)); }
		
		for (int i = 0; i < totalAdditional; i++)
		{ dns.additional.add(DNSResourceRecord.deserialize(bb)); }
		
		return dns;
	}
	
	public byte[] serialize()
	{
		byte[] data = new byte[this.getLength()];
		ByteBuffer bb = ByteBuffer.wrap(data);
		bb.putShort(this.id);
		
		byte tmp = 0;
		tmp |= (byte)(this.query ? 0 : (1 << 7));
		tmp |= (byte)(this.opcode << 3);
		tmp |= (byte)(this.authoritative ? (1 << 2) : 0);
		tmp |= (byte)(this.truncated ? (1 << 1) : 0);
		tmp |= (byte)(this.recursionDesired ? 1 : 0);
		bb.put(tmp);
		
		tmp = 0;
		tmp |= (byte)(this.recursionAvailable ? (1 << 7) : 0);
		tmp |= (byte)(this.authenticated ? (1 << 5) : 0);
		tmp |= (byte)(this.checkingDisabled ? (1 << 4) : 0);
		tmp |= (byte)(this.rcode & 0b00001111);
		bb.put(tmp);
		
		bb.putShort((short)(this.questions.size()));
		bb.putShort((short)(this.answers.size()));
		bb.putShort((short)(this.authorities.size()));
		bb.putShort((short)(this.additional.size()));
		
		for (DNSQuestion question : this.questions)
		{ bb.put(question.serialize()); }
		for (DNSResourceRecord record : this.answers)
		{ bb.put(record.serialize()); }
		for (DNSResourceRecord record : this.authorities)
		{ bb.put(record.serialize()); }
		for (DNSResourceRecord record : this.additional)
		{ bb.put(record.serialize()); }
		
		return data;
	}
	
	public int getLength()
	{
		int length = 12;
		for (DNSQuestion question : this.questions)
		{ length += question.getLength(); }
		for (DNSResourceRecord record : this.answers)
		{ length += record.getLength(); }
		for (DNSResourceRecord record : this.authorities)
		{ length += record.getLength(); }
		for (DNSResourceRecord record : this.additional)
		{ length += record.getLength(); }
		return length;
	}
	
	public String toString()
	{
		String strOpcode;
		switch(this.opcode)
		{
		case DNS.OPCODE_STANDARD_QUERY:
			strOpcode = "Standard query";
			break;
		default:
			strOpcode = String.format("Unknown (%d)", this.opcode);
			break;
		}
		
		String strRcode;
		switch(this.rcode)
		{
		case DNS.RCODE_NO_ERROR:
			strRcode = "No error";
			break;
		case DNS.RCODE_FORMAT_ERROR:
			strRcode = "Format error";
			break;
		case DNS.RCODE_NOT_IMPLEMENTED:
			strRcode = "Not implemented";
			break;
		default:
			strRcode = String.format("Unknown (%d)", this.rcode);
			break;
		}
		
		String result = String.format(
				"ID: 0x%04x, %s, Opcode: %s, Return Code: %s, Authoritative: %s, Truncated: %s, Recursion desired: %s, Recursion avail: %s\n",
				this.id, (this.query ? "Query" : "Response"), 
				strOpcode, strRcode, this.authoritative, this.truncated,
				this.recursionDesired, this.recursionAvailable);
		result += String.format("Questions: %d\n", this.questions.size());
		for (DNSQuestion question : this.questions)
		{ result += "\t" + question.toString() + "\n"; }
		result += String.format("Answers: %d\n", this.answers.size());
		for (DNSResourceRecord record : this.answers)
		{ result += "\t" + record.toString() + "\n"; }
		result += String.format("Authority: %d\n", this.authorities.size());
		for (DNSResourceRecord record : this.authorities)
		{ result += "\t" + record.toString() + "\n"; }
		result += String.format("Additional: %d\n", this.additional.size());
		for (DNSResourceRecord record : this.additional)
		{ result += "\t" + record.toString() + "\n"; }
		return result;
	}
	
	public static String deserializeName(ByteBuffer bb)
	{
		String name = new String();
		
		// Continue while there is another label, or a pointer
		byte labelLength = bb.get();
		while ((labelLength > 0) 
				|| ((labelLength & 0b11000000) == 0b11000000))
		{
			if ((labelLength & 0b11000000) == 0b11000000)
			{
				// Reader pointer
				bb.position(bb.position()-1);
				short ptr = (short)(bb.getShort() & 0b0011111111111111);
				
				// Go to pointer, saving place to return
				int returnPtr = bb.position();
				bb.position(ptr);
				
				name += "." + DNS.deserializeName(bb);
				
				// Return to location after pointer
				bb.position(returnPtr);
				break;
			}
			else
			{
				byte[] labelBytes = new byte[labelLength];
				bb.get(labelBytes);
				name += "." 
						+ new String(labelBytes, StandardCharsets.US_ASCII);
			}
			labelLength = bb.get();
		}
		
		// Remove leading period
		if (name.length() > 0)
		{ name = name.substring(1); }
		
		return name;
	}
	
	public static byte[] serializeName(String name)
	{
		byte[] data = new byte[name.length() + 1 + (name.length() > 0 ? 1 : 0)];
		ByteBuffer bb = ByteBuffer.wrap(data);
		
		if (name.length() > 0)
		{
			String[] labels = name.split("\\.");
			for (String label : labels)
			{
				bb.put((byte)(label.length()));
				bb.put(label.getBytes(StandardCharsets.US_ASCII));
			}
		}
		bb.put((byte)0);
		
		return data;
	}
}
