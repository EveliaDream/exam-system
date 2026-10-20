package com.finalproject.examsystem.security;

import com.finalproject.examsystem.domain.User;
import com.finalproject.examsystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 从 sys_user 表加载用户，供 Spring Security 认证使用。 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsernameAndDeletedFalse(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + username));
        return UserPrincipal.from(user);
    }
}
