import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.*;
import java.util.Arrays;
import java.lang.NumberFormatException;
import java.lang.IndexOutOfBoundsException;

public class Iperfer {
	public static void main(String[] args){
		String modeIndicator = null;
		int portNum = -1;
		double time = Double.MIN_VALUE;
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
						try{
							hostName = args[++i];
						}catch(IndexOutOfBoundsException ex) {
							System.out.println("Error: missing or additional arguments - h flag - IOOBE");
							return;
						}
						
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
						}catch(IndexOutOfBoundsException ex) {
							System.out.println("Error: missing or additional arguments - p flag - IOOBE");
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
					if(time == Double.MIN_VALUE && i + 1 <= args.length){
						try {
							time = Double.parseDouble(args[++i]);
						}catch(NumberFormatException ex) {
							System.out.println("Error: invalid time");
						}catch(IndexOutOfBoundsException ex) {
							System.out.println("Error: missing or additional arguments - t flag - IOOBE");
							return;
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
				double dataKB = 0;
				Socket echoSocket = new Socket(hostName, portNum);
				byte[] data = new byte[1000];
				Arrays.fill(data, (byte) 0 );
				DataOutputStream out = new DataOutputStream(echoSocket.getOutputStream());
				double timeToRun = time * Math.pow(10, 9);
				double totalTime = 0;
				long startTime, endTime;
				while(totalTime < timeToRun) {
					startTime = System.nanoTime();
					out.write(data);
					endTime = System.nanoTime();
					totalTime += endTime - startTime;
					dataKB++;
				}
				out.close();
				echoSocket.close();
				totalTime = totalTime / Math.pow(10, 9);;
				double dataRate = dataKB / 1000 * 8 / totalTime;
				
				System.out.println("sent=" + dataKB + " KB rate=" + dataRate + " Mbps");
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
				double dataB = 0;
				long startTime, endTime;
				double totalTime = 0;

				while(true){
					startTime = System.nanoTime();
					int byteNum = in.read(data, 0, 1000);
					endTime = System.nanoTime();
					if(byteNum == -1){
						break;
					}
					totalTime += endTime - startTime;
					dataB = dataB + byteNum;
				}
				in.close();
				clientSocket.close();
				serverSocket.close();
				totalTime = totalTime / Math.pow(10, 9);;
				double dataRate = dataB / 1000000 * 8 / totalTime;
				System.out.println("received=" + dataB / 1000 + " KB rate=" + dataRate + " Mbps");
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