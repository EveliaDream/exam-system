package com.finalproject.examsystem.service;

import com.finalproject.examsystem.common.BusinessException;
import com.finalproject.examsystem.common.ErrorCode;
import com.finalproject.examsystem.common.PageResult;
import com.finalproject.examsystem.domain.Role;
import com.finalproject.examsystem.domain.StudentClass;
import com.finalproject.examsystem.domain.User;
import com.finalproject.examsystem.dto.user.ImportResult;
import com.finalproject.examsystem.dto.user.StudentCreateRequest;
import com.finalproject.examsystem.dto.user.StudentQuery;
import com.finalproject.examsystem.dto.user.StudentResponse;
import com.finalproject.examsystem.dto.user.StudentUpdateRequest;
import com.finalproject.examsystem.repository.ExamRecordRepository;
import com.finalproject.examsystem.repository.StudentClassRepository;
import com.finalproject.examsystem.repository.UserRepository;
import com.finalproject.examsystem.security.SecurityUtils;
import com.finalproject.examsystem.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 学生管理。
 * <p>
 * 数据隔离策略（阶段 2 的核心安全约束）：
 * <ul>
 *   <li>教师只能操作"自己名下班级"里的学生</li>
 *   <li>无班级的学生（未分班）视为公共待分配资源，任何教师都可编辑，便于建号后再分班</li>
 *   <li>学生本人在前端看不到本模块，后端由 {@code @PreAuthorize} 拦截</li>
 * </ul>
 * 注意：这与"教师只能操作自己创建的题库/考试"是同一套思路——权限边界定义在服务端。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    /** 新学生默认初始密码 */
    public static final String DEFAULT_PASSWORD = "123456";

    private final UserRepository userRepository;
    private final StudentClassRepository classRepository;
    private final ExamRecordRepository examRecordRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public PageResult<StudentResponse> pageStudents(StudentQuery query) {
        Page<User> page = userRepository.searchByTeacherScope(
                Role.STUDENT,
                currentTeacherId(),
                query.classId(),
                query.normalizedKeyword(),
                query.enabled(),
                PageRequest.of(query.pageIndex(), query.pageSize(), Sort.by(Sort.Direction.DESC, "id")));
        return PageResult.of(page.getContent().stream().map(StudentResponse::from).toList(),
                page.getTotalElements(), query.pageIndex() + 1, query.pageSize());
    }

    @Transactional(readOnly = true)
    public StudentResponse getStudent(Long id) {
        User student = requireStudent(id);
        requireManageable(student);
        return StudentResponse.from(student);
    }

    @Transactional
    public StudentResponse createStudent(StudentCreateRequest request) {
        String username = request.username().trim();
        if (userRepository.existsByUsernameAndDeletedFalse(username)) {
            throw BusinessException.of(ErrorCode.USERNAME_DUPLICATED);
        }
        StudentClass clazz = resolveClass(request.classId());
        requireClassOwnedByCurrentTeacher(clazz);

        String rawPassword = (request.password() == null || request.password().isBlank())
                ? DEFAULT_PASSWORD : request.password();
        User student = User.create(username, passwordEncoder.encode(rawPassword), request.realName().trim(), Role.STUDENT);
        student.setStudentClass(clazz);
        userRepository.save(student);
        log.info("教师 {} 新增学生 {}", currentTeacherId(), username);
        return StudentResponse.from(student);
    }

    @Transactional
    public StudentResponse updateStudent(Long id, StudentUpdateRequest request) {
        User student = requireStudent(id);
        requireManageable(student);

        String username = request.username().trim();
        // 用户名唯一性要排除自己，否则"不改用户名直接保存"会误报重复
        userRepository.findByUsernameAndDeletedFalse(username).ifPresent(other -> {
            if (!other.getId().equals(id)) {
                throw BusinessException.of(ErrorCode.USERNAME_DUPLICATED);
            }
        });

        // 原班级与目标班级都必须归属当前教师，防止把学生"搬到"别人的班
        requireClassOwnedByCurrentTeacher(student.getStudentClass());
        StudentClass clazz = resolveClass(request.classId());
        requireClassOwnedByCurrentTeacher(clazz);

        boolean classChanged = !java.util.Objects.equals(
                student.getStudentClass() == null ? null : student.getStudentClass().getId(),
                clazz == null ? null : clazz.getId());

        student.setUsername(username);
        student.setRealName(request.realName().trim());
        student.setStudentClass(clazz);
        if (request.enabled() != null && !request.enabled().equals(student.getEnabled())) {
            student.setEnabled(request.enabled());
            // 停用账号时使其已签发的 token 立即失效，否则被停用的学生仍能继续作答
            student.setTokenVersion(student.getTokenVersion() + 1);
        }
        student.setUpdatedAt(LocalDateTime.now());
        userRepository.save(student);
        log.info("教师 {} 修改学生 {}, 班级是否变更={}", currentTeacherId(), id, classChanged);
        return StudentResponse.from(student);
    }

    /**
     * 删除学生（逻辑删除）。
     * <p>
     * 若该学生已有作答记录则拒绝删除：试卷成绩必须可追溯，删掉账号会让成绩单出现孤儿数据。
     * 此时应改为"停用账号"。
     */
    @Transactional
    public void deleteStudent(Long id) {
        User student = requireStudent(id);
        requireManageable(student);

        if (examRecordRepository.countByStudentId(id) > 0) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID,
                    "该学生已有考试作答记录，不能删除；如需禁止其登录请改为停用账号");
        }
        student.setDeleted(true);
        student.setUpdatedAt(LocalDateTime.now());
        // 逻辑删除后旧 token 必须立即失效
        student.setTokenVersion(student.getTokenVersion() + 1);
        userRepository.save(student);
        log.info("教师 {} 删除学生 {}", currentTeacherId(), id);
    }

    /** 教师重置学生密码：恢复为初始密码，并使该学生已签发的 token 失效 */
    @Transactional
    public void resetPassword(Long id) {
        User student = requireStudent(id);
        requireManageable(student);
        student.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        student.setTokenVersion(student.getTokenVersion() + 1);
        student.setUpdatedAt(LocalDateTime.now());
        userRepository.save(student);
        log.info("教师 {} 重置学生 {} 的密码", currentTeacherId(), id);
    }

    /**
     * 批量导入学生。
     * <p>
     * 输入格式：每行一条，逗号分隔 —— {@code 用户名,姓名,年级,班级名称}
     * 采用逐行处理策略：单行出错只跳过该行并记录原因，不影响其余行。
     */
    @Transactional
    public ImportResult importStudents(String content) {
        if (content == null || content.isBlank()) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "导入内容为空");
        }
        List<String> errors = new ArrayList<>();
        int total = 0;
        int success = 0;

        String[] lines = content.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            total++;
            int lineNo = i + 1;
            try {
                String[] parts = line.split(",");
                if (parts.length < 2) {
                    errors.add("第 " + lineNo + " 行：至少需要【用户名,姓名】两列");
                    continue;
                }
                String username = parts[0].trim();
                String realName = parts[1].trim();
                String grade = parts.length > 2 ? parts[2].trim() : null;
                String className = parts.length > 3 ? parts[3].trim() : null;

                if (username.isEmpty() || realName.isEmpty()) {
                    errors.add("第 " + lineNo + " 行：用户名与姓名不能为空");
                    continue;
                }
                if (!username.matches("^[a-zA-Z0-9_]{3,50}$")) {
                    errors.add("第 " + lineNo + " 行：用户名 " + username + " 格式不合法（3~50 位字母、数字或下划线）");
                    continue;
                }
                if (userRepository.existsByUsernameAndDeletedFalse(username)) {
                    errors.add("第 " + lineNo + " 行：用户名 " + username + " 已存在");
                    continue;
                }

                StudentClass clazz = null;
                if (className != null && !className.isEmpty()) {
                    clazz = findOrCreateClass(className, grade);
                }

                User student = User.create(username, passwordEncoder.encode(DEFAULT_PASSWORD), realName, Role.STUDENT);
                student.setStudentClass(clazz);
                userRepository.save(student);
                success++;
            } catch (Exception e) {
                log.warn("导入第 {} 行失败", lineNo, e);
                errors.add("第 " + lineNo + " 行：" + e.getMessage());
            }
        }
        log.info("教师 {} 批量导入学生：共 {} 行，成功 {} 行，失败 {} 行",
                currentTeacherId(), total, success, errors.size());
        return ImportResult.of(total, success, errors);
    }

    // ================= 内部方法 =================

    /** 班级名称不存在时自动创建，并归属当前教师 —— 避免教师为导入名单先手工建几十个班 */
    private StudentClass findOrCreateClass(String className, String grade) {
        return classRepository.findByTeacherIdAndDeletedFalseOrderByIdAsc(currentTeacherId()).stream()
                .filter(c -> c.getName().equals(className))
                .findFirst()
                .orElseGet(() -> {
                    if (classRepository.existsByNameAndDeletedFalse(className)) {
                        // 同名班级属于其他教师：不越权占用，本条学生按未分班处理
                        log.warn("班级 {} 已存在但不属于当前教师，该学生将不分配班级", className);
                        return null;
                    }
                    return classRepository.save(StudentClass.create(className, grade, currentUser()));
                });
    }

    private User requireStudent(Long id) {
        User user = userRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> BusinessException.of(ErrorCode.USER_NOT_FOUND));
        if (user.getRole() != Role.STUDENT) {
            throw BusinessException.of(ErrorCode.NOT_A_STUDENT);
        }
        return user;
    }

    /** 无班级的学生视为待分配资源，任何教师可编辑；有班级的必须归属当前教师 */
    private void requireManageable(User student) {
        if (student.getStudentClass() != null) {
            requireClassOwnedByCurrentTeacher(student.getStudentClass());
        }
    }

    private void requireClassOwnedByCurrentTeacher(StudentClass clazz) {
        if (clazz == null) {
            return;
        }
        User teacher = clazz.getTeacher();
        if (teacher != null && !teacher.getId().equals(currentTeacherId())) {
            throw BusinessException.of(ErrorCode.CLASS_NOT_OWNED);
        }
    }

    private StudentClass resolveClass(Long classId) {
        if (classId == null) {
            return null;
        }
        return classRepository.findByIdAndDeletedFalse(classId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.CLASS_NOT_FOUND));
    }

    private User currentUser() {
        UserPrincipal principal = SecurityUtils.currentUser();
        return userRepository.findByIdAndDeletedFalse(principal.userId())
                .orElseThrow(() -> BusinessException.of(ErrorCode.USER_NOT_FOUND));
    }

    private Long currentTeacherId() {
        return SecurityUtils.currentUserId();
    }
}
