package ru.mts.e2e.mtucheck;

import java.util.Properties;
import com.jcraft.jsch.*;

public class SSHConnection {

    private Session session;
    private ChannelShell channel;
    private String login;
    private String password;
    private String ip;

    public SSHConnection(String ip, String login, String password) throws JSchException {
        this.ip = ip;
        this.login = login;
        this.password = password;
        session = connect(this.ip, this.login, this.password);
    }

    private Session connect(String ip, String login, String password) throws JSchException {
        JSch jSch = new JSch();
        try {
            session = jSch.getSession(login, ip, 22);
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.setPassword(password);
            System.out.println("Connecting SSH to " + ip + " - Please wait for few seconds... ");
            session.connect();
            System.out.println("Connected!");
        }catch(JSchException e){
            throw new JSchException ("An error occurred while connecting to "+ ip + ": " + e.getMessage());
        }
        return session;
    }

    public Channel getChannel() throws JSchException {
        if(channel == null || !channel.isConnected()){
            try{
                channel = (ChannelShell)session.openChannel("shell");
                channel.connect();
                System.out.println("You can execute your commands on " + ip);
            }catch(JSchException e){
                throw new JSchException ("Error while opening channel: " + e.getMessage());
            }
        }
        return channel;
    }

    public void close() {
        try {
            channel.disconnect();
            session.disconnect();
            System.out.println("Disconnected channel and session to " + ip);
        } catch (Exception e) { /*Do nothing*/ }
    }
}