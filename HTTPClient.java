
/**
 * @author Steven Perry
 * @version 4/3/2019
 */
import java.io.*;
import java.net.*;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HTTPClient
{
    private static Timestamp ts;
    private static byte[] sendData, receiveData;
    private static DatagramSocket clientSocket;
    private static DatagramPacket sendPacket, receivePacket;
    private static InetAddress myAddress;
    private static int serverPort;
    private static String clientIP, serverHostname, path, entry, response;
    private static String encodedUsername, encodedPassword, username, password, body, temp;

    public static void main (String args[]) throws Exception
    {
        // Check if exactly 2 arguments were passed
        if (args.length != 2) {
            System.err.println("Usage: <server-hostname> <server-port>");
            System.exit(1);
        } 

        // Get client external IP from a website
        //URL whatsmyip = new URL("http://checkip.amazonaws.com");
        //BufferedReader in = new BufferedReader(new InputStreamReader(
        //        whatsmyip.openStream()));
        //clientIP = in.readLine(); 

        // get current path
        path = System.getProperty("user.dir");
        // report various info to console
        System.out.println("Current path: " + path);
        //System.out.println("External IP: " + clientIP);
        // serverHostname = "Stevens-PC";
        serverHostname = args[0];
        System.out.println("Server: " + serverHostname);
        serverPort = Integer.parseInt(args[1]);
        // serverPort = 56582;
        System.out.println("Server Port: " + serverPort);
        ts = new Timestamp(System.currentTimeMillis());
        System.out.println("Current Time: " + ts);

        clientSocket = new DatagramSocket();
        myAddress = InetAddress.getByName("localhost");

        BufferedReader input =
            new BufferedReader(new InputStreamReader(System.in));

        boolean running = true;
        int state = 0;
        entry = "";
        System.out.println("HTTP Client is starting...");
        System.out.println("*Type QUIT to close connection and client at any time*");
        while (running) {
            // first if user entered QUIT we end the program
            if (entry.length() >=4 && entry.equalsIgnoreCase("QUIT")) {
                entry = "QUIT";
                msgServer();
                if (response.length() >= 3 && response.substring(0,3).equals("221")) {
                    System.out.println("Connection with server has closed.");
                }
                else {
                    System.out.println("Server unresponsive to connection close request...");
                }
                running = false;
            }
            else {
                // Initial 220 connection established message
                if (state == 0) {
                    System.out.println("Attempting to connect to server...");
                    entry = "";
                    msgServer();    
                    if (response.length() >= 3 && response.substring(0,3).equals("220"))
                    {
                        System.out.println("Connection to server " 
                            + serverHostname + " established.");
                        state = 1;
                    }
                    else {
                        System.out.println("Connection attempt failed. Press enter to try again.");
                        entry = input.readLine();
                    }
                }
                // HELO message
                else if (state == 1) {
                    entry = "HELO";
                    msgServer();
                    if (response.length() >= 3 && response.substring(0,3).equals("250")) {
                        System.out.println("Exchanged greeting with server.");
                        state = 2;
                    }
                    else {
                        System.out.println("Failed greeting with server. Press enter to try again.");
                        entry = input.readLine();
                    }

                }
                // AUTH message
                else if (state == 2) {
                    entry = "AUTH";
                    msgServer();
                    if (response.length() >= 16 && response.substring(0,16).equals("334 dXNlcm5hbWU6")) {
                        System.out.println("Started authentication.");
                        System.out.print("Username: ");
                        entry = input.readLine();
                        state = 3;
                    }
                    else {
                        System.out.println("Failed authentication request. Press enter to try again.");
                        entry = input.readLine();
                    }
                }
                // Sending encoded Username
                else if (state == 3) {
                    username = entry;
                    encodedUsername = Base64.getEncoder().encodeToString(username.getBytes());
                    entry = encodedUsername;
                    msgServer();
                    // cass 1: user is already registered, server asks for password
                    if (response.length() >= 16 && response.substring(0,16).equals("334 cGFzc3dvcmQ6")) {
                        System.out.print("Password: ");
                        entry = input.readLine();
                        state = 4;
                    }
                    // case 2: user is now registered, password is received
                    else if (response.length() >= 3 && response.substring(0,3).equals("330")) {
                        System.out.println("You are registered as a new user, your password is: " + response.substring(4));
                        System.out.println("Connection ended, waiting 5 seconds for reconnect...");
                        TimeUnit.SECONDS.sleep(5);
                        username = "";
                        encodedUsername = "";
                        state = 0;
                    }
                }
                // Sending encoded Password
                else if (state == 4) {
                    password = entry;
                    encodedPassword = Base64.getEncoder().encodeToString(password.getBytes());
                    entry = encodedPassword;
                    msgServer();
                    // case 1: authorization successful
                    if (response.length() >= 3 && response.substring(0,3).equals("235")) {
                        System.out.println("Authorization Successful.");
                        System.out.print("How many emails to download?: ");
                        entry = input.readLine();
                        state = 5;
                    }
                    // case 2: authorization failed
                    else if (response.length() >= 3 && response.substring(0,3).equals("535")) {
                        System.out.println("Authorization failed, please try again.");
                        System.out.print("Password: ");
                        entry = input.readLine();
                    }
                }
                // HTTP Request message
                else if (state == 5) {
                    // try case: int has been entered
                    try {
                        int count = Integer.parseInt(entry.trim());
                        entry = ("GET /db/" + username + "/ HTTP/1.1" + System.lineSeparator()
                            + "Host: <" + serverHostname + ">" + System.lineSeparator()
                            + "Count: " + count);
                        msgServer();
                        // response successful
                        if (response.length() >= 15 && response.substring(0,15).equals("HTTP/1.1 200 OK"))
                        {
                            // save response as .txt
                            String timeStamp = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date());
                            try {
                                BufferedWriter out = new BufferedWriter(
                                        new FileWriter(System.getProperty("user.dir")
                                            + File.separator+"db" 
                                            + File.separator+ username
                                            + File.separator+ "Emails retrieved at " + timeStamp
                                            + ".txt", true));
                                out.write(response);
                                out.close();
                                System.out.println("Emails retrieved and stored in /db/username folder");
                            } catch (IOException ex) {
                                System.out.println("Emails retrieved but could not create email file");
                            }
                            System.out.print("How many emails to download?: ");
                            entry = input.readLine();
                        }
                        // bad response
                        else {
                            System.out.println("Server says: " + response.substring(13));
                            System.out.print("How many emails to download?: ");
                            entry = input.readLine();
                        }
                    }
                    // catch case: int hasn't been entered
                    catch (NumberFormatException nfe) {
                        System.out.println("Invalid entry, expecting a number.");
                        System.out.print("How many emails to download?: ");
                        entry = input.readLine();
                    }

                }
            }
        }
        clientSocket.close();
        System.out.println("Client has stopped...");
    }

    private static void msgServer() throws Exception {
        sendData = new byte[1024];
        sendData = entry.getBytes();
        sendPacket = new DatagramPacket(
            sendData, sendData.length, InetAddress.getByName(serverHostname), serverPort);
        clientSocket.send(sendPacket);
        receiveData = new byte[1024];
        receivePacket = new DatagramPacket(receiveData, receiveData.length);
        clientSocket.receive(receivePacket);
        response = new String(receivePacket.getData());
    }
}