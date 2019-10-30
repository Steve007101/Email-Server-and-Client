
/**
 *
 * @author Steven Perry
 * @version 4/4/2019
 */

import java.io.*;
import java.net.*;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Pattern;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HTTPResponder implements Runnable
{
    private Timestamp ts;

    DatagramSocket socket = null;
    DatagramPacket packet = null;
    String serverIP;
    String serverHostName;
    InetAddress clientAddress;
    String clientStringAddress;
    String clientHostName;
    int clientPort;
    int count;

    public HTTPResponder(DatagramSocket socket, DatagramPacket packet, String serverIP, String serverHostName) {
        this.socket = socket;
        this.packet = packet;
        this.serverIP = serverIP;
        this.serverHostName = serverHostName;
        this.clientAddress = packet.getAddress();
        this.clientStringAddress = this.clientAddress.getHostAddress();
        this.clientHostName = this.clientAddress.getHostName();
        this.clientPort = packet.getPort();
    }

    public void run() {

        String recieved, response;
        // get message from packet
        recieved = new String(packet.getData(), 0, packet.getLength());

        // log incoming message
        writeLog(clientAddress.getHostAddress(), serverIP, recieved);

        // process response
        response = processResponse(recieved, clientAddress.getHostName());

        // turn response into packet
        byte[] buf = new byte[1024];
        buf = response.getBytes();
        packet = new DatagramPacket(buf, buf.length, clientAddress, clientPort);

        //if (response.equals("221 Connection Closed"))
        //{
        //    running = false;
        //    continue;
        //}

        // send packet
        try {
            socket.send(packet);
        } catch (IOException e) {
            System.out.println("failed to send packet");
        }
        // log outgoing message
        writeLog(serverIP, clientAddress.getHostAddress(), response);
    }

    private String processResponse(String theInput, String clientName)
    {
        String theOutput = "unsuccessful processing";
        int state = 0;
        String username = "";
        String receivUser = "";

        // First we check if the connected client has already been registered
        File dbFile = new File(System.getProperty("user.dir")+ 
                File.separator+"db" +File.separator+ clientName + "HTTP.txt");
        if (dbFile.exists())
        {
            try {
                // if we have a previous record, we load the client state and username
                BufferedReader in = new BufferedReader(new FileReader(dbFile));
                state = Integer.parseInt(in.readLine());
                username = in.readLine();
                receivUser = in.readLine();
                in.close();
            } catch (IOException e) {
                System.out.println("failed to read from client database file");
            }

        }
        // if the client wasn't returning it's a new client and we make a new file for it
        else {
            try { 
                dbFile.createNewFile();
                BufferedWriter out = new BufferedWriter(
                        new FileWriter(dbFile, false));
                out.write(Integer.toString(state));
                out.close();
            }
            catch (IOException e) {
                System.out.println("failed to create client database file");
            }
        }

        // First we consider a QUIT message, under any state
        if (theInput.length() >= 4 && theInput.substring(0,4).equals("QUIT")) { 
            theOutput = "221 Connection Closed";
            state = 0;
            username = "";
            receivUser = "";
        }
        // If first message, respond with 220
        else if (state == 0) {
            ts = new Timestamp(System.currentTimeMillis());
            theOutput = "220 " + serverHostName + " simple SMTP program ready at " + ts + "CST";
            state = 1;
        }
        // Expecting HELO
        else if (state == 1) {
            if (theInput.length() >= 4 
            && theInput.substring(0, 4).equals("HELO")) {
                theOutput = "250 " + serverHostName + " Hello " + theInput.substring(4) + ", pleased to meet you";
                state = 2;
            }
            else {
                if (theInput.length() >= 4 && (theInput.substring(0, 4).equals("AUTH") || theInput.substring(0, 4).equals("DATA")))
                    theOutput = "503 Bad Sequence, Expecting HELO";
                else if (theInput.length() >= 9 && theInput.substring(0, 9).equals("MAIL FROM"))
                    theOutput = "503 Bad Sequence, Expecting HELO";
                else if (theInput.length() >= 7 && theInput.substring(0, 7).equals("RCPT TO"))
                    theOutput = "503 Bad Sequence, Expecting HELO";
                else
                    theOutput = "500 Command Unrecognized, expecting HELO";
            }
        }
        // Expecting AUTH
        else if (state == 2) {
            if (theInput.length() >= 4 
            && theInput.substring(0, 4).equals("AUTH")) {
                // ask for username
                theOutput = "334 dXNlcm5hbWU6";
                state = 3;
            }
            else {
                if (theInput.length() >= 4 && (theInput.substring(0, 4).equals("HELO") || theInput.substring(0, 4).equals("DATA")))
                    theOutput = "503 Bad Sequence, Expecting AUTH";
                else if (theInput.length() >= 9 && theInput.substring(0, 9).equals("MAIL FROM"))
                    theOutput = "503 Bad Sequence, Expecting AUTH";
                else if (theInput.length() >= 7 && theInput.substring(0, 7).equals("RCPT TO"))
                    theOutput = "503 Bad Sequence, Expecting AUTH";
                else
                    theOutput = "500 Command Unrecognized, expecting AUTH";
            }
        }
        // Expecting Username in base 64
        // Need to handle if it's a new user as well here
        else if (state == 3) {
            // decode base64 input
            String decodedString = new String(Base64.getDecoder().decode(theInput));
            // set sentinel value
            boolean userExists = false;
            // check if user folder exists
            File udbFolder = new File(System.getProperty("user.dir")+ File.separator+"db" +File.separator+ decodedString);            
            if (udbFolder.exists() && udbFolder.isDirectory())
            {
                // the user might have a folder created from mail being sent to them
                // but might not yet be an authorized user, need to check password file
                File passFile = new File(System.getProperty("user.dir")+ File.separator+"db" +File.separator+".user_pass");
                if (passFile.exists()) {
                    try {
                        // password file exists, cycle through password file to find if user is registered
                        BufferedReader in = new BufferedReader(new FileReader(passFile));
                        String str;
                        while (!userExists && ((str=in.readLine())!=null))
                        {
                            String[] strArr = (str.split(" "));
                            if (decodedString.equals(strArr[0])) {
                                userExists = true;
                                theOutput = "334 cGFzc3dvcmQ6";
                                state = 4;
                                username = decodedString;
                            }
                        }
                        in.close();
                    } catch (IOException e) {
                        System.out.println("failed to read from password database file");
                    }
                }
            }
            // if the user doesn't exist, we register them
            if (!userExists) {
                // make user folder, but only if it doesn't already exist
                // they could still have something sent to them but not 
                // yet be a registered user
                if (!udbFolder.exists())
                {
                    (new File(udbFolder+File.separator)).mkdir();
                }
                // generate random 5 digit password
                SecureRandom random = new SecureRandom();
                String newPassword = String.format("%05d", random.nextInt(100000)); 
                // send client 5 digit password, reset state and username entry
                theOutput = "330 " + newPassword;
                state = 0;
                username = "";
                receivUser = "";
                // store password with +447, base 64
                String modPassword = Integer.toString(Integer.parseInt(newPassword) + 447);
                String encodedPassword = Base64.getEncoder().encodeToString(modPassword.getBytes());
                // write to hidden master password file (the dot makes it hidden on linux, will not be tested on windows)
                File passFile = new File(System.getProperty("user.dir")+ File.separator+"db" +File.separator+".user_pass");
                try { 
                    BufferedWriter out = new BufferedWriter(
                            new FileWriter(passFile, true)); // append if other passwords already exist
                    out.write(decodedString + " " + encodedPassword + System.lineSeparator());
                    out.close();
                }
                catch (IOException e) {
                    System.out.println("failed to write to password database file");
                }
            }
            /*
            else {
            if (theInput.length() >= 4 && (theInput.substring(0, 4).equals("HELO") || theInput.substring(0, 4).equals("DATA")
            || theInput.substring(0, 4).equals("AUTH")))
            theOutput = "503 Bad Sequence, expecting email in form example@cs447.edu in base64";
            else if (theInput.length() >= 9 && theInput.substring(0, 9).equals("MAIL FROM"))
            theOutput = "503 Bad Sequence, expecting email in form example@cs447.edu in base64";
            else if (theInput.length() >= 7 && theInput.substring(0, 7).equals("RCPT TO"))
            theOutput = "503 Bad Sequence, expecting email in form example@cs447.edu in base64";
            else
            theOutput = "500 Command Unrecognized, expecting email in form username@cs447.edu in base64";
            }
             */

        }
        // Expecting Password in base64
        else if (state == 4) {
            // decode base64 message, add 447, encode back to check database
            String receivedPass = new String(Base64.getDecoder().decode(theInput));
            String modPassword = Integer.toString(Integer.parseInt(receivedPass) + 447);
            String decodedPass = Base64.getEncoder().encodeToString(modPassword.getBytes());
            // set sentinel value
            boolean passwordMatch = false;
            // check against password database
            File passFile = new File(System.getProperty("user.dir")+ File.separator+"db" +File.separator+".user_pass");
            if (passFile.exists()) {
                try {
                    // password file exists, cycle through password file to find if password for user is correct
                    BufferedReader in = new BufferedReader(new FileReader(passFile));
                    String str;
                    while (!passwordMatch && ((str=in.readLine())!=null))
                    {
                        String[] strArr = (str.split(" "));
                        if (username.equals(strArr[0])) {
                            if (decodedPass.equals(strArr[1]))
                            {
                                passwordMatch = true;
                                theOutput = "235 Authorization Successful";
                                state = 5;
                            }

                        }
                    }
                    in.close();
                } catch (IOException e) {
                    System.out.println("failed to read from password database file");
                }
            }
            if (!passwordMatch) {
                theOutput = "535 Authorization Failed, please try again";
            }

        }
        // Expecting HTTP Request
        else if (state == 5) {
            if (theInput.length() >= (34+username.length()+serverHostName.length()) && theInput.substring(0, 8).equals("GET /db/")
            && (theInput.indexOf(username) == (8)) && (theInput.indexOf("/ HTTP/1.1") == (8+username.length()))
            && (theInput.indexOf("Host: <") != -1) && (theInput.indexOf(serverHostName) == (theInput.indexOf("Host: <")+7))
            && (theInput.indexOf("Count: ") != -1)) {
                boolean success = true;
                // attempt to read count from request, might not be number or actually there
                try {
                    count = Integer.parseInt(theInput.substring(theInput.indexOf("Count: ")+7));
                } catch (Exception e) {
                    success = false;
                }
                if (success)
                {
                    // now we need to check how many emails there are for this user
                    int emailNum = 0;
                    File emailNumPath = new File(System.getProperty("user.dir")+ File.separator+"db" +File.separator+ username
                            +File.separator+ ".email_Num");            
                    if (emailNumPath.exists())
                    {
                        try {
                            // if we have a previous record, we load the current email num
                            BufferedReader in = new BufferedReader(new FileReader(emailNumPath));
                            emailNum = Integer.parseInt(in.readLine());
                            in.close();
                        } catch (IOException e) {
                            System.out.println("failed to read from email number file");
                        }
                    }
                    // if count less than 0, bad request
                    if (count < 0)
                        theOutput = "HTTP/1.1 400 Bad Request";
                    else {
                        // if count more than emails total, file not found
                        if (count > emailNum)
                            theOutput = "HTTP/1.1 404 File Not Found";
                        else
                        {
                            int emailstoget = count;
                            // finally we put together the emails
                            String emails, temp;
                            emails = "";
                            // try catch in case the email files themselves aren't found
                            try {
                                for (int i = 0; i < emailstoget; i++)
                                {
                                    BufferedReader in = new BufferedReader(new FileReader(System.getProperty("user.dir")
                                                + File.separator+"db" 
                                                + File.separator+ username
                                                + File.separator+ String.format("%03d", (emailNum-i)) + ".email"));
                                    emails += ("Message: " + (emailNum-i) + System.lineSeparator());
                                    while ((temp = in.readLine()) != null) {
                                        emails += (temp + System.lineSeparator());
                                    }
                                    in.close();
                                }
                                // and now we make the message and send it
                                ts = new Timestamp(System.currentTimeMillis());
                                theOutput = "HTTP/1.1 200 OK" + System.lineSeparator()
                                + "Server: <" + serverHostName + ">" + System.lineSeparator()
                                + "Last-Modified: " + ts + System.lineSeparator()
                                + "Count: " + count + System.lineSeparator()
                                + "Content-Type: text/plain" + System.lineSeparator()
                                + "Messages in Inbox: " + emailNum + System.lineSeparator()
                                + emails;
                            } catch (IOException e) {
                                theOutput = "HTTP/1.1 404 File Not Found";
                            }
                        }
                    }
                }
                else {
                    theOutput = "HTTP/1.1 400 Bad Request";
                }
            }
            else {
                theOutput = "HTTP/1.1 400 Bad Request";
            }
        }
        // write ending state, username and receivUser to client db file
        try { 
            BufferedWriter out = new BufferedWriter(
                    new FileWriter(dbFile, false));
            out.write(Integer.toString(state) 
                + System.lineSeparator() + username
                + System.lineSeparator() + receivUser);
            out.close();
        }
        catch (IOException e) {
            System.out.println("failed to write to client database file");
        }
        // return response
        return theOutput;
    }

    private void writeLog(String fromIP, String toIP, String msg)
    {
        Timestamp ts = new Timestamp(System.currentTimeMillis());
        String logMsg = (ts + " CST " + fromIP + " " + toIP + " UDP " + msg);
        try { 
            BufferedWriter out = new BufferedWriter(
                    new FileWriter(".server_log", true));
            out.write(logMsg + System.lineSeparator());
            out.close();
        }
        catch (IOException e) {
            System.out.println("failed to write to log file");
        }
        System.out.println("[serverlog] " + logMsg);

    }
}

