package Util;

import java.util.Date;

public class Log {
    public static void Info(String title, String msg)
    {
        System.out.println(new Date().toString() + " [Info] ["+title+"] " + msg);
        System.out.flush();
    }
    public static void Error(String title, String msg)
    {
        System.out.println(new Date().toString() + " [Error] ["+title+"] " + msg);
        System.out.flush();
    }
}
