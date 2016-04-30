package edu.wisc.cs.sdn.simpledns;

import java.io.File;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Scanner;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Collection;
import java.nio.ByteBuffer;

import edu.wisc.cs.sdn.simpledns.packet.DNS;
import edu.wisc.cs.sdn.simpledns.packet.DNSQuestion;
import edu.wisc.cs.sdn.simpledns.packet.DNSRdataAddress;
import edu.wisc.cs.sdn.simpledns.packet.DNSRdataName;
import edu.wisc.cs.sdn.simpledns.packet.DNSRdataString;
import edu.wisc.cs.sdn.simpledns.packet.DNSResourceRecord;

public class SimpleDNS
{
	private static final int PORT_LISTEN = 8053;
	private static final int PORT_SEND = 53;

	private static List<EC2Entry> ec2 = new ArrayList<EC2Entry>();

	public static void main(String[] args) {
        if(args.length != 4 || !args[0].equals("-r") || !args[2].equals("-e")){
			System.out.println("Usage:\njava edu.wisc.cs.sdn.simpledns.SimpleDNS -r <root server ip> -e <ec2 csv>");
			System.exit(0);
		}

		try {
			DatagramSocket socket = new DatagramSocket(PORT_LISTEN);
			parseCSV(args[3]);
			InetAddress serverIP = InetAddress.getByName(args[1]);
			DatagramPacket packet = new DatagramPacket(new byte[1500], 1500);
			while (true) {
				socket.receive(packet);
				DNS dns = DNS.deserialize(packet.getData(), packet.getLength());

				if (DNS.OPCODE_STANDARD_QUERY != dns.getOpcode()) continue;

				if (dns.getQuestions().isEmpty() || !checkType(dns.getQuestions().get(0).getType()))
						continue;

				DatagramPacket reply;
				if (dns.isRecursionDesired()) {
					reply = rresolve(packet, dns, serverIP);
				}
				else {
					reply = resolve(packet, serverIP);
				}

				reply.setPort(packet.getPort());
				reply.setAddress(packet.getAddress());
				socket.send(reply);
			}

		}
		catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	private static boolean checkType(short t) {
		if (DNS.TYPE_A == t || DNS.TYPE_AAAA == t || DNS.TYPE_NS == t || DNS.TYPE_CNAME == t)
			return true;
		return false;
	}

	private static void parseCSV(String filename) throws Exception {
		Scanner in = new Scanner(new File(filename));

		while (in.hasNext()) {
			String line = in.nextLine();
			String[] sp = line.split(",");
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

			ec2.add(new EC2Entry(ip, mask, sp[1]));
		}
	}

	private static int parseIP(String ips) throws Exception {
		String[] ip_split = ips.split("\\.");
	
		int ip = 0;
		for (int i = 0; i < 4; i++) {
			ip |= Integer.parseInt(ip_split[i]) << (8 * (3-i));
		}
		return ip;
	}

	private static DatagramPacket resolve(DatagramPacket packet, InetAddress serverIP) throws Exception {
		DatagramSocket socket = new DatagramSocket();
		DatagramPacket outPacket = new DatagramPacket(packet.getData(), packet.getLength(), serverIP, PORT_SEND);
		DatagramPacket inPacket = new DatagramPacket(new byte[1500], 1500);
		socket.send(outPacket);
		socket.receive(inPacket);
		socket.close();
		return inPacket;
	}

	private static DatagramPacket rresolve(DatagramPacket packet, DNS dns, InetAddress serverIP) throws Exception {
		DatagramSocket socket = new DatagramSocket();
		DatagramPacket outPacket = new DatagramPacket(packet.getData(), packet.getLength(), serverIP, PORT_SEND);
		DatagramPacket inPacket = new DatagramPacket(new byte[1500], 1500);

		List<DNSResourceRecord> prevAuth = new ArrayList<DNSResourceRecord>();
		List<DNSResourceRecord> prevAddi = new ArrayList<DNSResourceRecord>();
		List<DNSResourceRecord> cnames = new ArrayList<DNSResourceRecord>();
		socket.send(outPacket);

		while (true) {
			socket.receive(inPacket);

			DNS inDNS = DNS.deserialize(inPacket.getData(), inPacket.getData().length);
			List<DNSResourceRecord> answers = inDNS.getAnswers();
			List<DNSResourceRecord> authorities = inDNS.getAuthorities();
			List<DNSResourceRecord> additionals = inDNS.getAdditional();

			if (0 < authorities.size()) {
				for (DNSResourceRecord r : authorities) {
					if (checkType(r.getType())) {
						prevAuth = authorities;
						break;
					}
				}
			}
			if (0 < additionals.size()) {
				prevAddi = additionals;
			}

			if (0 == answers.size()) {
				InetAddress nextServerIP = null;

				outerloop:
				for (DNSResourceRecord auth : authorities) {
					for (DNSResourceRecord addi : additionals) {
						String serverName = ((DNSRdataName) auth.getData()).getName();
						if (DNS.TYPE_NS == auth.getType() && DNS.TYPE_A == addi.getType() && addi.getName().equals(serverName)) {
							nextServerIP = ((DNSRdataAddress) addi.getData()).getAddress();
							break outerloop;
						}
					}
				}

				if (null == nextServerIP) {
					DNS reply = new DNS();

					for (DNSResourceRecord cname : cnames) {
						answers.add(cname);
					}

					reply.setQuestions(dns.getQuestions());
					reply.setAnswers(answers);

					Collection<DNSResourceRecord> toRemove = new ArrayList<DNSResourceRecord>();
					for (DNSResourceRecord auth : authorities) {
						if (!checkType(auth.getType()))
							toRemove.add(auth);
					}
					authorities.removeAll(toRemove);
					toRemove.clear();
					for (DNSResourceRecord addi : additionals) {
						if (!checkType(addi.getType()))
							toRemove.add(addi);
					}
					additionals.removeAll(toRemove);

					if (0 == authorities.size())
						authorities = prevAuth;
					if (0 == additionals.size())
						additionals = prevAddi;

					reply.setAuthorities(authorities);
					reply.setAdditional(additionals);
					reply.setQuery(false);
					reply.setOpcode((byte) 0);
					reply.setAuthoritative(false);
					reply.setTruncated(false);
					reply.setRecursionAvailable(true);
					reply.setRecursionDesired(true);
					reply.setAuthenicated(false);
					reply.setCheckingDisabled(false);
					reply.setRcode((byte) 0);
					reply.setId(dns.getId());

					socket.close();
					return new DatagramPacket(reply.serialize(), reply.getLength());
				}

				socket.send(new DatagramPacket(outPacket.getData(), outPacket.getLength(), nextServerIP, PORT_SEND));
			}
			else {
				DNSResourceRecord ans = answers.get(0);
				if (DNS.TYPE_CNAME == ans.getType()) {
					cnames.add(ans);

					String name = ((DNSRdataName) ans.getData()).getName();
					List<DNSQuestion> questions = new ArrayList<DNSQuestion>();
					questions.add(new DNSQuestion(name, inDNS.getQuestions().get(0).getType()));

					DNS request = new DNS();
					request.setQuery(true);
					request.setOpcode((byte) 0);
					request.setTruncated(false);
					request.setRecursionDesired(true);
					request.setAuthenicated(false);
					request.setId(dns.getId());
					request.setQuestions(questions);

					socket.send(new DatagramPacket(request.serialize(), request.getLength(), serverIP, PORT_SEND));
				}
				else {

					if (DNS.TYPE_A == dns.getQuestions().get(0).getType()) {
						// TODO: CHECK EC2
					}

					for (DNSResourceRecord name : cnames) {
						answers.add(name);
					}

					if (0 == inDNS.getAuthorities().size()) {
						inDNS.setAuthorities(prevAuth);
					}
					if (0 == inDNS.getAdditional().size()) {
						inDNS.setAdditional(prevAddi);
					}

					inDNS.setAnswers(answers);
					inDNS.setQuestions(inDNS.getQuestions());
					inDNS.setQuery(false);
					inDNS.setOpcode((byte) 0);
					inDNS.setAuthoritative(false);
					inDNS.setTruncated(false);
					inDNS.setRecursionAvailable(true);
					inDNS.setRecursionDesired(true);
					inDNS.setAuthenicated(false);
					inDNS.setCheckingDisabled(false);
					inDNS.setRcode((byte) 0);

					socket.close();
					return new DatagramPacket(inDNS.serialize(), inDNS.getLength());
				}

			}
		}
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