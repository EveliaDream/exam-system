package com.finalproject.examsystem.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 学情分析测试（阶段 6 验收）。
 * <p>
 * 重点验证三件事：
 * <ol>
 *   <li><b>掌握度按分值加权</b>：全错时掌握度为 0，全对时为 1</li>
 *   <li><b>样本门槛生效</b>：交卷人数不足 30 时 sufficient=false，
 *       判断类结论标注「样本不足」，而不是编一个结论</li>
 *   <li><b>数据归属</b>：教师只能看自己考试的统计，学生只能看自己的分析与详情</li>
 * </ol>
 */
@DisplayName("学情分析")
class AnalyticsApiTest extends BaseApiTest {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private String t(int plusMinutes) {
        return LocalDateTime.now().plusMinutes(plusMinutes).format(FMT);
    }

    private List<JsonNode> pickQuestions(int n) throws Exception {
        MvcResult r = mockMvc.perform(get("/api/questions")
                        .header("Authorization", bearer(teacherToken))
                        .param("page", "1")
                        .param("size", String.valueOf(n)))
                .andExpect(status().isOk())
                .andReturn();
        List<JsonNode> list = new ArrayList<>();
        expectOk(r).get("list").forEach(list::add);
        assertThat(list.size()).isGreaterThanOrEqualTo(n);
        return list;
    }

