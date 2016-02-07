import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.*;
import java.util.Arrays;
import java.lang.NumberFormatException;

public class Iperfer {
	public static void main(String[] args){
		String modeIndicator = null;
		int portNum = -1;
		int time = Integer.MIN_VALUE;
		String hostName = null;
					
		for(int i = 0; i < args.length; i++){
			switch (args[i].toLowerCase()) {
				case "-c" :
					if(modeIndicator == null) {
						modeIndicator = "c";
						if(args.length != 7){
							System.out.println("Error: missing or additional arguments - client mode");
							return;
						}
					} else {
						System.out.println("Error: more than one mode flag (1)");
						return;
					}
					break;
				case "-s" :
					if(modeIndicator == null) {
						modeIndicator = "s";
						if(args.length != 3){
							System.out.println("Error: missing or additional arguments - server mode");
							return;
						}
					} else {
						System.out.println("Error: more than one mode flag (2)");
						return;
					}
					break;
				case "-h" :
					if(hostName == null && i + 1 <= args.length){
						hostName = args[++i];
					} else {
						System.out.println("Error: missing or additional arguments - h flag");
						return;
					}
					break;
				case "-p" :
					if(portNum < 0 && i + 1 <= args.length){
						try {
							portNum = Integer.parseInt(args[++i]);
						}catch(NumberFormatException ex) {
							System.out.println("Error: invalid port number");
							return;
						}
						
						if(portNum < 1024 || portNum > 65535){
							System.out.println("Error: port number must be in the range 1024 to 65535");
							return;
						}
					} else {
						System.out.println("Error: missing or additional arguments - p flag");
						return;
					}
					break;
				case "-t" :
					if(time == Integer.MIN_VALUE && i + 1 <= args.length){
						try {
							time = Integer.parseInt(args[++i]);
						}catch(NumberFormatException ex) {
							System.out.println("Error: invalid time");
						}

						if(time <= 0){
							System.out.println("Error: time must be a positive integer");
							return;
						}
					} else {
						System.out.println("Error: missing or additional arguments - t flag");
						return;
					}
					break;
				default:
					System.out.println("Error: no matching flag");
					break;
			}
		}
		
		if(modeIndicator.equalsIgnoreCase("c")){
			try{
				double counter = 0;
				Socket echoSocket = new Socket(hostName, portNum);
				byte[] data = new byte[1000];
				Arrays.fill(data, (byte) 0 );
				DataOutputStream out = new DataOutputStream(echoSocket.getOutputStream());
				long begin = System.currentTimeMillis();
				while(System.currentTimeMillis() - begin < time * 1000) {
					out.write(data);
					counter++;
				}
				out.close();
				echoSocket.close();
				
				double dataRate = counter / 1000 * 8 / time;
				
				System.out.println("sent=" + counter + " KB rate=" + dataRate + " Mbps");
			} catch (Exception ex){
				System.out.println("Socket error");
				ex.printStackTrace(System.out);
			}
			
		} else if(modeIndicator.equalsIgnoreCase("s")){
			try{
				ServerSocket serverSocket = new ServerSocket(portNum);
				Socket clientSocket = serverSocket.accept();
				DataInputStream in = new DataInputStream(clientSocket.getInputStream()); 
				byte[] data = new byte[1000];
				double counter = 0;
				long startTime = System.currentTimeMillis();
				long endTime = startTime;
				while(true){
					int byteNum = in.read(data, 0, 1000);
					if(byteNum == -1){
						endTime = System.currentTimeMillis();
						break;
					}
					counter = counter + byteNum;
				}
				
				
				in.close();
				clientSocket.close();
				serverSocket.close();
				counter = counter / 1000;
				long totalTime = endTime - startTime;
				double dataRate = counter * 8 / totalTime;
				System.out.println("received=" + counter + " KB rate=" + dataRate + " Mbps");
			} catch(Exception ex){
				System.out.println("Server Read error");
				ex.printStackTrace(System.out);
			}
		} else{
			System.out.println("Error: missing mode flag");
			return;
		}
		
	}
}