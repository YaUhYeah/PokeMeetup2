package io.github.pokemeetup.utils;

import java.text.SimpleDateFormat;
import java.util.Date;

public class TimeUtils {
    private static final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd HH:mm");

    public static String formatTime(long timestamp) {
        if (System.currentTimeMillis() - timestamp < 24 * 60 * 60 * 1000) {
            // If less than 24 hours ago, show only time
            return timeFormat.format(new Date(timestamp));
        }
        // Otherwise show date and time
        return dateFormat.format(new Date(timestamp));
    }
}
