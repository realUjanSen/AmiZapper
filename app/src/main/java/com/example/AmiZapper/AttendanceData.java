package com.example.amizapper;

import java.util.Locale;

/**
 * Data class representing attendance for a single course
 */
public class AttendanceData {
    private final String courseCode;
    private final String subjectName;
    private final double percentage;
    private final int classesAttended;
    private final int totalClasses;

    public AttendanceData(String courseCode, String subjectName, double percentage,
                          int classesAttended, int totalClasses) {
        this.courseCode = courseCode;
        this.subjectName = subjectName;
        this.percentage = percentage;
        this.classesAttended = classesAttended;
        this.totalClasses = totalClasses;
    }

    public String getCourseCode() {
        return courseCode;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public double getPercentage() {
        return percentage;
    }

    public int getClassesAttended() {
        return classesAttended;
    }

    public int getTotalClasses() {
        return totalClasses;
    }

    public String getAttendanceColor() {
        if (percentage >= 85) {
            return "#2FA822"; // Green
        } else if (percentage >= 75) {
            return "#f4c242"; // Yellow/Orange
        } else {
            return "#DC3912"; // Red
        }
    }

    public String getClassCountString() {
        return classesAttended + "/" + totalClasses;
    }

    public String getPercentageString() {
        return String.format(Locale.US, "%.2f%%", percentage);
    }

    @Override
    public String toString() {
        return courseCode + " - " + subjectName + ": " + getPercentageString() + " (" + getClassCountString() + ")";
    }
}

