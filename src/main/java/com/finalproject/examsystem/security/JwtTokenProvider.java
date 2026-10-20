package com.finalproject.examsystem.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 签发与校验。
 * <p>
 * payload 约定：
 * <pre>
 * sub   用户名
 * uid   用户主键
 * role  STUDENT / TEACHER
 * tv    token 版本号，与 sys_user.token_version 比对，用于改密码后使旧 token 失效
 * </pre>
 * 说明：本类只依赖 jjwt + JDK，若后期需要刷新令牌、黑名单等能力，替换实现不影响其他层。
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private final String secret;
    private final long expireMillis;

    private SecretKey key;

    public JwtTokenProvider(@Value("${exam.jwt.secret}") String secret,
                            @Value("${exam.jwt.expire-millis}") long expireMillis) {
        this.secret = secret;
        this.expireMillis = expireMillis;
    }

    @PostConstruct
    void init() {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            // HS256 要求密钥至少 256 bit，短密钥直接拒绝启动比运行期报错更好排查
            throw new IllegalStateException("exam.jwt.secret 长度不足 32 字节，请检查配置");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    public String generate(Long userId, String username, String role, int tokenVersion) {
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .claim("uid", userId)
                .claim("role", role)
                .claim("tv", tokenVersion)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMillis))
                .signWith(key)
                .compact();
    }

    /** 解析并校验签名与过期时间；任何异常都返回 null，由调用方按"未登录"处理 */
    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("token 校验失败: {}", e.getMessage());
            return null;
        }
    }

    public long getExpireMillis() {
        return expireMillis;
    }
}
