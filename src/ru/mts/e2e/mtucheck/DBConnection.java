package ru.mts.e2e.mtucheck;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Класс возвращает connection к выбраной базе данных.
 */

public class DBConnection {
    /**Строка MySQL Driver */
    private static final String  DRIVER_MYSQL = "com.mysql.cj.jdbc.Driver";
    /**Строка Oracle Driver */
    private static final String  DRIVER_ORACLE = "oracle.jdbc.OracleDriver";

    /**Строка Oracle JDBC URL*/
    private static final String  JDBC_ORACLE = "jdbc:oracle:thin:@10.73.10.2:1521:DATASAFE";
    /**Строка MySQL JDBC URL*/
    private static final String  JDBC_MYSQL = "jdbc:mysql://localhost:3306/mtucheck?serverTimezone=UTC";

    /**Логин MySQL*/
    private static final String LOGIN_MYSQL = "root";
    /**Пароль MySQL*/
    private static final String PASSWORD_MYSQL = "6DOSs_G1";

    /**Логин Oracle*/
    private static final String  LOGIN_ORACLE = "ETCYS";
    /**Пароль Oracle*/
    private static final String  PASSWORD_ORACLE = "U8$BBUUU1OV986NO7";

    static {
        try {
            Class.forName(DRIVER_MYSQL);
            Class.forName(DRIVER_ORACLE);
        } catch (ClassNotFoundException e) {
            System.out.println("Error: no such DB driver exists.");
            e.printStackTrace();
        }
    }

    /**
     * Метод возращает Connection к выбранной базе данных.
     * @param type Получает тип базы данных, к которой требуется подключение.
     * @return Возвращает Connection к выбранной базе данных.
     * @throws SQLException Метод бросает SQLException при возникновении соответствующей ошибки.
     */
    public static Connection getConnection (DBType type) throws SQLException {
        Connection connection = null;
        switch (type) {
            case ORACLE:
                connection = DriverManager.getConnection(JDBC_ORACLE, LOGIN_ORACLE, PASSWORD_ORACLE);
                break;
            case MYSQL:
                connection = DriverManager.getConnection(JDBC_MYSQL, LOGIN_MYSQL, PASSWORD_MYSQL);
                break;
        }
        return connection;
    }
}