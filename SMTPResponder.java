
/**
 *
 * @author Steven Perry
 * @version 4/30/2019
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

public class SMTPResponder extends Thread
{
    private Timestamp ts;

    Socket socket = null;
    String serverIP, serverHostName, clientAddress, clientHostName,
    received, response;
    int clientPort;
    final DataInputStream dis; 
    final DataOutputStream dos; 
    
    boolean running = true;
    String username = "";
    String receivUser = "";
    int state = 0;

    public SMTPResponder(Socket socket, DataInputStream dis, DataOutputStream dos, String serverIP, String serverHostName) {
        super();
        this.socket = socket;
        this.dis = dis; 
        this.dos = dos; 
        this.serverIP = serverIP;
        this.serverHostName = serverHostName;
        this.clientAddress = socket.getInetAddress().getHostAddress();
        this.clientHostName = socket.getInetAddress().getHostName();

    }

    public void run() {

        System.out.println("Thread Accepted Client " + clientHostName);
        try {
            while (running) {
                // get message from client
                received = dis.readUTF();


                // log incoming message
                writeLog(clientAddress, serverIP, received);

                // process response
                response = processResponse(received, clientHostName);

                // send message to client
                dos.writeUTF(response);
                // log outgoing message
                writeLog(serverIP, clientAddress, response);
            }
            this.dis.close();
            this.dos.close();
        } catch(IOException e){ 
            e.printStackTrace(); 
        } 
        System.out.println("Thread Closed Client " + clientHostName);
    }

    private String processResponse(String theInput, String clientName)
    {
        String theOutput = "unsuccessful processing";

        // First we consider a QUIT message, under any state
        if (theInput.length() >= 4 && theInput.substring(0,4).equals("QUIT")) { 
            theOutput = "221 Connection Closed";
            running = false;
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
        // Expecting MAIL FROM
        else if (state == 5) {
            if (theInput.length() >= 9 && theInput.substring(0, 9).equals("MAIL FROM")) {
                if ((theInput.length() >= (username.length()+20)) 
                && (theInput.substring(10).equals(username + "@cs447.edu")))
                {
                    theOutput = "250 <" + username + "cs447.edu>... Sender ok";
                    state = 6;
                }
                else {
                    theOutput = "501 Invalid Entry, expecting email of form username@cs447.edu";
                }
            }
            else {
                if (theInput.length() >= 4 && (theInput.substring(0, 4).equals("AUTH") || theInput.substring(0, 4).equals("DATA")
                    || theInput.substring(0, 4).equals("HELO")))
                    theOutput = "503 Bad Sequence, Expecting MAIL FROM";
                else if (theInput.length() >= 7 && theInput.substring(0, 7).equals("RCPT TO"))
                    theOutput = "503 Bad Sequence, Expecting MAIL FROM";
                else
                    theOutput = "500 Command Unrecognized, expecting MAIL FROM";
            }
        }
        // Expecting RCPT TO
        else if (state == 6) {
            if (theInput.length() >= 7 && theInput.substring(0, 7).equals("RCPT TO")) {
                Pattern pattern = Pattern.compile("^(.+)@cs447.edu$");
                Matcher matcher = pattern.matcher(theInput.substring(8));
                if (!matcher.matches())
                {
                    theOutput = "501 Invalid Entry, expecting email of form recipient@cs447.edu";
                }
                else
                {
                    receivUser = theInput.substring(8, theInput.indexOf("@"));
                    // make new folder for recipient if it doesn't exist already
                    File rudbFolder = new File(System.getProperty("user.dir")+ File.separator+"db" +File.separator+ receivUser);            
                    if (!rudbFolder.exists())
                    {
                        (new File(rudbFolder+File.separator)).mkdir();
                    }
                    theOutput = "250 <" + receivUser + "@cs447.edu>... Recipient ok";
                    state = 7;

                }
            }
            else {
                if (theInput.length() >= 4 && (theInput.substring(0, 4).equals("AUTH") || theInput.substring(0, 4).equals("DATA")
                    || theInput.substring(0, 4).equals("HELO")))
                    theOutput = "503 Bad Sequence, Expecting RCPT TO";
                else if (theInput.length() >= 9 && theInput.substring(0, 9).equals("MAIL FROM"))
                    theOutput = "503 Bad Sequence, Expecting RCPT TO";
                else
                    theOutput = "500 Command Unrecognized, expecting RCPT TO";
            }
        }
        // Expecting DATA
        else if (state == 7) {
            if (theInput.length() >= 4 && theInput.substring(0, 4).equals("DATA")) {
                // check if correct end of email present
                // int end = theInput.lastIndexOf(".");
                int end = theInput.lastIndexOf(System.lineSeparator() + "." + System.lineSeparator());
                if (end == -1)
                {
                    theOutput = "501 Invalid Entry, enter email ending with '.' on a line by itself";
                }
                else
                {
                    // finally we create the email
                    ts = new Timestamp (System.currentTimeMillis());
                    String email = ("Date: " + ts + " CST" + System.lineSeparator()
                            + "From: <" + username + "@cs447.edu>" + System.lineSeparator()
                            + "To: <" + receivUser + "@cs447.edu>" + System.lineSeparator()
                            + theInput.substring(5,end)
                        );
                    // now we need to check what receiver email num this is if not the first
                    int emailNum = 1;
                    File emailNumPath = new File(System.getProperty("user.dir")+ File.separator+"db" +File.separator+ receivUser
                            +File.separator+ ".email_Num");            
                    if (emailNumPath.exists())
                    {
                        try {
                            // if we have a previous record, we load the current email num and add 1
                            BufferedReader in = new BufferedReader(new FileReader(emailNumPath));
                            emailNum = (Integer.parseInt(in.readLine()))+1;
                            in.close();
                        } catch (IOException e) {
                            System.out.println("failed to read from email number file");
                        }
                    }
                    // now we write the current emailNum to email num file
                    try { 
                        BufferedWriter out = new BufferedWriter(
                                new FileWriter(emailNumPath, false));
                        out.write(Integer.toString(emailNum));
                        out.close();
                    }
                    catch (IOException e) {
                        System.out.println("failed to write to email number file");
                    }
                    // finally we store the email in the database
                    try {
                        BufferedWriter out = new BufferedWriter(
                                new FileWriter(System.getProperty("user.dir")
                                    + File.separator+"db" 
                                    + File.separator+ receivUser
                                    + File.separator+ String.format("%03d", emailNum)
                                    + ".email", false));
                        out.write(email);
                        out.close();
                    } catch (IOException ex) {
                        System.out.println("Could not create email file");
                    }

                    // send the Mail accepted message, reset receiver field
                    // and go back to MAIL TO stage
                    theOutput = "250 Mail accepted";
                    receivUser = "";

                    state = 5;
                }
            }
            else {
                if (theInput.length() >= 4 && (theInput.substring(0, 4).equals("AUTH") || theInput.substring(0, 4).equals("HELO")))
                    theOutput = "503 Bad Sequence, Expecting DATA";
                else if (theInput.length() >= 9 && theInput.substring(0, 9).equals("MAIL FROM"))
                    theOutput = "503 Bad Sequence, Expecting DATA";
                else if (theInput.length() >= 7 && theInput.substring(0, 7).equals("RCPT TO"))
                    theOutput = "503 Bad Sequence, Expecting DATA";
                else
                    theOutput = "500 Command Unrecognized, expecting DATA";
            }
        }

        // return response
        return theOutput;
    }

    private void writeLog(String fromIP, String toIP, String msg)
    {
        Timestamp ts = new Timestamp(System.currentTimeMillis());
        String logMsg = (ts + " CST " + fromIP + " " + toIP + " TCP " + msg);
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

