package com.finalproject.examsystem.domain;

/**
 * 用户角色。
 * <p>
 * 学生与教师共用一张 user 表，用该字段区分，避免两套登录逻辑。
 * Spring Security 中对应权限字符串 ROLE_STUDENT / ROLE_TEACHER。
 */
public enum Role {
    STUDENT("学生"),
    TEACHER("教师");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
