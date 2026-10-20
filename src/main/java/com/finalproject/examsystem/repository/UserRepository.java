package com.finalproject.examsystem.repository;

import com.finalproject.examsystem.domain.Role;
import com.finalproject.examsystem.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsernameAndDeletedFalse(String username);

    boolean existsByUsernameAndDeletedFalse(String username);

    Optional<User> findByIdAndDeletedFalse(Long id);

    Page<User> findByRoleAndDeletedFalse(Role role, Pageable pageable);

    List<User> findByStudentClassIdAndDeletedFalse(Long classId);

    long countByStudentClassIdAndDeletedFalse(Long classId);

    /** 统计某班级下的学生数（角色固定为学生，避免把班主任教师也算进去） */
    long countByStudentClassIdAndRoleAndDeletedFalse(Long classId, Role role);

    /**
     * 按关键词模糊搜索（用户名或真实姓名）。
     * <p>
     * 注意用全限定名 com.finalproject.examsystem.domain.User：
     * 表名是 sys_user 而非 user，JPQL 里写 User 容易被误认为表名。
     */
    @Query("""
            select u from com.finalproject.examsystem.domain.User u
            where u.deleted = false
              and (:role is null or u.role = :role)
              and (:classId is null or u.studentClass.id = :classId)
              and (:keyword is null or u.username like %:keyword% or u.realName like %:keyword%)
            """)
    Page<User> search(@Param("role") Role role,
                      @Param("classId") Long classId,
                      @Param("keyword") String keyword,
                      Pageable pageable);

    /**
     * 按教师数据范围查询学生。
     * <p>
     * 数据隔离条件写在 SQL 里而不是查完再过滤：
     * 否则分页总数会把别的班级学生也算进去，前端页码与总数对不上。
     * <p>
     * 范围约定：学生要么还未分班（class 为空，视为待分配），要么属于当前教师名下的班级。
     * 其中 {@code t.id} 是本教师，{@code :teacherId} 是当前登录教师，用于排除同学科其他教师的班级。
     */
    @Query("""
            select u from com.finalproject.examsystem.domain.User u
            left join u.studentClass c
            left join c.teacher t
            where u.deleted = false
              and (:role is null or u.role = :role)
              and (c is null or t.id = :teacherId)
              and (:classId is null or c.id = :classId)
              and (:enabled is null or u.enabled = :enabled)
              and (:keyword is null or u.username like %:keyword% or u.realName like %:keyword%)
            """)
    Page<User> searchByTeacherScope(@Param("role") Role role,
                                    @Param("teacherId") Long teacherId,
                                    @Param("classId") Long classId,
                                    @Param("keyword") String keyword,
                                    @Param("enabled") Boolean enabled,
                                    Pageable pageable);
}
