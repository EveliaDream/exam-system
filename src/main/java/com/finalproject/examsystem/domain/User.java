package com.finalproject.examsystem.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 用户（学生与教师共用）。
 * <p>
 * 表名用 sys_user 而非 user：user 在 MySQL 8 与 H2 中都是保留字，直接建表会报错。
 */
@Entity
@Table(name = "sys_user")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    /** BCrypt 密文，绝不存明文 */
    @Column(nullable = false, length = 100)
    private String password;

    @Column(name = "real_name", nullable = false, length = 50)
    private String realName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    /** 学生所属班级；教师为 null */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_id")
    private StudentClass studentClass;

    @Column(nullable = false)
    private Boolean enabled = true;

    /**
     * token 版本号：JWT 无状态无法服务端吊销，改密码或强制下线时 +1，
     * 过滤器校验不一致即视为失效。这是删除 Redis 后的登出失效方案。
     */
    @Column(name = "token_version", nullable = false)
    private Integer tokenVersion = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(nullable = false)
    private Boolean deleted = false;

    public static User create(String username, String encodedPassword, String realName, Role role) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(encodedPassword);
        user.setRealName(realName);
        user.setRole(role);
        return user;
    }

    /** Spring Security 的权限字符串 */
    public String authority() {
        return "ROLE_" + role.name();
    }
}
