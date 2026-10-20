package com.finalproject.examsystem.dto.clazz;

import com.finalproject.examsystem.domain.StudentClass;

/** 班级响应，附带学生人数。 */
public record ClassResponse(Long id,
                           String name,
                           String grade,
                           Long teacherId,
                           String teacherName,
                           long studentCount) {

    public static ClassResponse from(StudentClass clazz, long studentCount) {
        return new ClassResponse(
                clazz.getId(),
                clazz.getName(),
                clazz.getGrade(),
                clazz.getTeacher() == null ? null : clazz.getTeacher().getId(),
                clazz.getTeacher() == null ? null : clazz.getTeacher().getRealName(),
                studentCount);
    }
}
