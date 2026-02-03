package com.example.amizapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Defines the standard period slots for the timetable
 */
public class PeriodSlots {

    public static class PeriodSlot {
        public final int periodNumber;
        public final String startTime;
        public final String endTime;
        public final boolean isBreak;
        public final String label;

        public PeriodSlot(int periodNumber, String startTime, String endTime, boolean isBreak, String label) {
            this.periodNumber = periodNumber;
            this.startTime = startTime;
            this.endTime = endTime;
            this.isBreak = isBreak;
            this.label = label;
        }
    }

    private static final List<PeriodSlot> SLOTS = new ArrayList<>();

    static {
        // Morning periods (before lunch)
        SLOTS.add(new PeriodSlot(1, "09:15", "10:05", false, "Period 1"));
        SLOTS.add(new PeriodSlot(2, "10:10", "11:00", false, "Period 2"));
        SLOTS.add(new PeriodSlot(3, "11:05", "11:55", false, "Period 3"));
        SLOTS.add(new PeriodSlot(4, "12:00", "12:50", false, "Period 4"));

        // Lunch/Tiffin break
        SLOTS.add(new PeriodSlot(-1, "12:50", "13:50", true, "Lunch Break"));

        // Afternoon periods (after lunch)
        SLOTS.add(new PeriodSlot(5, "13:50", "14:40", false, "Period 5"));
        SLOTS.add(new PeriodSlot(6, "14:45", "15:35", false, "Period 6"));
        SLOTS.add(new PeriodSlot(7, "15:40", "16:30", false, "Period 7"));
        SLOTS.add(new PeriodSlot(8, "16:35", "17:25", false, "Period 8"));
    }

    public static List<PeriodSlot> getAllSlots() {
        return SLOTS;
    }

    public static List<PeriodSlot> getPeriodsOnly() {
        List<PeriodSlot> periods = new ArrayList<>();
        for (PeriodSlot slot : SLOTS) {
            if (!slot.isBreak) {
                periods.add(slot);
            }
        }
        return periods;
    }

    /**
     * Find which period slot a class belongs to based on start time
     */
    public static int findPeriodNumber(String startTime) {
        for (PeriodSlot slot : SLOTS) {
            if (slot.startTime.equals(startTime) || timeMatches(slot.startTime, startTime)) {
                return slot.periodNumber;
            }
        }
        return -1;
    }

    /**
     * Compare times accounting for slight variations
     */
    private static boolean timeMatches(String slotTime, String classTime) {
        // Normalize times for comparison (handle "09:15" vs "9:15")
        String normalizedSlot = normalizeTime(slotTime);
        String normalizedClass = normalizeTime(classTime);
        return normalizedSlot.equals(normalizedClass);
    }

    private static String normalizeTime(String time) {
        if (time == null) return "";
        time = time.trim();
        // Remove leading zero from hour if present
        if (time.startsWith("0")) {
            time = time.substring(1);
        }
        return time;
    }

    /**
     * Get the last period that has a class on a given day
     */
    public static int getLastScheduledPeriod(List<TimetableClass> classes) {
        int lastPeriod = 0;
        for (TimetableClass cls : classes) {
            int period = findPeriodNumber(cls.getStartTime());
            if (period > lastPeriod) {
                lastPeriod = period;
            }
        }
        return lastPeriod;
    }
}
