package ru.mts.e2e.mtucheck;

/**
 * Класс eNB создаеся на основе полученных из таблицы enb данных. Каждая строка в таблице это один экземпляр объекта eNB.
 * Созданый таким образом экземпляр передается затем на проверку MTU.
 *
 */

public class eNB {
    /** Поле eNB id */
    private String id;

    /** Поле eNB ip address */
    private String ip;

    /** Поле MME ip address. IP адрес ММЕ, с которого нужно производить проверку */
    private String mmeIP;

    /** Поле дата последней проверки eNB в Unix Time*/
    private long last_check;

    /** Поле eNB статус.
     * @see EnodebStatus
     */
    private EnodebStatus status;

    /**
     * Конструктор - создание нового объекта eNB с определенными значениями
     * @param id - eNB id
     * @param ip - eNB ip address
     * @param mmeIP - MME ip address
     * @param last_check - дата последней проверки
     * @param status - eNB статус
     */
    public eNB (String id, String ip, String mmeIP, long last_check, EnodebStatus status) {
        this.id = id;
        this.ip = ip;
        this.mmeIP = mmeIP;
        this.last_check = last_check;
        this.status = status;
    }

    /**
     * Функция получения значения поля {@link eNB#id}
     * @return возвращает eNB id
     */
    public String getId() { return id; }

    /**
     * Функция получения значения поля {@link eNB#ip}
     * @return возвращает eNB ip address
     */
    public String getIp() {
        return ip;
    }

    /**
     * Функция получения значения поля {@link eNB#mmeIP}
     * @return возвращает MME ip address
     */
    public String getMmeIP() {
        return mmeIP;
    }

    /**
     * Функция получения значения поля {@link eNB#last_check}
     * @return возвращает дату последней проверки в Unix Time
     */
    public long getLast_check() {
        return last_check;
    }

    /**
     * Функция получения значения поля {@link eNB#status}
     * @return возвращает статус eNB
     */
    public EnodebStatus getStatus() {
        return status;
    }

    /**
     * Функция переопределения метода toString для получения удобного вывода на экран объекта eNB.
     * @return возвращает eNB параметры.
     */
    @Override
    public String toString() {
        return "eNB{" +
                "id='" + id + '\'' +
                ", ip='" + ip + '\'' +
                ", mmeIP='" + mmeIP + '\'' +
                ", last_check=" + last_check +
                ", status=" + status +
                '}';
    }
}