package ru.mts.e2e.mtucheck;

import java.sql.*;

public class UpdateEnbTable {
    private static final String INSERT_TO_ENB_TEMP_TABLE = "INSERT INTO enb_temp (ip, enb_id, mr_id, last_check) VALUES (?,?,(SELECT id FROM mr WHERE name = ?),?)";
    private static final String INSERT_ALL_NEW_TO_ENB_TABLE = "INSERT INTO enb (ip, enb_id, mr_id, last_check) VALUES (?,?,?,?)";
    private static final String CREATE_ENB_TEMP_TABLE = "CREATE TEMPORARY TABLE enb_temp LIKE enb";
    private static final String SELECT_ALL_EXIST_ENB =
            "SELECT DISTINCT " +
            "CASE " +
            "     WHEN NODE = 'asr5500-norilsk' THEN 'NOR'" +
            "     WHEN NODE = 'MAG-MME-04' or NODE = 'MAG-MME-05' or NODE = 'BOR-MME-04' or NODE = 'BOR-MME-05' THEN 'CEN' " +
            "     ELSE MR " +
            "     END as MR," +
            "GLOBAL_ENODEB_ID, ENODEB_IP_ADDRESS " +
            "FROM IOSS_CM.CIS_SGSN_mme_service_enod#_271 " +
            "WHERE GLOBAL_ENODEB_ID IS NOT NULL and ENODEB_IP_ADDRESS IS NOT NULL AND MR IS NOT NULL " +
            "ORDER BY 1,2,3 ";
    private static final String SELECT_NOT_EXISTS_ENB = "SELECT ip, enb_id, status, mr_id, last_check FROM enb_temp AS t1 " +
            "WHERE NOT EXISTS " +
            "(SELECT mr_id, enb_id FROM enb AS t2 " +
            "WHERE t1.mr_id=t2.mr_id AND t1.enb_id=t2.enb_id)";

    public static void doUpdate () {

        try (Connection connectionMySQL = DBConnection.getConnection(DBType.MYSQL);
             Connection connectionOracle = DBConnection.getConnection(DBType.ORACLE))
        {
            Statement statementSQL = connectionMySQL.createStatement();
            statementSQL.executeUpdate(CREATE_ENB_TEMP_TABLE);
            System.out.println("Enb temporary table is created.");
            System.out.println("Adding all existing enb to temporary table in progress...");
            connectionMySQL.setAutoCommit(false);
            long date = System.currentTimeMillis();
            long start = date;
            long finish = 0;
            int inserted = 0;
            try (Statement statementOracle = connectionOracle.createStatement();
                 ResultSet resultSetOracleAllEnb = statementOracle.executeQuery(SELECT_ALL_EXIST_ENB);
                 PreparedStatement psMySQLInsertToEnbTempTable = connectionMySQL.prepareStatement(INSERT_TO_ENB_TEMP_TABLE))
            {
                while (resultSetOracleAllEnb.next()){

                    String MR = resultSetOracleAllEnb.getString("MR");
                    String GLOBAL_ENODEB_ID = resultSetOracleAllEnb.getString("GLOBAL_ENODEB_ID");
                    String ENODEB_IP_ADDRESS = resultSetOracleAllEnb.getString("ENODEB_IP_ADDRESS");

                    psMySQLInsertToEnbTempTable.setString(1, ENODEB_IP_ADDRESS);
                    psMySQLInsertToEnbTempTable.setString(2, GLOBAL_ENODEB_ID);
                    psMySQLInsertToEnbTempTable.setString(3, MR);
                    psMySQLInsertToEnbTempTable.setLong(4, date);

                    try {
                        psMySQLInsertToEnbTempTable.executeUpdate();
                        inserted++;
                      } catch (SQLException e){
                        connectionMySQL.rollback();
                        e.printStackTrace();
                        break;
                    }
                }
                connectionMySQL.commit();
                finish = System.currentTimeMillis();
                System.out.println(inserted + " records inserted into enb temporary table." + " Transaction time = " + (finish-start) + " ms. ");
                start = finish;
                inserted = 0;
            }

            try (ResultSet rsNotExitssEnb = statementSQL.executeQuery(SELECT_NOT_EXISTS_ENB);
                 PreparedStatement psInsertNewToEnbTable = connectionMySQL.prepareStatement(INSERT_ALL_NEW_TO_ENB_TABLE)) {
                while (rsNotExitssEnb.next()) {

                    int MR = rsNotExitssEnb.getInt("mr_id");
                    String GLOBAL_ENODEB_ID = rsNotExitssEnb.getString("enb_id");
                    String ENODEB_IP_ADDRESS = rsNotExitssEnb.getString("ip");

                    psInsertNewToEnbTable.setString(1, ENODEB_IP_ADDRESS);
                    psInsertNewToEnbTable.setString(2, GLOBAL_ENODEB_ID);
                    psInsertNewToEnbTable.setInt(3, MR);
                    psInsertNewToEnbTable.setLong(4, date);

                    try {
                        psInsertNewToEnbTable.executeUpdate();
                        inserted++;
                    } catch (SQLException e){
                        connectionMySQL.rollback();
                        e.printStackTrace();
                        break;
                    }
                }
                connectionMySQL.commit();
                finish = System.currentTimeMillis();
                System.out.println(inserted + " new records inserted into enb table." + " Transaction time = " + (finish-start) + " ms. ");
            }

        } catch (SQLException e) {
            System.out.println("Connection or SQL execution Failed!");
            e.printStackTrace();
        }
    }
}