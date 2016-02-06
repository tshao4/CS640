import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.*;
import java.sql.Time;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class Iperfer {
	public static void main(String[] args){
		String modeIndicator = "";
		int portNum = -1;
		int time = Integer.MIN_VALUE;
		String hostName = "";
					
		for(int i = 0; i < args.length; i++){
			switch (args[i].toLowerCase()) {
				case "-c" :
					if(modeIndicator == null) {
						modeIndicator = "c";
						if(args.length != 7){
							System.out.println("Error: missing or additional arguments");
							return;
						}
					} else {
						System.out.println("Error: missing or additional arguments");
						return;
					}
					break;
				case "-s" :
					if(modeIndicator == null) {
						modeIndicator = "s";
						if(args.length != 3){
							System.out.println("Error: missing or additional arguments");
							return;
						}
					} else {
						System.out.println("Error: missing or additional arguments");
						return;
					}
					break;
				case "-h" :
					if(hostName == null){
						hostName = args[++i];
					} else {
						System.out.println("Error: missing or additional arguments");
						return;
					}
					break;
				case "-p" :
					if(portNum < 0){
						portNum = Integer.parseInt(args[++i]);
						if(portNum < 1024 || portNum > 65535){
							System.out.println("Error: port number must be in the range 1024 to 65535");
						}
					} else {
						System.out.println("Error: missing or additional arguments");
						return;
					}
					break;
				case "-t" :
					if(time == Integer.MIN_VALUE){
						time = Integer.parseInt(args[++i]);
						if(time < 0){
							System.out.println("Error: missing or additional arguments");
							return;
						}
					} else {
						System.out.println("Error: missing or additional arguments");
						return;
					}
					break;
				default:
					break;
			}
		}
		
		if(modeIndicator.equalsIgnoreCase("-c")){
			try{
				Counter counter = new Counter();
				Socket echoSocket = new Socket(hostName, portNum);
				byte[] data = new byte[1000];
				Arrays.fill(data, (byte) 0 );
				DataOutputStream out = new DataOutputStream(echoSocket.getOutputStream());
				Timer timer = new Timer();
				TimerTask task = new TimerTask(){
					@Override
					public void run(){
						try {
							out.write(data);
							counter.increment();
						} catch (IOException e) {
							// TODO Auto-generated catch block
							System.out.println("output stream error");
						} 
						
					}
					
				};
				time = time * 1000;
				timer.schedule(task, 0, time);
				out.close();
				echoSocket.close();
				
				double dataRate = counter.getValue() / 1000 * 8 / time;
				
				System.out.println(String.format("sent=%1 KB rate=$2 Mbps", counter.getValue(), dataRate));
			} catch (Exception ex){
				System.out.println("Socket error");
			}
			
		} else if(modeIndicator.equalsIgnoreCase("-s")){
			try{
				ServerSocket serverSocket = new ServerSocket(portNum);
				Socket clientSocket = serverSocket.accept();
				DataInputStream in = new DataInputStream(clientSocket.getInputStream()); 
				byte[] data = new byte[1000];
				int counter = 0;
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
				
				long totalTime = endTime - startTime;
				double dataRate = counter / 1000 * 8 / totalTime;
				System.out.println(String.format("sent=%1 KB rate=$2 Mbps", counter, dataRate));
			} catch(Exception ex){
				System.out.println("Server Read error");
			}
		} else{
			System.out.println("Error: missing or additional arguments");
			return;
		}
		
		
	}
}


class Counter{
	private int counter;
	public Counter(){
		counter = 0;
	}
	
	public int getValue(){
		return counter;
	}
	
	public void increment(){
		counter++;
	}
}
