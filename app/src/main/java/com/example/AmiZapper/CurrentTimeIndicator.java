package com.example.amizapper;

import java.util.Calendar;

/**
 * Utility class for calculating current time indicator position within periods
 */
public class CurrentTimeIndicator {

    /**
     * Result class containing indicator information
     */
    public static class IndicatorPosition {
        public final boolean isWithinPeriod;
        public final float progress; // 0.0 to 1.0
        public final boolean isInGap; // true if in 5-min gap between periods
        public final boolean isAtEndOfPrevious; // true if indicator should show at bottom of previous card
        public final boolean isAtStartOfNext; // true if indicator should show at top of next card

        public IndicatorPosition(boolean isWithinPeriod, float progress, boolean isInGap,
                                  boolean isAtEndOfPrevious, boolean isAtStartOfNext) {
            this.isWithinPeriod = isWithinPeriod;
            this.progress = progress;
            this.isInGap = isInGap;
            this.isAtEndOfPrevious = isAtEndOfPrevious;
            this.isAtStartOfNext = isAtStartOfNext;
        }
    }

    /**
     * Get the current time in minutes from midnight
     */
    public static int getCurrentTimeMinutes() {
        Calendar now = Calendar.getInstance();
        int hour = now.get(Calendar.HOUR_OF_DAY);
        int minute = now.get(Calendar.MINUTE);
        return hour * 60 + minute;
    }

    /**
     * Parse time string (HH:MM or H:MM) to minutes from midnight
     */
    public static int parseTimeToMinutes(String time) {
        if (time == null || time.isEmpty()) return -1;
        try {
            String[] parts = time.trim().split(":");
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            return hour * 60 + minute;
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Check if the current time falls within a specific period
     * @param startTime start time in HH:MM format
     * @param endTime end time in HH:MM format
     * @return IndicatorPosition with progress if within period
     */
    public static IndicatorPosition getIndicatorPosition(String startTime, String endTime) {
        int currentMinutes = getCurrentTimeMinutes();
        int startMinutes = parseTimeToMinutes(startTime);
        int endMinutes = parseTimeToMinutes(endTime);

        if (startMinutes < 0 || endMinutes < 0) {
            return new IndicatorPosition(false, 0, false, false, false);
        }

        if (currentMinutes >= startMinutes && currentMinutes <= endMinutes) {
            // Within this period
            float progress = (float)(currentMinutes - startMinutes) / (endMinutes - startMinutes);
            return new IndicatorPosition(true, progress, false, false, false);
        }

        return new IndicatorPosition(false, 0, false, false, false);
    }

    /**
     * Check if we're in the 5-minute gap between two periods
     * @param previousEndTime end time of previous period
     * @param nextStartTime start time of next period
     * @return true if in gap
     */
    public static boolean isInGapBetweenPeriods(String previousEndTime, String nextStartTime) {
        int currentMinutes = getCurrentTimeMinutes();
        int prevEnd = parseTimeToMinutes(previousEndTime);
        int nextStart = parseTimeToMinutes(nextStartTime);

        if (prevEnd < 0 || nextStart < 0) return false;

        return currentMinutes > prevEnd && currentMinutes < nextStart;
    }

    /**
     * Check if today is a specific day of week
     * @param dayIndex 0=Monday, 1=Tuesday, etc.
     * @return true if today is that day
     */
    public static boolean isToday(int dayIndex) {
        Calendar cal = Calendar.getInstance();
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        // Convert: Sunday=1...Saturday=7 to Monday=0...Sunday=6
        int todayIndex = (dayOfWeek == Calendar.SUNDAY) ? 6 : dayOfWeek - 2;
        return todayIndex == dayIndex;
    }

    /**
     * Get today's day index (0=Monday ... 6=Sunday)
     */
    public static int getTodayIndex() {
        Calendar cal = Calendar.getInstance();
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        return (dayOfWeek == Calendar.SUNDAY) ? 6 : dayOfWeek - 2;
    }

    /**
     * Get the current period number based on current time
     * @return period number (1-8), 0 for lunch, -1 if outside school hours, -2 if in gap
     */
    public static int getCurrentPeriodNumber() {
        int currentMinutes = getCurrentTimeMinutes();

        for (PeriodSlots.PeriodSlot slot : PeriodSlots.getAllSlots()) {
            int start = parseTimeToMinutes(slot.startTime);
            int end = parseTimeToMinutes(slot.endTime);

            if (currentMinutes >= start && currentMinutes <= end) {
                return slot.isBreak ? 0 : slot.periodNumber;
            }
        }

        // Check if we're in a gap
        PeriodSlots.PeriodSlot[] slots = PeriodSlots.getAllSlots().toArray(new PeriodSlots.PeriodSlot[0]);
        for (int i = 0; i < slots.length - 1; i++) {
            int prevEnd = parseTimeToMinutes(slots[i].endTime);
            int nextStart = parseTimeToMinutes(slots[i + 1].startTime);
            if (currentMinutes > prevEnd && currentMinutes < nextStart) {
                return -2; // In gap
            }
        }

        return -1; // Outside school hours
    }

    /**
     * Get the previous and next period numbers when in a gap
     * @return int array [previousPeriod, nextPeriod] or null if not in gap
     */
    public static int[] getGapAdjacentPeriods() {
        int currentMinutes = getCurrentTimeMinutes();
        PeriodSlots.PeriodSlot[] slots = PeriodSlots.getAllSlots().toArray(new PeriodSlots.PeriodSlot[0]);

        for (int i = 0; i < slots.length - 1; i++) {
            int prevEnd = parseTimeToMinutes(slots[i].endTime);
            int nextStart = parseTimeToMinutes(slots[i + 1].startTime);
            if (currentMinutes > prevEnd && currentMinutes < nextStart) {
                int prevPeriod = slots[i].isBreak ? 0 : slots[i].periodNumber;
                int nextPeriod = slots[i + 1].isBreak ? 0 : slots[i + 1].periodNumber;
                return new int[]{prevPeriod, nextPeriod};
            }
        }
        return null;
    }
}