    private long publishExam(String title, List<JsonNode> questions, int scoreEach) throws Exception {
        int total = questions.size() * scoreEach;
        MvcResult c = mockMvc.perform(post("/api/exams")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","totalScore":%d,"passScore":%d,
                                 "startTime":"%s","endTime":"%s","duration":30,
                                 "shuffleQuestion":false,"shuffleOption":false,"maxAttempt":1}"""
                                .formatted(title, total, total / 2, t(-5), t(60))))
                .andExpect(status().isOk())
                .andReturn();
        long examId = expectOk(c).get("id").asLong();

        StringBuilder items = new StringBuilder();
        for (JsonNode q : questions) {
            if (!items.isEmpty()) items.append(",");
            items.append("{\"questionId\":%d,\"score\":%d}".formatted(q.get("id").asLong(), scoreEach));
        }
        mockMvc.perform(put("/api/exams/" + examId + "/questions")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[" + items + "]}"))
                .andExpect(status().isOk());

        long classId = expectOk(mockMvc.perform(get("/api/classes/options")
                        .header("Authorization", bearer(teacherToken)))
                .andReturn()).get(0).get("id").asLong();
        mockMvc.perform(put("/api/exams/" + examId + "/classes")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classIds\":[%d]}".formatted(classId)))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/exams/" + examId + "/publish")
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk());
        return examId;
    }

    /** 学生完整作答并交卷，返回 recordId */
    private long takeExam(long examId, List<JsonNode> bank, boolean correct) throws Exception {
        MvcResult s = mockMvc.perform(post("/api/my/exams/" + examId + "/start")
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode paper = expectOk(s);
        long recordId = paper.get("recordId").asLong();

        StringBuilder sb = new StringBuilder();
        for (JsonNode q : paper.get("questions")) {
            long qid = q.get("questionId").asLong();
            JsonNode b = null;
            for (JsonNode x : bank) {
                if (x.get("id").asLong() == qid) {
                    b = x;
                    break;
                }
            }
            List<String> answer = new ArrayList<>();
            if (b != null) {
                if (correct) {
                    b.get("answer").forEach(a -> answer.add(a.asString()));
                } else {
                    answer.add("WRONG");
                }
            }
            if (!sb.isEmpty()) sb.append(",");
            sb.append("{\"questionId\":%d,\"answer\":%s,\"timeSpentSec\":20,\"submitCount\":1}"
                    .formatted(qid, toJsonArray(answer)));
        }

        mockMvc.perform(put("/api/my/records/" + recordId + "/answers")
                        .header("Authorization", bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":[" + sb + "]}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/my/records/" + recordId + "/submit")
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk());
        return recordId;
    }

    private String toJsonArray(List<String> values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(values.get(i).replace("\"", "\\\"")).append("\"");
        }
        return sb.append("]").toString();
    }

    // ==================== 教师端看板 ====================

    @Test
    @DisplayName("看板：交卷 1 人时统计正确，且标记样本不足（sufficient=false）")
    void overviewWithOneSubmission() throws Exception {
        List<JsonNode> questions = pickQuestions(3);
        String title = uniqueName("分析");
        long examId = publishExam(title, questions, 10);
        takeExam(examId, questions, true);

        MvcResult r = mockMvc.perform(get("/api/analytics/exams/" + examId)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode a = expectOk(r);

        assertThat(a.get("submittedCount").asInt()).isEqualTo(1);
        assertThat(a.get("participantCount").asInt()).isEqualTo(1);
        assertThat(a.get("avgScore").asDouble()).isEqualTo(30.0);
        assertThat(a.get("passRate").asDouble()).isEqualTo(1.0);
        assertThat(a.get("maxScore").asInt()).isEqualTo(30);
        // 1 人 < 30 人门槛 → 样本不足
        assertThat(a.get("sufficient").asBoolean()).isFalse();
        assertThat(a.get("questionStats").size()).isEqualTo(3);
        assertThat(a.get("students").size()).isEqualTo(1);
        assertThat(a.get("students").get(0).get("passed").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("看板：样本不足时每题判断标注「样本不足，仅供参考」")
    void judgementMarkedInsufficient() throws Exception {
        List<JsonNode> questions = pickQuestions(2);
        long examId = publishExam(uniqueName("样本"), questions, 10);
        takeExam(examId, questions, true);

        JsonNode a = expectOk(mockMvc.perform(get("/api/analytics/exams/" + examId)
                        .header("Authorization", bearer(teacherToken)))
                .andReturn());
        for (JsonNode q : a.get("questionStats")) {
            assertThat(q.get("judgementCode").asString()).isEqualTo("INSUFFICIENT");
            assertThat(q.get("judgement").asString()).contains("仅供参考");
        }
    }

    @Test
    @DisplayName("看板：分数段分布按得分率划分，满分为 90% 以上档")
    void scoreDistribution() throws Exception {
        List<JsonNode> questions = pickQuestions(2);
        long examId = publishExam(uniqueName("分布"), questions, 10);
        takeExam(examId, questions, true);

        JsonNode a = expectOk(mockMvc.perform(get("/api/analytics/exams/" + examId)
                        .header("Authorization", bearer(teacherToken)))
                .andReturn());
        JsonNode buckets = a.get("scoreDistribution");
        assertThat(buckets.size()).isEqualTo(5);
        // 满分 → 落在最后一档
        assertThat(buckets.get(4).get("count").asInt()).isEqualTo(1);
        assertThat(buckets.get(0).get("count").asInt()).isZero();
    }

    @Test
    @DisplayName("看板：知识点掌握度按分值加权聚合")
    void knowledgeStats() throws Exception {
        List<JsonNode> questions = pickQuestions(3);
        long examId = publishExam(uniqueName("知识点"), questions, 10);
        takeExam(examId, questions, true);

        JsonNode a = expectOk(mockMvc.perform(get("/api/analytics/exams/" + examId)
                        .header("Authorization", bearer(teacherToken)))
                .andReturn());
        JsonNode ks = a.get("knowledgeStats");
        assertThat(ks.size()).isGreaterThan(0);
        // 全对 → 每个知识点掌握度都是 1
        for (JsonNode k : ks) {
            assertThat(k.get("mastery").asDouble()).isEqualTo(1.0);
            assertThat(k.get("weak").asBoolean()).isFalse();
        }
    }

    @Test
    @DisplayName("看板：无作答时不报错，返回零值")
    void overviewWithoutSubmissions() throws Exception {
        long examId = publishExam(uniqueName("无人"), pickQuestions(1), 10);

        MvcResult r = mockMvc.perform(get("/api/analytics/exams/" + examId)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode a = expectOk(r);
        assertThat(a.get("submittedCount").asInt()).isZero();
        assertThat(a.get("avgScore").asDouble()).isZero();
        assertThat(a.get("students").size()).isZero();
    }

    @Test
    @DisplayName("越权：教师2 看不了教师1 的考试分析（50108）")
    void otherTeacherForbidden() throws Exception {
        long examId = publishExam(uniqueName("隔离分析"), pickQuestions(1), 10);
        mockMvc.perform(get("/api/analytics/exams/" + examId)
                        .header("Authorization", bearer(teacher2Token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(50108));
    }

    @Test
    @DisplayName("越权：学生不能访问教师看板（403）")
    void studentForbidden() throws Exception {
        mockMvc.perform(get("/api/analytics/exams/1")
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isForbidden());
    }

    // ==================== 学生端 ====================

    @Test
    @DisplayName("学生分析：全对时没有薄弱知识点")
    void noWeakPointsWhenAllCorrect() throws Exception {
        List<JsonNode> questions = pickQuestions(2);
        long examId = publishExam(uniqueName("全对分析"), questions, 10);
        long recordId = takeExam(examId, questions, true);

        JsonNode a = expectOk(mockMvc.perform(get("/api/my/records/" + recordId + "/analysis")
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(a.get("hasData").asBoolean()).isTrue();
        assertThat(a.get("weakPoints").size()).as("全对不应推送薄弱点").isZero();
        assertThat(a.get("allPoints").size()).isGreaterThan(0);
        for (JsonNode k : a.get("allPoints")) {
            assertThat(k.get("mastery").asDouble()).isEqualTo(1.0);
        }
    }

    @Test
    @DisplayName("学生分析：全错时所有知识点掌握度为 0 并进入薄弱列表")
    void weakPointsWhenAllWrong() throws Exception {
        List<JsonNode> questions = pickQuestions(2);
        long examId = publishExam(uniqueName("全错分析"), questions, 10);
        long recordId = takeExam(examId, questions, false);

        JsonNode a = expectOk(mockMvc.perform(get("/api/my/records/" + recordId + "/analysis")
                        .header("Authorization", bearer(studentToken)))
                .andReturn());
        assertThat(a.get("weakPoints").size()).isGreaterThan(0)
                .isLessThanOrEqualTo(5);
        for (JsonNode k : a.get("weakPoints")) {
            assertThat(k.get("mastery").asDouble()).isZero();
            assertThat(k.get("wrongCount").asInt()).isGreaterThan(0);
        }
    }

    @Test
    @DisplayName("学生详情：交卷后可看到正确答案与解析")
    void detailAfterSubmit() throws Exception {
        List<JsonNode> questions = pickQuestions(2);
        long examId = publishExam(uniqueName("详情"), questions, 10);
        long recordId = takeExam(examId, questions, true);

        MvcResult r = mockMvc.perform(get("/api/my/records/" + recordId + "/detail")
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode d = expectOk(r);
        assertThat(d.get("questions").size()).isEqualTo(2);
        for (JsonNode q : d.get("questions")) {
            // 交卷后才允许出现标准答案与解析
            assertThat(q.get("correctAnswer").size()).isGreaterThan(0);
            assertThat(q.get("correct").asBoolean()).isTrue();
            assertThat(q.get("score").asInt()).isEqualTo(q.get("maxScore").asInt());
        }
    }

    @Test
    @DisplayName("学生详情：未交卷时不能查看（60100）")
    void detailBeforeSubmitForbidden() throws Exception {
        List<JsonNode> questions = pickQuestions(1);
        long examId = publishExam(uniqueName("未交卷详情"), questions, 10);

        JsonNode paper = expectOk(mockMvc.perform(post("/api/my/exams/" + examId + "/start")
                        .header("Authorization", bearer(studentToken)))
                .andReturn());
        long recordId = paper.get("recordId").asLong();

        mockMvc.perform(get("/api/my/records/" + recordId + "/detail")
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(60100));
        mockMvc.perform(get("/api/my/records/" + recordId + "/analysis")
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("越权：拿别人的记录看分析返回 404")
    void cannotSeeOthersAnalysis() throws Exception {
        List<JsonNode> questions = pickQuestions(1);
        long examId = publishExam(uniqueName("分析归属"), questions, 10);
        long recordId = takeExam(examId, questions, true);

        String other = login("student2", "123456");
        mockMvc.perform(get("/api/my/records/" + recordId + "/analysis")
                        .header("Authorization", bearer(other)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/my/records/" + recordId + "/detail")
                        .header("Authorization", bearer(other)))
                .andExpect(status().isNotFound());
    }

    private String uniqueName(String prefix) {
        return prefix + System.currentTimeMillis() % 100000 + "_" + (int) (Math.random() * 1000);
    }
}
