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
 * 班级管理接口测试（阶段 2 验收）。
 * <p>
 * 重点覆盖两条业务规则：班级重名拦截、班级内仍有学生时禁止删除。
 */
@DisplayName("班级管理")
class ClassAdminApiTest extends BaseApiTest {

    private static final String URL = "/api/classes";

    private JsonNode createClass(String name, String grade) throws Exception {
        MvcResult result = mockMvc.perform(post(URL)
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","grade":"%s"}""".formatted(name, grade)))
                .andExpect(status().isOk())
                .andReturn();
        return expectOk(result);
    }

    private String uniqueClassName() {
        return "测试班级_" + System.currentTimeMillis() % 100000 + "_" + (int) (Math.random() * 1000);
    }

    @Test
    @DisplayName("分页查询班级：返回统一分页结构")
    void pageClasses() throws Exception {
        MvcResult result = mockMvc.perform(get(URL)
                        .header("Authorization", bearer(teacherToken))
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode data = expectOk(result);
        assertThat(data.get("list").isArray()).isTrue();
        assertThat(data.get("total").asLong()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("新增班级：返回班级信息且学生数为 0")
    void createClassSuccess() throws Exception {
        String name = uniqueClassName();
        JsonNode created = createClass(name, "2024");

        assertThat(created.get("name").asString()).isEqualTo(name);
        assertThat(created.get("grade").asString()).isEqualTo("2024");
        assertThat(created.get("studentCount").asLong()).isZero();
        assertThat(created.get("teacherName").asString()).isEqualTo("张老师");
    }

    @Test
    @DisplayName("新增班级：名称重复返回 30201")
    void createDuplicateName() throws Exception {
        String name = uniqueClassName();
        createClass(name, "2024");

        mockMvc.perform(post(URL)
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","grade":"2024"}""".formatted(name)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(30201));
    }

    @Test
    @DisplayName("新增班级：名称为空返回 20001")
    void createWithBlankName() throws Exception {
        mockMvc.perform(post(URL)
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","grade":"2024"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(20001));
    }

    @Test
    @DisplayName("修改班级：保持不变不应误报重名")
    void updateWithoutChangingName() throws Exception {
        JsonNode created = createClass(uniqueClassName(), "2023");
        long id = created.get("id").asLong();
        String name = created.get("name").asString();

        mockMvc.perform(put(URL + "/" + id)
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","grade":"2025"}""".formatted(name)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.grade").value("2025"))
                .andExpect(jsonPath("$.data.name").value(name));
    }

    @Test
    @DisplayName("修改班级：改成已存在的名称返回 30201")
    void updateToExistingName() throws Exception {
        String nameA = uniqueClassName();
        String nameB = uniqueClassName();
        createClass(nameA, "2023");
        JsonNode b = createClass(nameB, "2023");

        mockMvc.perform(put(URL + "/" + b.get("id").asLong())
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","grade":"2023"}""".formatted(nameA)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(30201));
    }

    @Test
    @DisplayName("删除班级：空班级可删除，删除后查询返回 30200")
    void deleteEmptyClass() throws Exception {
        JsonNode created = createClass(uniqueClassName(), "2024");
        long id = created.get("id").asLong();

        mockMvc.perform(delete(URL + "/" + id)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get(URL + "/" + id)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(30200));
    }

    @Test
    @DisplayName("删除班级：班级内仍有学生时返回 30202 并提示人数")
    void deleteClassWithStudentsIsRejected() throws Exception {
        JsonNode created = createClass(uniqueClassName(), "2024");
        long classId = created.get("id").asLong();

        // 往该班级放一个学生
        mockMvc.perform(post("/api/students")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","realName":"占位学生","classId":%d}"""
                                .formatted(uniqueUsername("cls"), classId)))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(delete(URL + "/" + classId)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(30202))
                .andReturn();

        assertThat(json(result).get("message").asString()).contains("1 名学生");

        // 班级仍然存在
        mockMvc.perform(get(URL + "/" + classId)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("班级下拉选项：只返回当前教师名下的班级")
    void classOptions() throws Exception {
        // 先取当前登录教师的 userId，用于校验返回的班级归属
        long myId = expectOk(mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andReturn())
                .get("userId").asLong();

        MvcResult result = mockMvc.perform(get(URL + "/options")
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = expectOk(result);
        assertThat(data.size()).isGreaterThanOrEqualTo(2);
        for (JsonNode row : data) {
            assertThat(row.get("teacherId").asLong()).isEqualTo(myId);
        }
    }

    @Test
    @DisplayName("越权：学生访问班级管理接口返回 403")
    void studentCannotAccess() throws Exception {
        mockMvc.perform(get(URL).header("Authorization", bearer(studentToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }

    @Test
    @DisplayName("未登录访问班级管理接口返回 401")
    void anonymousCannotAccess() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }
}
