package ru.volganap.nikolay.kids_monitor_ably;

public interface KM_Constants {
    String LOG_TAG = "myLogs";
    String PREF_ACTIVITY = "pref_activity";
    String PREF_USER = "user_type";
    String PREF_REQUEST = "request_mode";
    String BROWSER_MODE = "browser_mode";
    String PARENT_PHONE = "parent_phone";
    String KID_PHONE = "kid_phone";
    String SERVER_DELAY_TITLE ="server_delay";
    String TIMER_DELAY = "timer_delay";
    String TIMER_SENDER = "timer_sender";
    String SENDER = "sender";
    String MESSAGE = "message";
    String MAP_TYPE = "map_type";
    String MARKER_DELAY = "marker_delay";
    String MARKER_SCALE = "marker_scale";
    String MARKER_MAX_NUMBER = "marker_max_number";
    String MAX_LOCATION_TIME = "max_location_time";
    String CHANGE_CONFIG_SERVER = "config";

    String ACTION_FROM_TIMER = "action_from_timer";
    String ACTION_FROM_GEOPOS = "action_from_geopos";
    String ACTION_FROM_OKHTTP = "action_from_okhttp";
    public static final String ACTION_FROM_BR = "ru.volganap.nikolay.kids_monitor_ably";
    public static final String ABLY_API_KEY = "1UUc1A.biVwVQ:gTtZjwGija3aqGJ6"; /* Sign up at ably.io to get your API key */
    public static final String URL_ADDR = "https://volganap.ru/location/index.php";
    String ABLY_ROOM = "kids_monitor";
    String COMMAND_BASE = "way?";
    String COMMAND_GET_POSITION = "pos";
    String COMMAND_CHECK_IT = "check";
    String ATTRIBUTE_NAME_DATE = "date-time";
    String ATTRIBUTE_NAME_LAT = "latitude";
    String ATTRIBUTE_NAME_LONG = "longitude";
    String ATTRIBUTE_NAME_ACCU = "accuracy";
    String ATTRIBUTE_NAME_BATT = "battery";
    String REG_SIGN = ";";
    String STA_SIGN = "#";
    String OK_STATE_PARENT = "0";
    String OK_STATE_KID = "1";
    String NET_ERROR_GOT_LOCATION_STATE = "2";
    String NET_ERROR_STATE = "Произошла ошибка подключения к серверу";
    String NO_CHANGE_STATE = "Настройки остались прежними";
    String TIMER_IS_SETUP_STATE = "Подтвержденные настройки таймера: ";
    String EMPTY_STORAGE_STATE = "Нет данных на сервере";
    String NO_LOCATION_FOUND_STATE = "Местоположение не удалось определить";
    String CONFIG_SERVER_STATE = "Изменение настроек на сервере: ";
    String CONFIRM_CONNECTION = "Обратная связь получена!: ";
    String LOCATION_IS_TURNED_OFF ="The Kid must turn the location on!";
}
