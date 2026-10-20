package com.finalproject.examsystem.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 教师间数据隔离测试（阶段 2 的核心安全约束）。
 * <p>
 * 这是本项目"权限边界"最容易出错的地方：仅仅用 @PreAuthorize 拦住学生是不够的，
 * 还必须保证教师 A 看不到、也改不动教师 B 名下的班级与学生。
 * 种子数据中 teacher2（刘老师）名下有一个"网络工程 2301 班"，专门用于验证这一点。
 */
@DisplayName("教师数据隔离")
class DataScopeApiTest extends BaseApiTest {

    private static final String CLASS_URL = "/api/classes";
    private static final String STUDENT_URL = "/api/students";

    /** 取 teacher2 名下班级的 id */
    private long teacher2ClassId() throws Exception {
        MvcResult result = mockMvc.perform(get(CLASS_URL + "/options")
                        .header("Authorization", bearer(teacher2Token)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = expectOk(result);
        assertThat(data.size()).isGreaterThan(0);
        return data.get(0).get("id").asLong();
    }

    @Test
    @DisplayName("班级列表：teacher 看不到 teacher2 名下的班级")
    void classListIsScopedToCurrentTeacher() throws Exception {
        long otherClassId = teacher2ClassId();

        MvcResult result = mockMvc.perform(get(CLASS_URL)
                        .header("Authorization", bearer(teacherToken))
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = expectOk(result);
        for (JsonNode row : data.get("list")) {
            assertThat(row.get("id").asLong()).isNotEqualTo(otherClassId);
        }
    }

    @Test
    @DisplayName("班级下拉：teacher 的选项里不含 teacher2 的班级")
    void classOptionsExcludeOtherTeacher() throws Exception {
        long otherClassId = teacher2ClassId();

        MvcResult result = mockMvc.perform(get(CLASS_URL + "/options")
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = expectOk(result);
        for (JsonNode row : data) {
            assertThat(row.get("id").asLong()).isNotEqualTo(otherClassId);
        }
    }

    @Test
    @DisplayName("越权修改班级：teacher 改 teacher2 的班级返回 30203")
    void cannotUpdateOtherTeacherClass() throws Exception {
        long otherClassId = teacher2ClassId();

        mockMvc.perform(put(CLASS_URL + "/" + otherClassId)
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"被篡改的班级","grade":"2023"}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(30203));
    }

    @Test
    @DisplayName("越权删除班级：teacher 删 teacher2 的班级返回 30203")
    void cannotDeleteOtherTeacherClass() throws Exception {
        long otherClassId = teacher2ClassId();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete(CLASS_URL + "/" + otherClassId)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(30203));
    }

    @Test
    @DisplayName("学生列表：teacher 看不到 teacher2 班里的学生")
    void studentListIsScopedToCurrentTeacher() throws Exception {
        long otherClassId = teacher2ClassId();

        // 往 teacher2 的班级里放一个学生
        String username = uniqueUsername("iso");
        mockMvc.perform(post(STUDENT_URL)
                        .header("Authorization", bearer(teacher2Token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","realName":"其他班学生","classId":%d}"""
                                .formatted(username, otherClassId)))
                .andExpect(status().isOk());

        // teacher 按该班级筛选，应该查不到任何人
        MvcResult byClass = mockMvc.perform(get(STUDENT_URL)
                        .header("Authorization", bearer(teacherToken))
                        .param("classId", String.valueOf(otherClassId)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(expectOk(byClass).get("total").asLong()).isZero();

        // teacher 用关键词也搜不到（隔离在 SQL 里，不依赖前端筛选）
        MvcResult byKeyword = mockMvc.perform(get(STUDENT_URL)
                        .header("Authorization", bearer(teacherToken))
                        .param("keyword", username))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(expectOk(byKeyword).get("total").asLong()).isZero();

        // teacher2 自己能看到
        MvcResult mine = mockMvc.perform(get(STUDENT_URL)
                        .header("Authorization", bearer(teacher2Token))
                        .param("keyword", username))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(expectOk(mine).get("total").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("越权新增：teacher 不能把学生分到 teacher2 的班级")
    void cannotAssignStudentToOtherTeacherClass() throws Exception {
        long otherClassId = teacher2ClassId();

        mockMvc.perform(post(STUDENT_URL)
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","realName":"非法分班","classId":%d}"""
                                .formatted(uniqueUsername("x"), otherClassId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(30203));
    }

    @Test
    @DisplayName("越权修改学生：teacher 不能改 teacher2 班里的学生")
    void cannotUpdateOtherTeacherStudent() throws Exception {
        long otherClassId = teacher2ClassId();
        String username = uniqueUsername("iso2");

        MvcResult created = mockMvc.perform(post(STUDENT_URL)
                        .header("Authorization", bearer(teacher2Token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","realName":"其他班学生","classId":%d}"""
                                .formatted(username, otherClassId)))
                .andExpect(status().isOk())
                .andReturn();
        long studentId = expectOk(created).get("id").asLong();

        mockMvc.perform(put(STUDENT_URL + "/" + studentId)
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","realName":"被篡改"}""".formatted(username)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(30203));

        // 教师重置其他班学生密码同样应被拒绝
        mockMvc.perform(put(STUDENT_URL + "/" + studentId + "/password/reset")
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(30203));
    }

    @Test
    @DisplayName("未分班学生：视为待分配资源，任何教师都可编辑并分班")
    void unassignedStudentIsManageable() throws Exception {
        // 种子里 student3（赵小刚）未分班
        MvcResult query = mockMvc.perform(get(STUDENT_URL)
                        .header("Authorization", bearer(teacherToken))
                        .param("keyword", "student3"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode list = expectOk(query).get("list");
        assertThat(list.size()).isEqualTo(1);
        JsonNode student3 = list.get(0);
        assertThat(isAbsentOrNull(student3, "className")).isTrue();

        // teacher2 也能看到这个未分班学生（不受教师数据范围限制）
        MvcResult byOther = mockMvc.perform(get(STUDENT_URL)
                        .header("Authorization", bearer(teacher2Token))
                        .param("keyword", "student3"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(expectOk(byOther).get("total").asLong()).isEqualTo(1);

        // 由 teacher 给其分班
        long targetClassId = expectOk(mockMvc.perform(get(CLASS_URL + "/options")
                        .header("Authorization", bearer(teacherToken)))
                .andReturn()).get(0).get("id").asLong();

        mockMvc.perform(put(STUDENT_URL + "/" + student3.get("id").asLong())
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"student3","realName":"赵小刚","classId":%d,"enabled":true}"""
                                .formatted(targetClassId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.classId").value(targetClassId));
    }
}
