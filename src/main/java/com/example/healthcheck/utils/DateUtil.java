package com.example.healthcheck.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

public class DateUtil {
    private static final String STANDARD_FORMAT = "yyyy-MM-dd HH:mm:ss";
    
    public static String nowFormat() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern(STANDARD_FORMAT));
    }
}
