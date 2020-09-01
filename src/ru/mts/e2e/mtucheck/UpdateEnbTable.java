package ru.mts.e2e.mtucheck;

import java.sql.*;

/**
 * Класс осуществляет обновление таблицы enb в дазе данных MySQL для поддержания ее в актуальном состоянии. Обновление происходит следующим образом.
 * Сначала происходит подключение к базе DataSafe (Oracle), которая регулярно обновляется на основе выгрузок конфигураций с узлов пакетной коры со всех регионов.
 * Для этой цели используется запрос {@link UpdateEnbTable#SELECT_ALL_EXIST_ENB}. Сформированная таблица содержит записи всех eNB сети.
 * После этого все полученные записи копируются во временную таблицу (формируется запросом  {@link UpdateEnbTable#CREATE_ENB_TEMP_TABLE}) в базу данных MySQL {@link UpdateEnbTable#INSERT_TO_ENB_TEMP_TABLE}.
 * Далее во временной таблице enb_temp (MySQL) находятся все записи, которых нет в рабочей таблице enb (MySQL) {@link UpdateEnbTable#SELECT_NOT_EXISTS_ENB}. Полученный результат добавляется в таблицу enb {@link UpdateEnbTable#INSERT_ALL_NEW_TO_ENB_TABLE}
 */

public class UpdateEnbTable {
    /**
     * SQL-запрос добавляет все полученные записи из базы DataSafe (Oracle) во временную таблицу enb_temp (MySQL)
     */
    private static final String INSERT_TO_ENB_TEMP_TABLE = "INSERT INTO enb_temp (ip, enb_id, mr_id, last_check) VALUES (?,?,(SELECT id FROM mr WHERE name = ?),?)";

    /**
     * SQL-запрос добавляет все записи из таблицы enb_temp (MySQL) в enb (MySQL), кототые в ней отсутствуют. Статус вновь созданной eNB = NEW.
     * @see EnodebStatus
     */
    private static final String INSERT_ALL_NEW_TO_ENB_TABLE = "INSERT INTO enb (ip, enb_id, mr_id, last_check) VALUES (?,?,?,?)";

    /**
     * SQL-запрос создает временную таблицу enb_temp (MySQL).
     */
    private static final String CREATE_ENB_TEMP_TABLE = "CREATE TEMPORARY TABLE enb_temp LIKE enb";

    /**
     * SQL-запрос считает количество записей в основной таблице enb (MySQL).
     */
    private static final String ENB_ALL_RECORDS_COUNTER = "SELECT count(id) FROM enb";

    /**
     * SQL-запрос плучает все уникальные eNB из таблицы CIS_SGSN_mme_service_enod#_271 (Oracle), которые сущестуют на сети в текущий момент.
     */
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
    /**
     * SQL-запрос находит все недостающие записи в таблице enb по сравнению с enb_temp.
     */
    private static final String SELECT_NOT_EXISTS_ENB = "SELECT ip, enb_id, status, mr_id, last_check FROM enb_temp AS t1 " +
            "WHERE NOT EXISTS " +
            "(SELECT mr_id, enb_id FROM enb AS t2 " +
            "WHERE t1.mr_id=t2.mr_id AND t1.enb_id=t2.enb_id)";

    /**
     * Метод, в котором происходят все манипуляции с таблицами и их обновление новыми данными сети.
     */
    public static void doUpdate () {

        try (Connection connectionMySQL = DBConnection.getConnection(DBType.MYSQL);
             Connection connectionOracle = DBConnection.getConnection(DBType.ORACLE))
        {
            Statement statementSQL = connectionMySQL.createStatement();
            statementSQL.executeUpdate(CREATE_ENB_TEMP_TABLE);
            MainMTU.logger.info("Enb temporary table is created ({})." , DBType.MYSQL.toString());
            MainMTU.logger.info("Adding all existing eNBs to the temporary table in progress...");
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
                        MainMTU.logger.trace("eNB = {}, {}, {} added to the temporary table. ", MR, GLOBAL_ENODEB_ID, ENODEB_IP_ADDRESS);
                        inserted++;
                      } catch (SQLException e){
                        //connectionMySQL.rollback();
                        MainMTU.logger.error("eNB = {}, {}, {} failed to add to the temporary table. Error message: {}.", MR, GLOBAL_ENODEB_ID, ENODEB_IP_ADDRESS, e.getMessage());
                        continue;
                    }
                }
                connectionMySQL.commit();
                finish = System.currentTimeMillis();
                MainMTU.logger.info("{} records inserted into enb temporary table. Transaction time = {}ms.", inserted, (finish-start));
                start = finish;
                inserted = 0;
            }

            try (ResultSet rsNotExitssEnb = statementSQL.executeQuery(SELECT_NOT_EXISTS_ENB);
                 PreparedStatement psInsertNewToEnbTable = connectionMySQL.prepareStatement(INSERT_ALL_NEW_TO_ENB_TABLE)) {

                MainMTU.logger.info("Adding all NEW eNBs to the \"enb\" table in progress...");

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
                        MainMTU.logger.trace("eNB = {}, {}, {} added to the MAIN enb table. ", MR, GLOBAL_ENODEB_ID, ENODEB_IP_ADDRESS);
                        inserted++;
                    } catch (SQLException e){
                        //connectionMySQL.rollback();
                        MainMTU.logger.error("eNB = {}, {}, {} failed to add to the MAIN table. Error message: {}.", MR, GLOBAL_ENODEB_ID, ENODEB_IP_ADDRESS, e.getMessage());
                        continue;
                    }
                }
                connectionMySQL.commit();
                finish = System.currentTimeMillis();
                MainMTU.logger.info("{} records inserted into enb MAIN table. Transaction time = {}ms.", inserted, (finish-start));
            }
            try (ResultSet allENBCounter = statementSQL.executeQuery(ENB_ALL_RECORDS_COUNTER))
                  {
                      if (allENBCounter.next()) {
                          int eNBCounter = allENBCounter.getInt(1);
                          MainMTU.logger.info("ENB MAIN table has {} records", eNBCounter);
                      }
                  }

        } catch (SQLException e) {
            MainMTU.logger.error("Connection establishing or SQL command execution is failed! Error message: {}", e.getMessage());
        }
    }
}