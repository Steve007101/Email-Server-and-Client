

/**
 * @author Steven Perry
 * @version 4/2/2019
 */

import java.io.*;
import java.net.*;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;


public class HTTPServer
{
    
    private static Timestamp ts;
    private static InetAddress serverAd;
    private static String serverHostName;
    private static String serverIP;
    private static int serverPort;
   
    public static void main(String[] args) throws IOException 
    {
        // Check if exactly one argument was passed
        if (args.length != 1) {
            System.err.println("Usage: <tcp-port-number>");
            System.exit(1);
        } 
        
        // check if client database exists
        if (!new File("db/").exists())
        {
            (new File("db/")).mkdir();
        }
        
        // Get server external IP from a website
        //URL whatsmyip = new URL("http://checkip.amazonaws.com");
        //BufferedReader in = new BufferedReader(new InputStreamReader(
        //        whatsmyip.openStream()));
        //serverIP = in.readLine(); 
        
        // get current path
        String path = System.getProperty("user.dir");
        // report various info to console
        System.out.println("Current path: " + path);
        //System.out.println("External IP: " + serverIP);
        serverAd = InetAddress.getLocalHost();
        serverIP = serverAd.getHostAddress();
        System.out.println("Internal IP: " + serverIP);
        serverHostName = serverAd.getHostName();
        System.out.println("Host Name: " + serverHostName);
        serverPort = Integer.parseInt(args[0]);
        System.out.println("Listening Port: " + serverPort);
        ts = new Timestamp(System.currentTimeMillis());
        System.out.println("Current Time: " + ts);

        DatagramSocket socket = new DatagramSocket(serverPort);
        byte[] buf;
        boolean running = true;

        System.out.println("HTTP Server starting...");
        while (running)
        {
               
            // Receive packet
            buf = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            socket.receive(packet);
            // start thread to take care of packet
            new Thread(new HTTPResponder(socket, packet, serverIP, serverHostName)).start(); 
 
        } 
        socket.close();
        System.out.println("Server closing...");    
    }

    
}