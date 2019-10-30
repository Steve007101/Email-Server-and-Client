
/**
 * @author Steven Perry
 * @version 4/30/2019
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
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;


public class SMTPClient
{
    private static Timestamp ts;
    private static InetAddress myAddress;
    private static int serverPort;
    private static String clientIP, serverHostname, path, entry, response;
    private static String encodedUsername, encodedPassword, username, password, body, temp;
    private static Socket s;
    private static DataInputStream dis; 
    private static DataOutputStream dos;

    public static void main (String args[]) throws IOException
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
        
        System.setProperty("javax.net.ssl.trustStore", "selfsigned.jks");
        System.setProperty("javax.net.ssl.trustStorePassword", "cs447.edu");
        SSLSocketFactory ssf = (SSLSocketFactory) SSLSocketFactory.getDefault();
        

        try {
            myAddress = InetAddress.getByName("localhost");

            BufferedReader input =
                new BufferedReader(new InputStreamReader(System.in));
                
            s = ssf.createSocket(serverHostname, serverPort);

            dis = new DataInputStream(s.getInputStream()); 
            dos = new DataOutputStream(s.getOutputStream());

            boolean running = true;
            int state = 0;
            entry = "";
            System.out.println("SMTP Client is starting...");
            System.out.println("*Type QUIT to close connection and client at any time aside from Email contents*");
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
                            System.out.println("Authorization Successful. Enter email details.");
                            System.out.print("From: ");
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
                    // MAIL FROM message
                    else if (state == 5) {
                        // case 1: email not of correct form
                        if (!((entry.length() >= (username.length()+10)) && (entry.equals(username + "@cs447.edu")))) {
                            System.out.println
                            ("From email doesn't match login, must be of form username@cs447.edu");
                            System.out.print("From: ");
                            entry = input.readLine();
                        }
                        // case 2: correct entry
                        else {
                            temp = "MAIL FROM " + entry;
                            entry = temp;
                            msgServer();
                            // case 1: mail from accepted
                            if (response.length() >= 3 && response.substring(0,3).equals("250")) {
                                System.out.print("To: ");
                                entry = input.readLine();
                                state = 6;
                            }
                            else {
                                System.out.println("Server rejected entry.");
                                System.out.print("From: ");
                                entry = input.readLine();
                            }
                        }
                    }
                    // RCPT TO message
                    else if (state == 6) {
                        Pattern pattern = Pattern.compile("^(.+)@cs447.edu$");
                        Matcher matcher = pattern.matcher(entry);
                        // case 1: email not of correct form
                        if (!matcher.matches()) {
                            System.out.println("To email must be of form example@cs447.edu");
                            System.out.print("To: ");
                            entry = input.readLine();
                        }
                        else {
                            temp = "RCPT TO " + entry;
                            entry = temp;
                            msgServer();
                            // case 1: rcpt to accepted
                            if (response.length() >= 3 && response.substring(0,3).equals("250")) {
                                System.out.println("Enter email contents, last line containing the phrase 'end of email' by itself:");
                                body = "";
                                while(!(temp = input.readLine()).equals("end of email")) {
                                    body += (temp + System.lineSeparator());
                                }
                                state = 7;
                            }
                            // case 2: rejected
                            else {
                                System.out.println("Server rejected entry.");
                                System.out.print("To: ");
                                entry = input.readLine();
                            }
                        }

                    }
                    // DATA message
                    else if (state == 7) {
                        // no need to check if invalid since email body can have anything
                        entry = ("DATA " + body + System.lineSeparator() + "." + System.lineSeparator());
                        msgServer();
                        if (response.length() >= 3 && response.substring(0,3).equals("250")) {
                            System.out.println("Email accepted. You may enter details for another email.");
                            System.out.print("From: ");
                            entry = input.readLine();
                            state = 5;
                        }
                        // if not accepted, need to enter again
                        else {
                            System.out.println("Server rejected entry.");
                            System.out.println("Enter email contents, last line containing the phrase 'end of email' by itself:");
                            body = "";
                            while(!(temp = input.readLine()).equals("end of email")) {
                                body += (temp + System.lineSeparator());
                            }
                        }
                    }
                }
            }
            s.close();
        }catch(Exception e){ 
            e.printStackTrace(); 
        } 
        System.out.println("Client has stopped...");
    }

    private static void msgServer() throws Exception {
        dos.writeUTF(entry);
        response = dis.readUTF();

    }
}