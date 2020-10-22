package ru.mts.e2e.mtucheck;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Formatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jcraft.jsch.*;

import javax.xml.bind.SchemaOutputResolver;

/**
 * Класс, где осуществялется выполенение команд по SSH на удаленном хосте. На основе полученного вывода делается вывод состоянии, в котором нахоится проверяемая eNB ({@link EnodebStatus}).
 */
public class eNBChecker {
    /**SSH channel к MME*/
    private Channel channel;
    /**OutputStream для channel*/
    private PrintStream out;
    /**InputStream для channel*/
    private InputStream in;
    /**Команда обычного ping*/
    private static final String PING = "ping %s src %s count 2";
    /**Команда ping большими пакетами*/
    private static final String PING_1472 = "ping %s src %s size 1472 count 2 df-bit on";
    /**Команда для переходи в нужный контекст ММЕ*/
    private static final String S1MMES11 = "context s1mmes11";
    /**Паттерн ожидаемого ответа*/
    private static final Pattern REPLY_PING_PATTERN = Pattern.compile("icmp_seq=\\d+ ttl=\\d+ time=\\d+.\\d+ ms");
    /**Флаг проверки обычным ping или большими пакетами без фрагментации. True - если идет проверка обычным ping, False - если идет проверка большими пакетами.*/
    private Boolean isFirstStage;

    /**
     * Конструктор принимает Channel и получает на его основе Input и OutputStream для выполнения команд и получения результат их выполнения.
     * @param ch Принимает в конструкторе Channel.
     * @throws IOException Конструктор бросает IOException
     */
    public eNBChecker(Channel ch) throws IOException {
        this.channel = ch;
        try {
            out = new PrintStream(channel.getOutputStream());
            in = channel.getInputStream();
        } catch (IOException e) {
            throw new IOException ("Can't get out/input streams for channel " + ch + ". Error message: "+  e.getMessage());
        }
    }

    /**
     * Основной метод, где осуществляется проверка переданного eNB на доступность больших пакетов в 1472 байта без возможности фрагментации. Выполнение команд, чтение результата, возврат статуса eNB после проверки.
     * @param enb Принимает на вход эксземпляр класса eNB ({@link eNB}) для анализа.
     * @return Возвращает статус ({@link EnodebStatus}) eNB после проверки.
     * @throws IOException Метод может бросить IOException.
     */
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

    /**
     * Метод выводит команды в SSH консоль MME.
     * @param command Передается строка команды.
     */
    private void sendCommand(String command) {
        out.println(command);
        out.flush();
    }

    /**
     * Метод читает полученный на консоль вывод после выполнения команды.
     * @return Возвращает строку, которая была выведена на консоль ММЕ после выполнения команды.
     * @throws IOException Метод может бросить IOException.
     */
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
                if (stringBuilder.toString().contains("[s1mmes11]")) {
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

    /**
     * Метод, где происходит непосредственный анализ вывода на консоль ММЕ после выполнение команды. На основе вывода, возвращается статус проверки eNB.
     * @param outputFromConsole Передается для анализа строка, полученная на консоль ММЕ после выполнения команды.
     * @return Возвращает EnodebStatus ({@link EnodebStatus}) после проверки.
     */
    private EnodebStatus parseOutput(String outputFromConsole) {
        Matcher reply = REPLY_PING_PATTERN.matcher(outputFromConsole);
        if (isFirstStage) {
           if (reply.find()) {
               isFirstStage = false;
               return EnodebStatus.GOOD;
           } else return EnodebStatus.DOWN;
        }

        if (reply.find()) {
            return EnodebStatus.GOOD;
        } else return EnodebStatus.BAD;
    }
}