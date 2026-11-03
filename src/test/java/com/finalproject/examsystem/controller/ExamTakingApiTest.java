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
 * 在线作答与自动判分测试（阶段 5 验收）。
 * <p>
 * 覆盖三件最要紧的事：
 * <ol>
 *   <li><b>不泄题</b>：学生端试卷 DTO 里绝不能出现 answer / analysis</li>
 *   <li><b>判分正确</b>：全对拿满分、全错得 0 分、空白不得分</li>
 *   <li><b>记录只属于本人</b>：拿别人的 recordId 访问一律 404；教师不能调学生接口</li>
 * </ol>
 * 以及断点续考（重进能看到已保存答案）与交卷幂等。
 */
@DisplayName("在线作答与判分")
class ExamTakingApiTest extends BaseApiTest {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private String t(int plusMinutes) {
        return LocalDateTime.now().plusMinutes(plusMinutes).format(FMT);
    }

    /** 取题库前 n 道题（含正确答案），用于构造可预期的试卷 */
    private List<JsonNode> pickQuestions(int n) throws Exception {
        MvcResult r = mockMvc.perform(get("/api/questions")
                        .header("Authorization", bearer(teacherToken))
                        .param("page", "1")
                        .param("size", String.valueOf(n)))
                .andExpect(status().isOk())
                .andReturn();
        List<JsonNode> list = new ArrayList<>();
        for (JsonNode q : expectOk(r).get("list")) {
            list.add(q);
        }
        assertThat(list.size()).isGreaterThanOrEqualTo(n);
        return list;
    }

    /** 用给定题目创建一场「现在正在进行」的考试并发布 */
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

