package edu.wisc.cs.sdn.simpledns;



import edu.wisc.cs.sdn.simpledns.packet.*;

public class SimpleDNS 
{
	public static void main(String[] args)
	{
        if(args.length != 4 || !args[0].equals("-r") || !args[2].equals("-e")){
			System.out.println("Usage:\njava SimpleDNS -r <root server ip> -e <ec2 csv>");
			System.exit(0);
		}

		
	}
}
