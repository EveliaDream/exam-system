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
 * 知识点与题库管理测试（阶段 3 验收）。
 * <p>
 * 重点覆盖「影响学情统计正确性」的几条规则：
 * 知识点同课程内不可重名、被引用不可删除；题目的答案结构必须合法（否则判分必错）。
 */
@DisplayName("知识点与题库")
class QuestionBankApiTest extends BaseApiTest {

    private static final String KP_URL = "/api/knowledge-points";
    private static final String Q_URL = "/api/questions";

    private String uniqueName(String prefix) {
        return prefix + System.currentTimeMillis() % 100000 + "_" + (int) (Math.random() * 1000);
    }

    private long createKp(String name, Long courseId) throws Exception {
        MvcResult r = mockMvc.perform(post(KP_URL)
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","courseId":%s,"sortOrder":1}"""
                                .formatted(name, courseId == null ? "null" : courseId.toString())))
                .andExpect(status().isOk())
                .andReturn();
        return expectOk(r).get("id").asLong();
    }

    private JsonNode createQuestion(String body) throws Exception {
        MvcResult r = mockMvc.perform(post(Q_URL)
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return expectOk(r);
    }

    @Test
    @DisplayName("知识点：新增并返回引用题数 0")
    void createKnowledgePoint() throws Exception {
        String name = uniqueName("知识点");
        MvcResult r = mockMvc.perform(post(KP_URL)
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","sortOrder":5}""".formatted(name)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = expectOk(r);
        assertThat(data.get("name").asString()).isEqualTo(name);
        assertThat(data.get("questionCount").asLong()).isZero();
    }

    @Test
    @DisplayName("知识点：同课程下重名返回 40108")
    void duplicateKnowledgePoint() throws Exception {
        String name = uniqueName("重复");
        createKp(name, null);
        mockMvc.perform(post(KP_URL)
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s"}""".formatted(name)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40108));
    }

    @Test
    @DisplayName("题目：新增时挂多个知识点，响应按重算结果返回标签")
    void createQuestionWithKnowledgePoints() throws Exception {
        long kp1 = createKp(uniqueName("计算"), null);
        long kp2 = createKp(uniqueName("语法"), null);

        JsonNode q = createQuestion("""
                {"type":"SINGLE","stem":"下列哪个是 Java 关键字？","options":["class","Class","klass","K"],
                 "answer":["A"],"score":5,"difficulty":"EASY","analysis":"class 是关键字",
                 "knowledgePointIds":[%d,%d],"enabled":true}""".formatted(kp1, kp2));

        assertThat(q.get("knowledgePoints").size()).isEqualTo(2);
        assertThat(q.get("typeLabel").asString()).isEqualTo("单选题");
    }

    @Test
    @DisplayName("题目：单选答案不唯一返回 40106")
    void singleChoiceWithMultipleAnswers() throws Exception {
        long kp = createKp(uniqueName("单选"), null);
        mockMvc.perform(post(Q_URL)
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"SINGLE","stem":"非法单选","options":["a","b"],"answer":["A","B"],
                                 "score":5,"difficulty":"EASY","knowledgePointIds":[%d]}""".formatted(kp)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40106));
    }

    @Test
    @DisplayName("题目：答案标识超出选项范围返回 40106")
    void answerOutOfOptionRange() throws Exception {
        long kp = createKp(uniqueName("越界"), null);
        mockMvc.perform(post(Q_URL)
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"SINGLE","stem":"答案越界","options":["a","b"],"answer":["D"],
                                 "score":5,"difficulty":"EASY","knowledgePointIds":[%d]}""".formatted(kp)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40106));
    }

    @Test
    @DisplayName("题目：多选题答案少于两个返回 40106")
    void multipleChoiceWithOneAnswer() throws Exception {
        long kp = createKp(uniqueName("多选"), null);
        mockMvc.perform(post(Q_URL)
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"MULTIPLE","stem":"非法多选","options":["a","b","c"],"answer":["A"],
                                 "score":10,"difficulty":"MEDIUM","knowledgePointIds":[%d]}""".formatted(kp)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40106));
    }

    @Test
    @DisplayName("题目：判断题答案必须为 T/F")
    void judgeAnswerMustBeTOrF() throws Exception {
        long kp = createKp(uniqueName("判断"), null);
        mockMvc.perform(post(Q_URL)
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"JUDGE","stem":"非法判断","options":["正确","错误"],"answer":["YES"],
                                 "score":5,"difficulty":"EASY","knowledgePointIds":[%d]}""".formatted(kp)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40106));
    }

    @Test
    @DisplayName("题目：引用不存在的知识点返回 40107")
    void nonexistentKnowledgePoint() throws Exception {
        mockMvc.perform(post(Q_URL)
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"SINGLE","stem":"知识点不存在","options":["a","b"],"answer":["A"],
                                 "score":5,"difficulty":"EASY","knowledgePointIds":[999999]}"""))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40107));
    }

    @Test
    @DisplayName("知识点：被题目引用时禁止删除（40109）")
    void cannotDeleteReferencedKnowledgePoint() throws Exception {
        long kp = createKp(uniqueName("被引用"), null);
        createQuestion("""
                {"type":"SINGLE","stem":"引用知识点的题","options":["a","b"],"answer":["A"],
                 "score":5,"difficulty":"EASY","knowledgePointIds":[%d]}""".formatted(kp));

        // 引用数为 1
        MvcResult detail = mockMvc.perform(get(KP_URL + "/" + kp)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(expectOk(detail).get("questionCount").asLong()).isEqualTo(1);

        mockMvc.perform(delete(KP_URL + "/" + kp)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40109));
    }

    @Test
    @DisplayName("题目：删除后解除知识点关联，知识点可删")
    void deleteQuestionReleasesKnowledgePoint() throws Exception {
        long kp = createKp(uniqueName("释放"), null);
        JsonNode q = createQuestion("""
                {"type":"SINGLE","stem":"会被删除的题","options":["a","b"],"answer":["A"],
                 "score":5,"difficulty":"EASY","knowledgePointIds":[%d]}""".formatted(kp));
        long qid = q.get("id").asLong();

        mockMvc.perform(delete(Q_URL + "/" + qid)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk());

        // 关联已解除，知识点可以删除
        mockMvc.perform(delete(KP_URL + "/" + kp)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("题目：按知识点筛选只返回关联该知识点的题")
    void filterByKnowledgePoint() throws Exception {
        long kp = createKp(uniqueName("筛选"), null);
        createQuestion("""
                {"type":"SINGLE","stem":"筛选目标题","options":["a","b"],"answer":["A"],
                 "score":5,"difficulty":"EASY","knowledgePointIds":[%d]}""".formatted(kp));

        MvcResult r = mockMvc.perform(get(Q_URL)
                        .header("Authorization", bearer(teacherToken))
                        .param("knowledgePointId", String.valueOf(kp)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = expectOk(r);
        assertThat(data.get("total").asLong()).isEqualTo(1);
        assertThat(data.get("list").get(0).get("knowledgePoints").size()).isEqualTo(1);
    }

    @Test
    @DisplayName("越权：学生访问知识点与题库接口均返回 403")
    void studentForbidden() throws Exception {
        mockMvc.perform(get(KP_URL).header("Authorization", bearer(studentToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(Q_URL).header("Authorization", bearer(studentToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(Q_URL)
                        .header("Authorization", bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"SINGLE","stem":"越权","options":["a","b"],"answer":["A"],
                                 "score":5,"difficulty":"EASY"}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("未登录访问题库接口返回 401")
    void anonymousUnauthorized() throws Exception {
        mockMvc.perform(get(Q_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }
}
