package edu.wisc.cs.sdn.simpledns;

import java.io.File;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Scanner;
import java.util.ArrayList;
import java.util.BitSet;
import java.nio.ByteBuffer;

import edu.wisc.cs.sdn.simpledns.packet.*;

public class SimpleDNS
{
	private static final int PORT_LISTEN = 8053;

	public static void main(String[] args) {
        if(args.length != 4 || !args[0].equals("-r") || !args[2].equals("-e")){
			System.out.println("Usage:\njava SimpleDNS -r <root server ip> -e <ec2 csv>");
			System.exit(0);
		}

		try {
			DatagramSocket socket = new DatagramSocket(PORT_LISTEN);
			List<EC2Entry> ec2 = parseCSV(args[3]);
			int serverIP = parseIP(args[1]);
			DatagramPacket packet = new DatagramPacket(ByteBuffer.wrap(new byte[1500]), 1500);
			while (true) {
				socket.receive(packet);
				DNS dns = DNS.deserialize(packet.getData());

				if (0 != dns.getOpcode() || !dns.isQuery()) continue;
				List<DNSQuestion> questions = dns.getQuestions();
				boolean typeGood = true;
				for (DNSQuestion q : questions) {
					short t = q.getType();
					if (DNS.TYPE_A != t && DNS.TYPE_AAAA != t && DNS.TYPE_CNAME != t && DNS.TYPE_NS != t) {
						typeGood = false;
						break;
					}
				}
				
				if (!typeGood && questions.isEmpty()) continue;

				for (DNSQuestion q : questions) {
					
				}
			}

		}
		catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	private static List<EC2Entry> parseCSV(String filename) throws Exception {
		Scanner in = new Scanner(new File(filename));
		List<EC2Entry> entries = new ArrayList<EC2Entry>();

		while (in.hasNext()) {
			String line = in.nextLine();
			String sp = line.split(",");
			String[] split = sp[0].split("/");
			
			int ip = parseIP(split[0]);

			int prefix = Integer.parseInt(split[1]);
			BitSet bs = new BitSet(32);
			for (int i = 0; i < prefix; i++) {
				bs.set(i);
			}
	
			int mask = 0;
			for (int i = 0; i < 32; i++) {
				mask = (bs.get(i) ? 1 : 0) | (mask << 1);
			}

			entries.add(new EC2Entry(ip, mask, sp[1]));
		}

		return entries;
	}

	private static int parseIP(String ips) throws Exception {
		String[] ip_split = ips.split("\\.");
	
		int ip = 0;
		for (int i = 0; i < 4; i++) {
			ip |= Integer.parseInt(ip_split[i]) << (8 * (3-i));
		}
		return ip;
	}
}

class EC2Entry {
	public int ip;
	public int mask;
	public String location;

	public EC2Entry(int ip, int mask, String location) {
		this.ip = ip;
		this.mask = mask;
		this.location = location;
	}
}