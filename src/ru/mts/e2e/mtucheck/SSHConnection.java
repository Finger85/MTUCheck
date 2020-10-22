package ru.mts.e2e.mtucheck;

import java.util.Properties;
import com.jcraft.jsch.*;

/**
 * Класс для получения SSH подключения к необходимым ММЕ для выполнения команд.
 */
public class SSHConnection {
    /**Session к конкретному ММЕ*/
    private Session session;
    /**ChannelShell к конкретному ММЕ*/
    private ChannelShell channel;
    /**Логин для подключения по SSH к ММЕ*/
    private String login;
    /**Пароль для подключения по SSH к ММЕ*/
    private String password;
    /**IP адрес ММЕ*/
    private String ip;

    /**
     * Конструктор SSH, получающий на вход логин/пароль и ip адрес ММЕ для установления подключения. На этом этапе создается session.
     * @param ip IP адрес MME
     * @param login Логин
     * @param password Пароль
     * @throws JSchException Метод может бросить исключение SSH
     */
    public SSHConnection(String ip, String login, String password) throws JSchException {
        this.ip = ip;
        this.login = login;
        this.password = password;
        session = connect(this.ip, this.login, this.password);
    }

    /**
     * Метод возвращает Session на указанный хост с переданными логином/паролем.
     * @param ip IP адрес MME
     * @param login Логин
     * @param password Пароль
     * @return Session к указанному IP адресу ММЕ.
     * @throws JSchException Метод может бросить исключение SSH
     */
    private Session connect(String ip, String login, String password) throws JSchException {
        JSch jSch = new JSch();
        try {
            session = jSch.getSession(login, ip, 22);
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.setPassword(password);
            MainMTU.logger.info("Connecting SSH to ip={}. Please wait a second... ", ip);
            session.connect();
            MainMTU.logger.info("DONE! SSH connection to ip={} established", ip);
        }catch(JSchException e){
            throw new JSchException ("An error occurred while connecting to "+ ip + ": " + e.getMessage());
        }
        return session;
    }

    /**
     * Метод возвращает Channel по ранее поднятой session.
     * @return Channel к подключению.
     * @throws JSchException Метод может бросить исключение SSH
     */
    public Channel getChannel() throws JSchException {
        if(channel == null || !channel.isConnected()){
            try{
                channel = (ChannelShell)session.openChannel("shell");
                channel.connect();
                MainMTU.logger.info("Commands can be executed on the host (ip={}).", ip);
            }catch(JSchException e){
                throw new JSchException ("Error while opening channel: " + e.getMessage());
            }
        }
        return channel;
    }

    /**
     * Метод корректно закрывает созданные session и channel SSH.
     */
    public void close() {
        try {
            channel.disconnect();
            session.disconnect();
            MainMTU.logger.info("SSH channel and session disconnected (ip={}).", ip);
        } catch (Throwable e) { /*Do nothing*/ }
    }
}