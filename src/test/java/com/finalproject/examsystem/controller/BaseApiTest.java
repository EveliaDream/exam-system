package com.finalproject.examsystem.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 测试基类：封装登录取 token、JSON 解析、唯一用户名生成等公共逻辑。
 * <p>
 * <b>测试固定使用 h2 profile</b>：测试会大量增删数据，绝不能跑在开发用的 MySQL 上，
 * 否则会把演示数据搅乱、且失败用例之间互相污染。用内存库时每个测试 JVM 都是干净起点。
 * <p>
 * 说明：多个测试类共享同一个 Spring 上下文与内存数据库，
 * 因此创建数据时必须保证唯一性（见 {@link #uniqueUsername}），否则测试之间会互相污染。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
public abstract class BaseApiTest {

    private static final AtomicInteger SEQ = new AtomicInteger(1);

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    protected String teacherToken;
    protected String teacher2Token;
    protected String studentToken;

    @BeforeEach
    void loginAll() throws Exception {
        teacherToken = login("teacher", "123456");
        teacher2Token = login("teacher2", "123456");
        studentToken = login("student1", "123456");
    }

    protected String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}""".formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).get("data").get("token").asString();
    }

    /** 必须显式指定 UTF-8，否则 getContentAsString() 默认按 ISO-8859-1 解码，中文会乱码 */
    protected JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    protected String uniqueUsername(String prefix) {
        return prefix + System.currentTimeMillis() % 100000 + "_" + SEQ.getAndIncrement();
    }

    protected String bearer(String token) {
        return "Bearer " + token;
    }

    /**
     * 校验接口返回 200 且 code=0，并返回 data 节点，便于链式断言。
     */
    protected JsonNode expectOk(MvcResult result) throws Exception {
        JsonNode body = json(result);
        assertThat(body.get("code").asInt()).isZero();
        return body.get("data");
    }

    /**
     * 判断某字段是否"不存在或为 null"。
     * <p>
     * 必须用 path() 而不是 get()：项目配置了 {@code spring.jackson.default-property-inclusion=non_null}，
     * 值为 null 的字段在 JSON 中会**整个消失**，此时 {@code get()} 返回 null 会直接 NPE，
     * 而 {@code path()} 会返回 MissingNode，语义才是"该字段无值"。
     */
    protected boolean isAbsentOrNull(JsonNode node, String field) {
        return node.path(field).isMissingNode() || node.path(field).isNull();
    }

    /** 读取字符串字段；字段不存在时返回 null 而不是抛异常 */
    protected String text(JsonNode node, String field) {
        return isAbsentOrNull(node, field) ? null : node.path(field).asString();
    }
}
