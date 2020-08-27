package ru.mts.e2e.mtucheck;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jcraft.jsch.*;


public class eNBChecker {
    private Channel channel;
    private PrintStream out;
    private InputStream in;
    private static final String PING = "ping %s src %s count 2";
    private static final String PING_1472 = "ping %s src %s size 1472 count 2 df-bit on";
    private static final String S1MMES11 = "context s1mmes11";
    private static final Pattern pattern = Pattern.compile("icmp_seq=\\d+ ttl=\\d+ time=\\d+.\\d+ ms");
    private Boolean isFirstStage;

    public eNBChecker(Channel ch) throws IOException {
        this.channel = ch;
        try {
            out = new PrintStream(channel.getOutputStream());
            in = channel.getInputStream();
        } catch (IOException e) {
            throw new IOException ("Can't get ssh chanel for " + ch + ". Error message: "+  e.getMessage());
        }
    }

    public EnodebStatus check(eNB enb) throws IOException {
        String outputFromConsole;
        sendCommand(S1MMES11);
        sendCommand(String.format(PING, enb.getIp(), enb.getMmeIP()));
        outputFromConsole = readOutput();
        isFirstStage = true;
        EnodebStatus stageStatus = parseOutput(outputFromConsole);

        if (stageStatus == EnodebStatus.GOOD) {
            sendCommand(String.format(PING_1472, enb.getIp(), enb.getMmeIP()));
            outputFromConsole = readOutput();
            stageStatus = parseOutput(outputFromConsole);
        }
        return stageStatus;
    }

    private void sendCommand(String command) {
        out.println(command);
        out.flush();
    }

    private String readOutput() throws IOException {
        byte[] buffer = new byte[1024];
        StringBuilder stringBuilder = new StringBuilder();
        try {
            while (true) {
                while (in.available() > 0) {
                    int i = in.read(buffer, 0, 1024);
                    if (i < 0) {
                        break;
                    }
                    stringBuilder.append(new String(buffer, 0, i));
                }
                if (stringBuilder.toString().contains("ping statistics")) {
                    break;
                }
                if (channel.isClosed()) {
                    break;
                }
                try {
                    Thread.sleep(1000);
                } catch (Exception ee) { /*Do nothing*/ }
            }
        } catch (IOException e) {
            throw new IOException ("Error while reading channel output: " + e.getMessage());
        }
        return stringBuilder.toString();
    }

    private EnodebStatus parseOutput(String outputFromConsole) {
        Matcher matcher = pattern.matcher(outputFromConsole);
        if (isFirstStage) {
           if (matcher.find()) {
               isFirstStage = false;
               return EnodebStatus.GOOD;
           } else return EnodebStatus.DOWN;
        }

        if (matcher.find()) {
            return EnodebStatus.GOOD;
        } else return EnodebStatus.BAD;
    }
}