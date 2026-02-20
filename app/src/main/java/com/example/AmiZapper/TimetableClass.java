package com.example.amizapper;

import java.io.Serializable;

/**
 * Represents a single class in the timetable
 */
public class TimetableClass implements Serializable {
    private static final long serialVersionUID = 4L; // Incremented for cancelled field

    private String time;
    private String courseCode;
    private String subjectName;
    private String teacher;
    private String location;
    private String dayOfWeek;
    private String attendanceColor;
    private String date; // Actual date in yyyy-MM-dd format
    private boolean cancelled; // Whether this class was cancelled by the institution

    public TimetableClass(String time, String courseCode, String subjectName, String teacher, String location, String dayOfWeek) {
        this.time = time;
        this.courseCode = courseCode;
        this.subjectName = subjectName;
        this.teacher = teacher;
        this.location = location;
        this.dayOfWeek = dayOfWeek;
        this.attendanceColor = "#3a87ad"; // Default color
        this.date = null;
        this.cancelled = false;
    }

    public TimetableClass(String time, String courseCode, String subjectName, String teacher,
                          String location, String dayOfWeek, String attendanceColor) {
        this(time, courseCode, subjectName, teacher, location, dayOfWeek);
        this.attendanceColor = attendanceColor != null ? attendanceColor : "#3a87ad";
    }

    public TimetableClass(String time, String courseCode, String subjectName, String teacher,
                          String location, String dayOfWeek, String attendanceColor, String date) {
        this(time, courseCode, subjectName, teacher, location, dayOfWeek, attendanceColor);
        this.date = date;
    }

    public TimetableClass(String time, String courseCode, String subjectName, String teacher,
                          String location, String dayOfWeek, String attendanceColor, String date, boolean cancelled) {
        this(time, courseCode, subjectName, teacher, location, dayOfWeek, attendanceColor, date);
        this.cancelled = cancelled;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getAttendanceColor() {
        return (attendanceColor != null && !attendanceColor.isEmpty()) ? attendanceColor : "#3a87ad";
    }

    public String getTime() {
        return time;
    }

    public String getCourseCode() {
        return courseCode;
    }

    public String getSubjectName() {
        return subjectName != null ? subjectName : courseCode;
    }

    public String getShortSubjectName(int maxLength) {
        String name = getSubjectName();
        if (name.length() > maxLength) {
            return name.substring(0, maxLength - 3) + "...";
        }
        return name;
    }

    public String getTeacher() {
        return teacher;
    }

    public String getShortTeacher() {
        // Remove employee ID in brackets
        String t = teacher;
        if (t != null && t.contains("[")) {
            t = t.substring(0, t.indexOf("[")).trim();
        }
        return t != null ? t : "";
    }

    public String getLocation() {
        return location;
    }

    public String getDayOfWeek() {
        return dayOfWeek;
    }

    public String getStartTime() {
        if (time != null && time.contains("to")) {
            return time.split("to")[0].trim();
        }
        return "";
    }

    public String getEndTime() {
        if (time != null && time.contains("to")) {
            return time.split("to")[1].trim();
        }
        return "";
    }
}
