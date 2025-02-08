package io.github.pokemeetup.system.gameplay.overworld;

import com.badlogic.gdx.graphics.Color;

public class DayNightCycle {
    private static final float NIGHT_END = 5.0f;     // 5:00 AM
    private static final float DAWN_START = 5.0f;    // 5:00 AM
    private static final float DAWN_END = 6.0f;      // 6:00 AM
    private static final float DAY_START = 6.0f;     // 6:00 AM
    private static final float DAY_END = 18.0f;      // 6:00 PM
    private static final float DUSK_START = 18.0f;   // 6:00 PM
    private static final float NIGHT_START = 19.0f;  // 7:00 PM

    // Base colors with proper ambient lighting
    private static final Color DAY_COLOR = new Color(1.0f, 1.0f, 1.0f, 1.0f);
    private static final Color NIGHT_COLOR = new Color(0.25f, 0.25f, 0.45f, 1.0f);
    private static final Color DAWN_COLOR = new Color(0.85f, 0.75f, 0.65f, 1.0f);
    private static final Color DUSK_COLOR = new Color(0.85f, 0.65f, 0.55f, 1.0f);
    private static final Color DAWN_DUSK_COLOR = new Color(0.8f, 0.6f, 0.6f, 1);
    public enum TimePeriod {
        NIGHT, DAWN, DAY, DUSK
    }

    public static TimePeriod getTimePeriod(float hourOfDay) {
        if (hourOfDay >= NIGHT_START || hourOfDay < NIGHT_END) {
            return TimePeriod.NIGHT;
        } else if (hourOfDay >= DAWN_START && hourOfDay < DAWN_END) {
            return TimePeriod.DAWN;
        } else if (hourOfDay >= DAY_START && hourOfDay < DAY_END) {
            return TimePeriod.DAY;
        } else {
            return TimePeriod.DUSK;
        }
    }public static float getHourOfDay(double worldTimeInMinutes) {
        return (float)((worldTimeInMinutes % (24 * 60)) / 60.0);
    }


    public static Color getWorldColor(float hourOfDay) {
        // Add debug logging

        Color result = new Color();

        // Check if it's nighttime (including crossing midnight)
        if (hourOfDay >= NIGHT_START || hourOfDay < DAWN_START) {
            return new Color(NIGHT_COLOR);
        }

        // Dawn transition
        if (hourOfDay >= DAWN_START && hourOfDay < DAY_START) {
            float progress = (hourOfDay - DAWN_START) / (DAY_START - DAWN_START);
            return result.set(DAWN_COLOR).lerp(DAY_COLOR, progress);
        }

        // Day time
        if (hourOfDay >= DAY_START && hourOfDay < DUSK_START) {
            return new Color(DAY_COLOR);
        }

        // Dusk transition
        float progress = (hourOfDay - DUSK_START) / (NIGHT_START - DUSK_START);
        return result.set(DAY_COLOR).lerp(NIGHT_COLOR, progress);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
    public static String getTimeString(double worldTimeInMinutes) {
        int hour = (int)(worldTimeInMinutes / 60) % 24;
        int minute = (int)(worldTimeInMinutes % 60);
        String amPm = hour >= 12 ? "PM" : "AM";
        hour = hour % 12;
        if (hour == 0) hour = 12;
        return String.format("%d:%02d %s", hour, minute, amPm);
    }
}
