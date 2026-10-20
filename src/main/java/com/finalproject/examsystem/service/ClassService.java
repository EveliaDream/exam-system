package com.finalproject.examsystem.service;

import com.finalproject.examsystem.common.BusinessException;
import com.finalproject.examsystem.common.ErrorCode;
import com.finalproject.examsystem.common.PageResult;
import com.finalproject.examsystem.domain.Role;
import com.finalproject.examsystem.domain.StudentClass;
import com.finalproject.examsystem.domain.User;
import com.finalproject.examsystem.dto.clazz.ClassQuery;
import com.finalproject.examsystem.dto.clazz.ClassResponse;
import com.finalproject.examsystem.dto.clazz.ClassSaveRequest;
import com.finalproject.examsystem.repository.StudentClassRepository;
import com.finalproject.examsystem.repository.UserRepository;
import com.finalproject.examsystem.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 班级管理。
 * <p>
 * 数据隔离：教师只能查看与维护自己名下的班级。
 * 删除采用逻辑删除，且要求班级内没有学生——否则学生会变成无人认领的孤儿数据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClassService {

    private final StudentClassRepository classRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public PageResult<ClassResponse> pageClasses(ClassQuery query) {
        Page<StudentClass> page = classRepository.searchByTeacherScope(
                currentTeacherId(),
                query.normalizedKeyword(),
                PageRequest.of(query.pageIndex(), query.pageSize(), Sort.by(Sort.Direction.DESC, "id")));
        return PageResult.of(page.getContent().stream().map(this::toResponse).toList(),
                page.getTotalElements(), query.pageIndex() + 1, query.pageSize());
    }

    /** 当前教师名下的全部班级（供"分配班级"下拉框使用，不分页） */
    @Transactional(readOnly = true)
    public List<ClassResponse> listMyClasses() {
        return classRepository.findByTeacherIdAndDeletedFalseOrderByIdAsc(currentTeacherId())
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ClassResponse getClass(Long id) {
        return toResponse(requireOwnedClass(id));
    }

    @Transactional
    public ClassResponse createClass(ClassSaveRequest request) {
        String name = request.name().trim();
        if (classRepository.existsByNameAndDeletedFalse(name)) {
            throw BusinessException.of(ErrorCode.CLASS_NAME_DUPLICATED);
        }
        StudentClass clazz = StudentClass.create(name, trimToNull(request.grade()), currentUser());
        classRepository.save(clazz);
        log.info("教师 {} 新增班级 {}", currentTeacherId(), name);
        return toResponse(clazz);
    }

    @Transactional
    public ClassResponse updateClass(Long id, ClassSaveRequest request) {
        StudentClass clazz = requireOwnedClass(id);
        String name = request.name().trim();
        // 排除自身，避免"不改名直接保存"误报重名
        if (classRepository.existsByNameAndDeletedFalseAndIdNot(name, id)) {
            throw BusinessException.of(ErrorCode.CLASS_NAME_DUPLICATED);
        }
        clazz.setName(name);
        clazz.setGrade(trimToNull(request.grade()));
        classRepository.save(clazz);
        log.info("教师 {} 修改班级 {}", currentTeacherId(), id);
        return toResponse(clazz);
    }

    @Transactional
    public void deleteClass(Long id) {
        StudentClass clazz = requireOwnedClass(id);
        long studentCount = userRepository.countByStudentClassIdAndRoleAndDeletedFalse(id, Role.STUDENT);
        if (studentCount > 0) {
            throw BusinessException.of(ErrorCode.CLASS_HAS_STUDENTS,
                    "该班级下仍有 " + studentCount + " 名学生，请先转移或删除这些学生");
        }
        clazz.setDeleted(true);
        classRepository.save(clazz);
        log.info("教师 {} 删除班级 {}", currentTeacherId(), id);
    }

    // ================= 内部方法 =================

    private ClassResponse toResponse(StudentClass clazz) {
        long count = userRepository.countByStudentClassIdAndRoleAndDeletedFalse(clazz.getId(), Role.STUDENT);
        return ClassResponse.from(clazz, count);
    }

    private StudentClass requireOwnedClass(Long id) {
        StudentClass clazz = classRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> BusinessException.of(ErrorCode.CLASS_NOT_FOUND));
        if (clazz.getTeacher() != null && !clazz.getTeacher().getId().equals(currentTeacherId())) {
            throw BusinessException.of(ErrorCode.CLASS_NOT_OWNED);
        }
        return clazz;
    }

    private User currentUser() {
        return userRepository.findByIdAndDeletedFalse(currentTeacherId())
                .orElseThrow(() -> BusinessException.of(ErrorCode.USER_NOT_FOUND));
    }

    private Long currentTeacherId() {
        return SecurityUtils.currentUserId();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
