package com.example.amizapper;

public class ExamData {
    private final String courseCode;
    private final String courseTitle;
    private final String examDate;
    private final String examTime;
    private final String room;

    public ExamData(String courseCode, String courseTitle, String examDate, String examTime, String room) {
        this.courseCode = courseCode;
        this.courseTitle = courseTitle;
        this.examDate = examDate;
        this.examTime = examTime;
        this.room = room;
    }

    public String getCourseCode() { return courseCode; }
    public String getCourseTitle() { return courseTitle; }
    public String getExamDate() { return examDate; }
    public String getExamTime() { return examTime; }
    public String getRoom() { return room; }
}