    /** 学生开始考试，返回试卷 JSON */
    private JsonNode startExam(long examId) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/my/exams/" + examId + "/start")
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andReturn();
        return expectOk(r);
    }

    private String answersPayload(JsonNode paper, List<JsonNode> bankQuestions, boolean correct) {
        StringBuilder sb = new StringBuilder();
        for (JsonNode q : paper.get("questions")) {
            long qid = q.get("questionId").asLong();
            JsonNode bank = null;
            for (JsonNode b : bankQuestions) {
                if (b.get("id").asLong() == qid) {
                    bank = b;
                    break;
                }
            }
            List<String> answer;
            if (bank == null) {
                answer = List.of();
            } else if (correct) {
                List<String> correctAns = new ArrayList<>();
                bank.get("answer").forEach(a -> correctAns.add(a.asString()));
                answer = correctAns;
            } else {
                // 故意答错：用与正确答案不同的内容
                String wrong = "SINGLE".equals(bank.get("type").asString()) ? "Z" : "WRONG";
                answer = "MULTIPLE".equals(bank.get("type").asString()) ? List.of("Z") : List.of(wrong);
            }
            if (!sb.isEmpty()) sb.append(",");
            sb.append("{\"questionId\":%d,\"answer\":%s,\"timeSpentSec\":12,\"submitCount\":2}"
                    .formatted(qid, toJsonArray(answer)));
        }
        return "{\"answers\":[" + sb + "]}";
    }

    private String toJsonArray(List<String> values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(values.get(i).replace("\"", "\\\"")).append("\"");
        }
        return sb.append("]").toString();
    }

    // ==================== 不泄题 ====================

    @Test
    @DisplayName("试卷 DTO 不含正确答案与解析（学生端无法通过 F12 看到答案）")
    void paperMustNotLeakAnswers() throws Exception {
        List<JsonNode> questions = pickQuestions(2);
        long examId = publishExam(uniqueName("不泄题"), questions, 5);

        JsonNode paper = startExam(examId);
        assertThat(paper.get("questions").size()).isEqualTo(2);
        for (JsonNode q : paper.get("questions")) {
            assertThat(q.has("answer")).as("题目不应包含 answer 字段").isFalse();
            assertThat(q.has("analysis")).as("题目不应包含 analysis 字段").isFalse();
            assertThat(q.get("stem").asString()).isNotBlank();
        }
    }

    @Test
    @DisplayName("试卷下发服务端截止时间与剩余秒数")
    void paperHasServerDeadline() throws Exception {
        long examId = publishExam(uniqueName("截止时间"), pickQuestions(1), 5);
        JsonNode paper = startExam(examId);
        assertThat(paper.get("deadline").asString()).isNotBlank();
        assertThat(paper.get("remainingSeconds").asLong()).isGreaterThan(0)
                .isLessThanOrEqualTo(30 * 60L + 5);
    }

    // ==================== 判分 ====================

    @Test
    @DisplayName("全对：得分等于试卷总分，答对题数等于题数")
    void answerAllCorrect() throws Exception {
        List<JsonNode> questions = pickQuestions(3);
        long examId = publishExam(uniqueName("全对"), questions, 10);

        JsonNode paper = startExam(examId);
        long recordId = paper.get("recordId").asLong();
        assertThat(paper.get("status").asString()).isEqualTo("IN_PROGRESS");

        mockMvc.perform(put("/api/my/records/" + recordId + "/answers")
                        .header("Authorization", bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answersPayload(paper, questions, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submitted").value(false));

        MvcResult r = mockMvc.perform(post("/api/my/records/" + recordId + "/submit")
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode result = expectOk(r);
        assertThat(result.get("objectiveScore").asInt()).isEqualTo(30);
        assertThat(result.get("correctCount").asInt()).isEqualTo(3);
        assertThat(result.get("questionCount").asInt()).isEqualTo(3);
        assertThat(result.get("passed").asBoolean()).isTrue();
        assertThat(result.get("autoSubmitted").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("全错：得 0 分且答对题数为 0")
    void answerAllWrong() throws Exception {
        List<JsonNode> questions = pickQuestions(2);
        long examId = publishExam(uniqueName("全错"), questions, 10);

        JsonNode paper = startExam(examId);
        long recordId = paper.get("recordId").asLong();

        mockMvc.perform(put("/api/my/records/" + recordId + "/answers")
                        .header("Authorization", bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answersPayload(paper, questions, false)))
                .andExpect(status().isOk());

        MvcResult r = mockMvc.perform(post("/api/my/records/" + recordId + "/submit")
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode result = expectOk(r);
        assertThat(result.get("objectiveScore").asInt()).isZero();
        assertThat(result.get("correctCount").asInt()).isZero();
        assertThat(result.get("passed").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("未作答直接交卷：得 0 分（空答案不得分）")
    void submitWithoutAnswering() throws Exception {
        long examId = publishExam(uniqueName("未作答"), pickQuestions(2), 10);

        JsonNode paper = startExam(examId);
        long recordId = paper.get("recordId").asLong();

        MvcResult r = mockMvc.perform(post("/api/my/records/" + recordId + "/submit")
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode result = expectOk(r);
        assertThat(result.get("objectiveScore").asInt()).isZero();
        assertThat(result.get("correctCount").asInt()).isZero();
    }

    @Test
    @DisplayName("交卷幂等：重复提交返回同一结果，不重复计分")
    void submitIsIdempotent() throws Exception {
        List<JsonNode> questions = pickQuestions(2);
        long examId = publishExam(uniqueName("幂等"), questions, 10);
        JsonNode paper = startExam(examId);
        long recordId = paper.get("recordId").asLong();

        mockMvc.perform(put("/api/my/records/" + recordId + "/answers")
                        .header("Authorization", bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answersPayload(paper, questions, true)))
                .andExpect(status().isOk());

        JsonNode first = expectOk(mockMvc.perform(post("/api/my/records/" + recordId + "/submit")
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andReturn());
        JsonNode second = expectOk(mockMvc.perform(post("/api/my/records/" + recordId + "/submit")
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(second.get("objectiveScore").asInt()).isEqualTo(first.get("objectiveScore").asInt());
        assertThat(second.get("submitTime").asString()).isEqualTo(first.get("submitTime").asString());
    }

    // ==================== 断点续考 ====================

    @Test
    @DisplayName("断点续考：重新进入能看到已保存答案，且不新建记录")
    void resumeKeepsAnswersAndRecord() throws Exception {
        List<JsonNode> questions = pickQuestions(2);
        long examId = publishExam(uniqueName("续考"), questions, 5);

        JsonNode first = startExam(examId);
        long recordId = first.get("recordId").asLong();

        mockMvc.perform(put("/api/my/records/" + recordId + "/answers")
                        .header("Authorization", bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answersPayload(first, questions, true)))
                .andExpect(status().isOk());

        // 模拟刷新页面：重新 start
        JsonNode second = startExam(examId);
        assertThat(second.get("recordId").asLong()).as("重复进入不应新建记录").isEqualTo(recordId);
        assertThat(second.get("savedAnswers").size()).isEqualTo(2);
    }

    // ==================== 权限与归属 ====================

    @Test
    @DisplayName("记录归属：拿别人的 recordId 访问返回 404")
    void cannotAccessOthersRecord() throws Exception {
        long examId = publishExam(uniqueName("归属"), pickQuestions(1), 5);
        JsonNode paper = startExam(examId);
        long recordId = paper.get("recordId").asLong();

        String otherStudent = login("student2", "123456");
        mockMvc.perform(get("/api/my/records/" + recordId + "/result")
                        .header("Authorization", bearer(otherStudent)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(60100));

        mockMvc.perform(post("/api/my/records/" + recordId + "/submit")
                        .header("Authorization", bearer(otherStudent)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("越权：教师不能调用学生作答接口（403）")
    void teacherCannotUseStudentEndpoints() throws Exception {
        mockMvc.perform(post("/api/my/exams/1/start")
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/my/records")
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("已交卷后再进入考试：返回结束状态的试卷壳（前端据此跳成绩页）")
    void startAfterSubmitReturnsFinishedShell() throws Exception {
        List<JsonNode> questions = pickQuestions(1);
        long examId = publishExam(uniqueName("已交卷"), questions, 5);
        JsonNode paper = startExam(examId);
        long recordId = paper.get("recordId").asLong();
        mockMvc.perform(post("/api/my/records/" + recordId + "/submit")
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk());

        JsonNode again = startExam(examId);
        assertThat(again.get("status").asString()).isEqualTo("SUBMITTED");
        assertThat(again.get("questions").size()).isZero();
        assertThat(again.get("remainingSeconds").asLong()).isZero();
    }

    @Test
    @DisplayName("我的成绩：交卷后出现在列表中，含得分与是否通过")
    void myRecordsAfterSubmit() throws Exception {
        List<JsonNode> questions = pickQuestions(1);
        String title = uniqueName("成绩列表");
        long examId = publishExam(title, questions, 10);
        JsonNode paper = startExam(examId);
        long recordId = paper.get("recordId").asLong();

        mockMvc.perform(put("/api/my/records/" + recordId + "/answers")
                        .header("Authorization", bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answersPayload(paper, questions, true)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/my/records/" + recordId + "/submit")
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk());

        MvcResult r = mockMvc.perform(get("/api/my/records")
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andReturn();
        boolean found = false;
        for (JsonNode rec : expectOk(r)) {
            if (title.equals(rec.get("examTitle").asString())) {
                found = true;
                assertThat(rec.get("objectiveScore").asInt()).isEqualTo(10);
                assertThat(rec.get("passed").asBoolean()).isTrue();
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    @DisplayName("越权：未分班学生不能进入考试")
    void unassignedStudentCannotStart() throws Exception {
        long examId = publishExam(uniqueName("未分班"), pickQuestions(1), 5);

        String username = uniqueName("noclass");
        mockMvc.perform(post("/api/students")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","realName":"未分班学生"}""".formatted(username)))
                .andExpect(status().isOk());

        String token = login(username, "123456");
        mockMvc.perform(post("/api/my/exams/" + examId + "/start")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());
    }

    private String uniqueName(String prefix) {
        return prefix + System.currentTimeMillis() % 100000 + "_" + (int) (Math.random() * 1000);
    }
}
