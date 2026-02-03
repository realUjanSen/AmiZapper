package com.example.amizapper;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps course codes to subject names and credits
 * This can be expanded or fetched from the server in future
 */
public class SubjectMapper {
    private static final Map<String, String> SUBJECT_MAP = new HashMap<>();
    private static final Map<String, Integer> CREDITS_MAP = new HashMap<>();

    static {
        // Major (Core) [ 8-12 ] [Compulsory]
        SUBJECT_MAP.put("CSIT221", "Elementary Algorithms");
        CREDITS_MAP.put("CSIT221", 4);
        SUBJECT_MAP.put("DSC306", "Machine Learning I");
        CREDITS_MAP.put("DSC306", 4);
        SUBJECT_MAP.put("CSIT364", "Soft Computing Techniques");
        CREDITS_MAP.put("CSIT364", 4);

        // Minor [ 3-7 ]
        SUBJECT_MAP.put("CSIT224", "IT Project Management");
        CREDITS_MAP.put("CSIT224", 2);
        SUBJECT_MAP.put("DSC309", "Data Visualization Lab");
        CREDITS_MAP.put("DSC309", 2);

        // Specialization Elective Courses/Discipline I/ Specialization Major (Core) [ 4-8 ]
        SUBJECT_MAP.put("CSIT136", "Internet of Things");
        CREDITS_MAP.put("CSIT136", 4);
        SUBJECT_MAP.put("CSIT363", "Digital Forensics");
        CREDITS_MAP.put("CSIT363", 4);
    }

    public static String getSubjectName(String courseCode) {
        if (courseCode == null) return "";
        String name = SUBJECT_MAP.get(courseCode.trim().toUpperCase());
        return name != null ? name : courseCode;
    }

    public static int getCredits(String courseCode) {
        if (courseCode == null) return 0;
        Integer credits = CREDITS_MAP.get(courseCode.trim().toUpperCase());
        return credits != null ? credits : 0;
    }

    public static String getCourseCodeWithCredits(String courseCode) {
        int credits = getCredits(courseCode);
        if (credits > 0) {
            return "(" + courseCode + ") [" + credits + " Credits]";
        }
        return "(" + courseCode + ")";
    }

    public static void addMapping(String courseCode, String subjectName, int credits) {
        SUBJECT_MAP.put(courseCode.toUpperCase(), subjectName);
        CREDITS_MAP.put(courseCode.toUpperCase(), credits);
    }
}
