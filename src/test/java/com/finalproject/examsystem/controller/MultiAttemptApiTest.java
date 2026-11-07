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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 多轮考试（补考 / 重考）测试。
 * <p>
 * 关键规则：
 * <ol>
 *   <li>限考次数用尽 → 不再新建记录，返回最近一次的结束态试卷壳</li>
 *   <li>有进行中的记录 → 优先恢复它，而不是新开一轮（否则刷新页面就会白白消耗一次机会）</li>
 *   <li>每轮记录 attemptNo 递增，成绩单上能区分「第 1 次 58 分、第 2 次 72 分」</li>
 * </ol>
 */
@DisplayName("多轮考试")
class MultiAttemptApiTest extends BaseApiTest {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private String t(int plusMinutes) {
        return LocalDateTime.now().plusMinutes(plusMinutes).format(FMT);
    }

    private List<JsonNode> pickQuestions(int n) throws Exception {
        MvcResult r = mockMvc.perform(get("/api/questions")
                        .header("Authorization", bearer(teacherToken))
                        .param("size", String.valueOf(n)))
                .andExpect(status().isOk())
                .andReturn();
        List<JsonNode> list = new ArrayList<>();
        expectOk(r).get("list").forEach(list::add);
        return list;
    }

    /** 创建并发布一场考试，指定限考次数 */
    private long publishExam(String title, List<JsonNode> questions, int scoreEach, int maxAttempt)
            throws Exception {
        int total = questions.size() * scoreEach;
        MvcResult c = mockMvc.perform(post("/api/exams")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","totalScore":%d,"passScore":%d,
                                 "startTime":"%s","endTime":"%s","duration":30,
                                 "shuffleQuestion":false,"shuffleOption":false,"maxAttempt":%d}"""
                                .formatted(title, total, total / 2, t(-5), t(60), maxAttempt)))
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

    private JsonNode start(long examId) throws Exception {
        return expectOk(mockMvc.perform(post("/api/my/exams/" + examId + "/start")
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andReturn());
    }

    private void submit(long recordId) throws Exception {
        mockMvc.perform(post("/api/my/records/" + recordId + "/submit")
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("限考 2 次：第 1 次交卷后可以再考一次，第 3 次进入返回结束态")
    void twoAttempts() throws Exception {
        long examId = publishExam(uniqueName("补考"), pickQuestions(2), 10, 2);

        // 第 1 次
        JsonNode p1 = start(examId);
        long r1 = p1.get("recordId").asLong();
        assertThat(p1.get("attemptNo").asInt()).isEqualTo(1);
        submit(r1);

        // 第 2 次：应新建记录，attemptNo=2，而不是恢复第 1 次
        JsonNode p2 = start(examId);
        long r2 = p2.get("recordId").asLong();
        assertThat(p2.get("attemptNo").asInt()).isEqualTo(2);
        assertThat(r2).isNotEqualTo(r1);
        assertThat(p2.get("status").asString()).isEqualTo("IN_PROGRESS");
        submit(r2);

        // 第 3 次：次数用尽，返回最近一次的结束态
        JsonNode p3 = start(examId);
        assertThat(p3.get("status").asString()).isEqualTo("SUBMITTED");
        assertThat(p3.get("attemptNo").asInt()).isEqualTo(2);
        assertThat(p3.get("questions").size()).isZero();
        assertThat(p3.get("recordId").asLong()).isEqualTo(r2);
    }

    @Test
    @DisplayName("有进行中的作答时优先恢复，不消耗新的一次机会")
    void inProgressIsResumed() throws Exception {
        long examId = publishExam(uniqueName("续考优先"), pickQuestions(1), 10, 3);

        JsonNode first = start(examId);
        long recordId = first.get("recordId").asLong();
        assertThat(first.get("attemptNo").asInt()).isEqualTo(1);

        // 反复进入（模拟刷新），必须始终是同一条记录
        for (int i = 0; i < 3; i++) {
            JsonNode again = start(examId);
            assertThat(again.get("recordId").asLong()).isEqualTo(recordId);
            assertThat(again.get("attemptNo").asInt()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("我的考试：返回已用次数、限考次数与最近记录 id")
    void myExamsShowsAttemptInfo() throws Exception {
        long examId = publishExam(uniqueName("次数展示"), pickQuestions(1), 10, 2);

        JsonNode paper = start(examId);
        submit(paper.get("recordId").asLong());

        MvcResult r = mockMvc.perform(get("/api/my/exams")
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode found = null;
        for (JsonNode e : expectOk(r)) {
            if (e.get("id").asLong() == examId) {
                found = e;
                break;
            }
        }
        assertThat(found).isNotNull();
        assertThat(found.get("usedAttempts").asInt()).isEqualTo(1);
        assertThat(found.get("maxAttempt").asInt()).isEqualTo(2);
        assertThat(found.get("canEnter").asBoolean()).isTrue();
        assertThat(found.get("inProgress").asBoolean()).isFalse();
        assertThat(found.get("lastRecordId").asLong()).isEqualTo(paper.get("recordId").asLong());
    }

    @Test
    @DisplayName("限考 1 次：交卷后 canEnter=false，标记次数已用完")
    void singleAttemptExhausted() throws Exception {
        long examId = publishExam(uniqueName("限考一次"), pickQuestions(1), 10, 1);
        JsonNode paper = start(examId);
        submit(paper.get("recordId").asLong());

        JsonNode found = null;
        for (JsonNode e : expectOk(mockMvc.perform(get("/api/my/exams")
                        .header("Authorization", bearer(studentToken)))
                .andReturn())) {
            if (e.get("id").asLong() == examId) {
                found = e;
                break;
            }
        }
        assertThat(found).isNotNull();
        assertThat(found.get("usedAttempts").asInt()).isEqualTo(1);
        assertThat(found.get("maxAttempt").asInt()).isEqualTo(1);
        assertThat(found.get("canEnter").asBoolean()).isFalse();
        // 仍可查看成绩
        assertThat(found.get("lastRecordId").asLong()).isEqualTo(paper.get("recordId").asLong());
    }

    @Test
    @DisplayName("多轮成绩各自独立：两次交卷产生两条记录，分数互不覆盖")
    void attemptsAreIndependent() throws Exception {
        List<JsonNode> questions = pickQuestions(2);
        long examId = publishExam(uniqueName("独立成绩"), questions, 10, 2);

        // 第 1 次全空交卷 → 0 分
        JsonNode p1 = start(examId);
        long r1 = p1.get("recordId").asLong();
        submit(r1);

        // 第 2 次全对 → 满分
        JsonNode p2 = start(examId);
        long r2 = p2.get("recordId").asLong();
        StringBuilder sb = new StringBuilder();
        for (JsonNode q : p2.get("questions")) {
            JsonNode bank = null;
            for (JsonNode b : questions) {
                if (b.get("id").asLong() == q.get("questionId").asLong()) {
                    bank = b;
                    break;
                }
            }
            List<String> answer = new ArrayList<>();
            if (bank != null) {
                bank.get("answer").forEach(a -> answer.add(a.asString()));
            }
            if (!sb.isEmpty()) sb.append(",");
            sb.append("{\"questionId\":%d,\"answer\":%s}"
                    .formatted(q.get("questionId").asLong(), toJsonArray(answer)));
        }
        mockMvc.perform(put("/api/my/records/" + r2 + "/answers")
                        .header("Authorization", bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":[" + sb + "]}"))
                .andExpect(status().isOk());
        submit(r2);

        JsonNode first = expectOk(mockMvc.perform(get("/api/my/records/" + r1 + "/result")
                        .header("Authorization", bearer(studentToken)))
                .andReturn());
        JsonNode second = expectOk(mockMvc.perform(get("/api/my/records/" + r2 + "/result")
                        .header("Authorization", bearer(studentToken)))
                .andReturn());

        assertThat(first.get("objectiveScore").asInt()).isZero();
        assertThat(first.get("attemptNo").asInt()).isEqualTo(1);
        assertThat(second.get("objectiveScore").asInt()).isEqualTo(20);
        assertThat(second.get("attemptNo").asInt()).isEqualTo(2);
        // 第 1 次的成绩不应被第 2 次覆盖
        assertThat(first.get("objectiveScore").asInt()).isZero();
    }

    private String toJsonArray(List<String> values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(values.get(i).replace("\"", "\\\"")).append("\"");
        }
        return sb.append("]").toString();
    }

    private String uniqueName(String prefix) {
        return prefix + System.currentTimeMillis() % 100000 + "_" + (int) (Math.random() * 1000);
    }
}
