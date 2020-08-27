package ru.mts.e2e.mtucheck;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.JSchException;
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

    public static void main(String[] args) {
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
        } catch (SQLException e) {
            System.out.println("Failed to establish a connection to the DataBase" + e.getMessage());
        }

        Map<Integer, Map<String, String>> sshSetting = SSHSettings.getSetting();
        for (Map.Entry<Integer, Map<String, String>> sshSettingEntry : sshSetting.entrySet()) {

            Thread thread = new Thread(() -> {

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
                    System.out.println("SSH connection error: " + e.getMessage());
                    sshConnection.close();
                } catch (IOException e) {
                    System.out.println("Getting in/output stream fail: " + e.getMessage());
                    sshConnection.close();
                }

                try (Connection connectionMySQLInThread = DBConnection.getConnection(DBType.MYSQL);
                     PreparedStatement statement = connectionMySQLInThread.prepareStatement(GET_ENB_BY_MR, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_UPDATABLE)) {

                    statement.setInt(1, mr_id);
                    ResultSet resultSet = statement.executeQuery();
                    connectionMySQLInThread.setAutoCommit(false);
                    while (resultSet.next()) {
                        long enb_last_check = resultSet.getLong("last_check");
                        EnodebStatus enb_status = EnodebStatus.valueOf(resultSet.getString("status"));

                        if ((enb_status == EnodebStatus.GOOD && (checkingDate - enb_last_check > DAY_IN_MS * 180)) ||
                                (enb_status == EnodebStatus.DOWN && (checkingDate - enb_last_check > DAY_IN_MS)) ||
                                (enb_status == EnodebStatus.BAD && (checkingDate - enb_last_check > DAY_IN_MS * 6)) ||
                                (enb_status == EnodebStatus.NEW)) {
                            String enb_ip = resultSet.getString("enb_ip");
                            String enb_id = resultSet.getString("enb_id");
                            int enb_mr_id = resultSet.getInt("mr_id");
                            String mme_ip = mmeIpMap.get(enb_mr_id);
                            EnodebStatus enodebStatus = eNBChecker.check(new eNB(enb_id, enb_ip, mme_ip, enb_last_check, enb_status));
                            System.out.println("ENB " + enb_ip + " STATUS is " + enodebStatus);
                            try {
                                resultSet.updateString("status", enodebStatus.toString());
                                resultSet.updateLong("last_check", checkingDate);
                                resultSet.updateRow();
                                connectionMySQLInThread.commit();
                            } catch (SQLException e) {
                                System.out.println("Failed to update a record of ENB " + enb_ip + " with new STATUS - " + enodebStatus + " due to SQL Exception" + e.getMessage());
                            }
                        }
                    }
                    connectionMySQLInThread.commit();
                } catch (SQLException e) {
                    System.out.println("Failed to establish a connection to the DataBase" + e.getMessage());
                } catch (IOException e) {
                    System.out.println("Fail to read output from a console" + e.getMessage());
                }
                sshConnection.close();
            });
            thread.start();
        }
    }
}