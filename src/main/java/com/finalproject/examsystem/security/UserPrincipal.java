package com.finalproject.examsystem.security;

import com.finalproject.examsystem.domain.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * 登录用户主体。
 * <p>
 * 额外携带 userId 与 tokenVersion，便于业务层直接获取当前用户、以及 JWT 校验版本号。
 */
public record UserPrincipal(Long userId,
                            String username,
                            String password,
                            String realName,
                            String role,
                            int tokenVersion,
                            boolean enabled) implements UserDetails {

    public static UserPrincipal from(User user) {
        return new UserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getPassword(),
                user.getRealName(),
                user.getRole().name(),
                user.getTokenVersion(),
                Boolean.TRUE.equals(user.getEnabled()));
    }

    /**
     * record 生成的访问器是 username() / password()，而 UserDetails 要求 getUsername() / getPassword()，
     * 两者不会自动互相满足，必须显式实现。
     */
    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
