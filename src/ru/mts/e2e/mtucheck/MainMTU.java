package ru.mts.e2e.mtucheck;


import com.jcraft.jsch.Channel;
import com.jcraft.jsch.JSchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.io.IOException;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class MainMTU {
    private final static String GET_ENB_BY_MR =
            "SELECT " +
                    "id, " +
                    "ip as enb_ip, " +
                    "enb_id, " +
                    "status, " +
                    "mr_id, " +
                    "last_check " +
                    "FROM enb " +
                    "WHERE mr_id = ?";

    private final static String GET_MMES_IP = "SELECT mr_id, ip FROM mme_view";
    private final static long DAY_IN_MS = 86_400_000;
    private final static long HOUR_IN_MS = 3_600_000;
    private final static long currentTime = System.currentTimeMillis();
    public static final Logger logger = LoggerFactory.getLogger(MainMTU.class);

    public static void main(String[] args) {

        logger.debug("MainMTU process is started");
        final long checkingDate = currentTime - currentTime % DAY_IN_MS - HOUR_IN_MS * 3;

        UpdateEnbTable.doUpdate();

        Map<Integer, String> mmeIpMap = new HashMap<>();
        try (Connection connection = DBConnection.getConnection(DBType.MYSQL);
             Statement statement = connection.createStatement()) {
            ResultSet resultSet = statement.executeQuery(GET_MMES_IP);
            while (resultSet.next()) {
                int mr_id = resultSet.getInt("mr_id");
                String ip = resultSet.getString("ip");
                mmeIpMap.put(mr_id, ip);
            }
            logger.debug("Map(mmeIpMap) has been created. Values: {}" , mmeIpMap.toString());
        } catch (SQLException e) {
            logger.error("Map(mmeIpMap) has not been created. Failed to establish a connection to the DataBase ({}) : {}.", DBType.MYSQL.toString(), e.getMessage());
        }

        Map<Integer, Map<String, String>> sshSetting = SSHSettings.getSetting();
        for (Map.Entry<Integer, Map<String, String>> sshSettingEntry : sshSetting.entrySet()) {

            Thread thread = new Thread(() -> {

                long start = System.currentTimeMillis();
                long finish;
                Map<String, String> sshSettingValue = sshSettingEntry.getValue();
                int mr_id = sshSettingEntry.getKey();
                String host_ip = sshSettingValue.get("ip");
                String host_login = sshSettingValue.get("login");
                String host_password = sshSettingValue.get("password");

                SSHConnection sshConnection = null;
                eNBChecker eNBChecker = null;
                try {
                    sshConnection = new SSHConnection(host_ip, host_login, host_password);
                    Channel channel = sshConnection.getChannel();
                    eNBChecker = new eNBChecker(channel);
                } catch (JSchException e) {
                    logger.error("Fail to establish ssh connection to a host ({}). Error message: {}", host_ip, e.getMessage());
                    sshConnection.close();
                } catch (IOException e) {
                    logger.error("Fail to get input/output stream to a host ({}). Error message: {}", host_ip, e.getMessage());
                    sshConnection.close();
                }

                try (Connection connectionMySQLInThread = DBConnection.getConnection(DBType.MYSQL);
                     PreparedStatement statement = connectionMySQLInThread.prepareStatement(GET_ENB_BY_MR, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_UPDATABLE)) {

                    statement.setInt(1, mr_id);
                    ResultSet resultSet = statement.executeQuery();
                    connectionMySQLInThread.setAutoCommit(false);
                    while (resultSet.next()) {
                        long enb_last_check = resultSet.getLong("last_check");
                        EnodebStatus enbStatusOld = EnodebStatus.valueOf(resultSet.getString("status"));

                        if ((enbStatusOld == EnodebStatus.GOOD && (checkingDate - enb_last_check > DAY_IN_MS * 180)) ||
                                (enbStatusOld == EnodebStatus.DOWN && (checkingDate - enb_last_check > DAY_IN_MS * 6)) ||
                                (enbStatusOld == EnodebStatus.BAD && (checkingDate - enb_last_check > DAY_IN_MS * 6)) ||
                                (enbStatusOld == EnodebStatus.NEW)) {
                            String enb_ip = resultSet.getString("enb_ip");
                            String enb_id = resultSet.getString("enb_id");
                            int enb_mr_id = resultSet.getInt("mr_id");
                            String mme_ip = mmeIpMap.get(enb_mr_id);
                            logger.debug("eNB (ip address={}) is previous status ={} is checking...", enb_ip, enbStatusOld.toString());
                            EnodebStatus enbStatusNew = eNBChecker.check(new eNB(enb_id, enb_ip, mme_ip, enb_last_check, enbStatusOld));

                            try {
                                if (enbStatusNew==EnodebStatus.DOWN && enbStatusOld==EnodebStatus.BAD) {
                                    resultSet.updateLong("last_check", checkingDate);
                                    resultSet.updateRow();
                                    connectionMySQLInThread.commit();
                                    logger.debug("eNB status (ip address={}) is {}", enb_ip, EnodebStatus.BAD.toString());
                                } else {
                                    resultSet.updateString("status", enbStatusNew.toString());
                                    resultSet.updateLong("last_check", checkingDate);
                                    resultSet.updateRow();
                                    connectionMySQLInThread.commit();
                                    if (enbStatusNew != enbStatusOld) {
                                        logger.info("eNB status (ip address={}) has been changed from {} to {}", enb_ip, enbStatusOld.toString(), enbStatusNew.toString());
                                    } else logger.debug("eNB status (ip address={}) is {}", enb_ip, enbStatusNew.toString());
                                }

                            } catch (SQLException e) {
                                logger.error("Failed to update a record of ENB (ip address={}) with new STATUS={} due to SQL Exception: {}", enb_ip, enbStatusNew.toString(), e.getMessage());
                            }
                        }
                    }
                    connectionMySQLInThread.commit();
                } catch (SQLException e) {
                    logger.error("Failed to establish a connection to the DataBase({}) : {}.", DBType.MYSQL.toString(), e.getMessage());
                } catch (IOException e) {
                    logger.error("Failed to read output from a host console (ip address={}). Error message: {}.", host_ip, e.getMessage());
                }
                sshConnection.close();
                finish = System.currentTimeMillis();
                logger.info("SSH connection to the host (ip address={}) is closed. Thread TID_{} is competed within {}sec.", host_ip, mr_id, (finish-start)/1000);
            });
            thread.setName("TID_" + sshSettingEntry.getKey());
            thread.start();
            logger.info("Thread with name \"{}\" is started. Thread state is {}", thread.getName(), thread.getState());
        }
    }
}