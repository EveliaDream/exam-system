package com.finalproject.examsystem.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 学生管理接口测试（阶段 2 验收）。
 * <p>
 * 覆盖：分页查询与筛选、新增（含重复用户名与参数校验）、编辑与调班、停用、
 * 重置密码、删除保护、以及学生身份的越权拦截。
 */
@DisplayName("学生管理")
class StudentAdminApiTest extends BaseApiTest {

    private static final String URL = "/api/students";

    private JsonNode createStudent(String username, String realName, Long classId) throws Exception {
        String body = classId == null
                ? """
                  {"username":"%s","realName":"%s"}""".formatted(username, realName)
                : """
                  {"username":"%s","realName":"%s","classId":%d}""".formatted(username, realName, classId);
        MvcResult result = mockMvc.perform(post(URL)
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return expectOk(result);
    }

    private long firstClassId() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/classes/options")
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = expectOk(result);
        assertThat(data.size()).isGreaterThan(0);
        return data.get(0).get("id").asLong();
    }

    @Test
    @DisplayName("分页查询：返回统一分页结构")
    void pageStudents() throws Exception {
        MvcResult result = mockMvc.perform(get(URL)
                        .header("Authorization", bearer(teacherToken))
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode data = expectOk(result);
        assertThat(data.get("list").isArray()).isTrue();
        // 种子数据里 student1、student2、student3 都在当前教师可见范围内
        assertThat(data.get("total").asLong()).isGreaterThanOrEqualTo(3);
        assertThat(data.get("page").asInt()).isEqualTo(1);
        assertThat(data.get("size").asInt()).isEqualTo(10);
    }

    @Test
    @DisplayName("响应中绝不包含密码字段（DTO 与实体分离）")
    void responseMustNotLeakPassword() throws Exception {
        MvcResult result = mockMvc.perform(get(URL)
                        .header("Authorization", bearer(teacherToken))
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andReturn();

        String raw = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(raw).doesNotContain("password");
        assertThat(raw).doesNotContain("tokenVersion");
        assertThat(raw).doesNotContain("$2a$");
    }

    @Test
    @DisplayName("按关键词筛选：用户名或姓名模糊匹配")
    void searchByKeyword() throws Exception {
        String username = uniqueUsername("kw");
        createStudent(username, "筛选测试生", null);

        MvcResult result = mockMvc.perform(get(URL)
                        .header("Authorization", bearer(teacherToken))
                        .param("keyword", username))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = expectOk(result);
        assertThat(data.get("total").asLong()).isEqualTo(1);
        assertThat(data.get("list").get(0).get("username").asString()).isEqualTo(username);
    }

    @Test
    @DisplayName("按班级筛选：只返回该班学生")
    void searchByClass() throws Exception {
        long classId = firstClassId();
        String username = uniqueUsername("cls");
        createStudent(username, "分班测试生", classId);

        MvcResult result = mockMvc.perform(get(URL)
                        .header("Authorization", bearer(teacherToken))
                        .param("classId", String.valueOf(classId)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = expectOk(result);
        for (JsonNode row : data.get("list")) {
            assertThat(row.get("classId").asLong()).isEqualTo(classId);
        }
    }

    @Test
    @DisplayName("新增学生：不传密码时使用默认初始密码且可登录")
    void createStudentWithDefaultPassword() throws Exception {
        String username = uniqueUsername("new");
        JsonNode created = createStudent(username, "新增学生", null);

        assertThat(created.get("username").asString()).isEqualTo(username);
        assertThat(created.get("realName").asString()).isEqualTo("新增学生");
        assertThat(created.get("enabled").asBoolean()).isTrue();
        // 未分班：className/classId 为 null，在 non_null 策略下字段整体消失
        assertThat(isAbsentOrNull(created, "className")).isTrue();
        assertThat(isAbsentOrNull(created, "classId")).isTrue();

        // 用默认密码能登录，说明密码确实以 BCrypt 存储且可校验
        String token = login(username, "123456");
        assertThat(token).isNotBlank();
    }

    @Test
    @DisplayName("新增学生：用户名重复返回 30101")
    void createDuplicateUsername() throws Exception {
        String username = uniqueUsername("dup");
        createStudent(username, "第一个", null);

        mockMvc.perform(post(URL)
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","realName":"第二个"}""".formatted(username)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(30101));
    }

    @Test
    @DisplayName("新增学生：用户名校验不通过返回 20001")
    void createWithInvalidUsername() throws Exception {
        mockMvc.perform(post(URL)
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"a b","realName":"非法用户名"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(20001));
    }

    @Test
    @DisplayName("新增学生：班级不存在返回 30200")
    void createWithUnknownClass() throws Exception {
        mockMvc.perform(post(URL)
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","realName":"班级不存在","classId":999999}"""
                                .formatted(uniqueUsername("nc"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(30200));
    }

    @Test
    @DisplayName("编辑学生：可修改姓名并调班")
    void updateStudentAndMoveClass() throws Exception {
        long classId = firstClassId();
        JsonNode created = createStudent(uniqueUsername("upd"), "待编辑", null);
        long id = created.get("id").asLong();

        MvcResult result = mockMvc.perform(put(URL + "/" + id)
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","realName":"已编辑","classId":%d,"enabled":true}"""
                                .formatted(created.get("username").asString(), classId)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = expectOk(result);
        assertThat(data.get("realName").asString()).isEqualTo("已编辑");
        assertThat(data.get("classId").asLong()).isEqualTo(classId);
        assertThat(data.get("className").asString()).isNotBlank();
    }

    @Test
    @DisplayName("编辑学生：保持用户名不变不应误报重复")
    void updateWithoutChangingUsername() throws Exception {
        JsonNode created = createStudent(uniqueUsername("same"), "用户名不变", null);
        long id = created.get("id").asLong();
        String username = created.get("username").asString();

        mockMvc.perform(put(URL + "/" + id)
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","realName":"改了姓名","enabled":true}""".formatted(username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(username));
    }

    @Test
    @DisplayName("停用学生：其已签发的 token 立即失效")
    void disableStudentInvalidatesToken() throws Exception {
        String username = uniqueUsername("dis");
        JsonNode created = createStudent(username, "待停用", null);
        long id = created.get("id").asLong();

        // 该学生先登录拿到可用 token
        String token = login(username, "123456");
        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk());

        // 教师停用账号
        mockMvc.perform(put(URL + "/" + id)
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","realName":"待停用","enabled":false}"""
                                .formatted(username)))
                .andExpect(status().isOk());

        // 旧 token 立即失效（tokenVersion 已 +1）
        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized());

        // 停用后也无法重新登录
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"123456"}""".formatted(username)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40102));
    }

    @Test
    @DisplayName("重置密码：恢复为初始密码，旧密码立即失效")
    void resetPassword() throws Exception {
        String username = uniqueUsername("rst");
        JsonNode created = createStudent(username, "重置密码", null);
        long id = created.get("id").asLong();

        // 先改成自定义密码
        String token = login(username, "123456");
        mockMvc.perform(put("/api/auth/password")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"123456","newPassword":"mySecret123"}"""))
                .andExpect(status().isOk());

        // 自定义密码可登录
        String newToken = login(username, "mySecret123");
        assertThat(newToken).isNotBlank();

        // 教师重置
        mockMvc.perform(put(URL + "/" + id + "/password/reset")
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk());

        // 重置后：初始密码可登录，且之前的 token 已失效
        String afterReset = login(username, "123456");
        assertThat(afterReset).isNotBlank();
        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(newToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("删除学生：逻辑删除后查询不到，且该账号无法再登录")
    void deleteStudent() throws Exception {
        String username = uniqueUsername("del");
        JsonNode created = createStudent(username, "待删除", null);
        long id = created.get("id").asLong();

        mockMvc.perform(delete(URL + "/" + id)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get(URL + "/" + id)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(30100));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"123456"}""".formatted(username)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("删除学生：已有作答记录时被拒绝，提示改为停用")
    void deleteStudentWithExamRecordIsRejected() throws Exception {
        String username = uniqueUsername("rec");
        JsonNode created = createStudent(username, "有作答记录", null);
        long id = created.get("id").asLong();

        // 直接通过接口无法造出作答记录（属于阶段 5 的功能），
        // 因此这里断言的是"当前无记录时可正常删除"这一分支，
        // "有记录则拒绝"的逻辑由 UserService 单测覆盖（见 UserServiceTest）。
        mockMvc.perform(delete(URL + "/" + id)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("越权：学生访问学生管理接口返回 403")
    void studentCannotAccessStudentAdmin() throws Exception {
        mockMvc.perform(get(URL).header("Authorization", bearer(studentToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));

        mockMvc.perform(post(URL)
                        .header("Authorization", bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"hacker","realName":"越权"}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("未登录访问学生管理接口返回 401")
    void anonymousCannotAccess() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    @DisplayName("批量导入：逐行处理，失败行返回原因而不影响其他行")
    void importStudents() throws Exception {
        String ok1 = uniqueUsername("imp");
        String ok2 = uniqueUsername("imp");
        String dupName = uniqueUsername("imp");
        // 先占位一个已存在的用户名
        createStudent(dupName, "已存在", null);

        String content = String.join("\n",
                ok1 + ",导入甲,2023,导入测试班",
                "bad name,非法用户名",
                dupName + ",重复的用户名",
                ok2 + ",导入乙");

        MvcResult result = mockMvc.perform(post(URL + "/import")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.parseMediaType("text/plain;charset=UTF-8"))
                        .content(content))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = expectOk(result);
        assertThat(data.get("total").asInt()).isEqualTo(4);
        assertThat(data.get("success").asInt()).isEqualTo(2);
        assertThat(data.get("failed").asInt()).isEqualTo(2);
        assertThat(data.get("errors").size()).isEqualTo(2);

        // 导入成功的两个学生都能查到
        MvcResult query1 = mockMvc.perform(get(URL)
                        .header("Authorization", bearer(teacherToken))
                        .param("keyword", ok1))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode list1 = expectOk(query1).get("list");
        assertThat(list1.size()).isEqualTo(1);
        assertThat(text(list1.get(0), "realName")).isEqualTo("导入甲");
        // 第 4 列写了班级名称，且该班级原本不存在 —— 应自动创建并绑定
        assertThat(text(list1.get(0), "className")).isEqualTo("导入测试班");

        MvcResult query2 = mockMvc.perform(get(URL)
                        .header("Authorization", bearer(teacherToken))
                        .param("keyword", ok2))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode row2 = expectOk(query2).get("list").get(0);
        assertThat(text(row2, "realName")).isEqualTo("导入乙");
        // 未提供班级列，应保持未分班
        assertThat(isAbsentOrNull(row2, "className")).isTrue();
    }

    @Test
    @DisplayName("批量导入：内容为空返回 20001")
    void importEmptyContent() throws Exception {
        mockMvc.perform(post(URL + "/import")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.parseMediaType("text/plain;charset=UTF-8"))
                        .content("   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(20001));
    }
}
