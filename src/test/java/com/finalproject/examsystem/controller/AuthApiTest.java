package com.finalproject.examsystem.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 认证与权限的集成测试（阶段 0 的验收标准）。
 * <p>
 * 用 MockMvc 而不是真实端口：测试不依赖 8080 是否被占用，且能断言到 JSON 细节。
 * 数据来自种子数据（DevDataSeeder），账号固定为 teacher / student1，密码 123456。
 * <p>
 * <b>测试固定使用 h2 profile</b>：测试会大量增删数据，绝不能跑在开发用的 MySQL 上，
 * 否则会把演示数据搅乱。改 profile 时请同步修改 {@link BaseApiTest}（两者需一致才能复用同一个 Spring 上下文）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@DisplayName("认证与权限")
class AuthApiTest {

    private static final String LOGIN_URL = "/api/auth/login";
    private static final String ME_URL = "/api/auth/me";
    private static final String TEACHER_ONLY_URL = "/api/users/profile";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}""".formatted(username, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        // 必须显式指定 UTF-8，否则 getContentAsString() 默认按 ISO-8859-1 解码，中文会乱码
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("data").get("token").asString();
    }

    @Test
    @DisplayName("教师用正确密码登录：返回可用 token")
    void loginSuccess() throws Exception {
        String token = login("teacher", "123456");
        assertThat(token).isNotBlank();

        mockMvc.perform(get(ME_URL).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value("teacher"))
                .andExpect(jsonPath("$.data.role").value("TEACHER"));
    }

    @Test
    @DisplayName("密码错误：返回 401，且不暴露账号是否存在")
    void loginWithWrongPassword() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"teacher","password":"wrong-password"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    @DisplayName("用户名不存在：提示与密码错误一致，避免账号枚举")
    void loginWithUnknownUser() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"not-exist-user","password":"123456"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    @DisplayName("参数校验：用户名为空返回 400")
    void loginWithBlankUsername() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"","password":"123456"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(20001));
    }

    @Test
    @DisplayName("未携带 token 访问受保护接口：返回 401 统一 JSON（不是 HTML 登录页）")
    void accessProtectedWithoutToken() throws Exception {
        mockMvc.perform(get(ME_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    @DisplayName("伪造 token：返回 401")
    void accessWithInvalidToken() throws Exception {
        mockMvc.perform(get(ME_URL).header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    @DisplayName("学生 token 访问教师接口：返回 403（权限边界生效）")
    void studentCannotAccessTeacherEndpoint() throws Exception {
        String studentToken = login("student1", "123456");

        mockMvc.perform(get(TEACHER_ONLY_URL).header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }

    @Test
    @DisplayName("教师 token 访问教师接口：返回 200")
    void teacherCanAccessTeacherEndpoint() throws Exception {
        String teacherToken = login("teacher", "123456");

        mockMvc.perform(get(TEACHER_ONLY_URL).header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isTeacher").value(true));
    }
}
