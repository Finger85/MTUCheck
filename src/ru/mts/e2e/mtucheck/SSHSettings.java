package ru.mts.e2e.mtucheck;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

public class SSHSettings {
    private static final String SQL =   "SELECT t1.mr_id, t1.ip, t2.login, t2.password " +
                                        "FROM ssh_mme_ip_view t1 " +
                                        "JOIN connections t2 " +
                                        "ON t1.mr_id = t2.mr_id";

    public static Map<Integer,Map<String,String>> getSetting () {
        Map<Integer,Map<String,String>> sshSetting = new HashMap<>();
        try (Connection connection = DBConnection.getConnection(DBType.MYSQL)){
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery(SQL);
            while (resultSet.next()) {
                Map<String,String> values = new HashMap<>();
                int mr_id = resultSet.getInt(1);
                String ip = resultSet.getString(2);
                String login = resultSet.getString(3);
                String password = resultSet.getString(4);
                values.put("ip", ip);
                values.put("login", login);
                values.put("password", password);
                sshSetting.put(mr_id, values);
            }
        } catch (SQLException e) {
            System.out.println("Failed to establish a connection to the DataBase" + e.getMessage());
        }

        return sshSetting;
    }
}