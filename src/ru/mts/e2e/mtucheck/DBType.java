package ru.mts.e2e.mtucheck;

/**
 * Класс перечисление, определяет все возможные типы подключения к существующим в проекте базам данных.
 */

public enum DBType {
    /** Обозначает, необходимость получить connection к БД Oracle*/
    ORACLE,
    /** Обозначает, необходимость получить connection к БД MySQL*/
    MYSQL
}
