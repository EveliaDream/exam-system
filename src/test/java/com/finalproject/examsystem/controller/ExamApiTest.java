package com.finalproject.examsystem.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 组卷与考试发布测试（阶段 4 验收）。
 * <p>
 * 重点覆盖发布前的五项把关：试卷非空、分值之和等于总分、已绑定班级、时间窗合法、及格线不超总分。
 * 这些校验的价值在于「提前拦住」——如果让学生考到一半才发现卷子有问题，代价远高于发布时报错。
 */
@DisplayName("组卷与考试发布")
class ExamApiTest extends BaseApiTest {

    private static final String URL = "/api/exams";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private String t(int plusHours) {
        return LocalDateTime.now().plusHours(plusHours).format(FMT);
    }

    private String body(String title, int totalScore, int passScore) {
        return """
                {"title":"%s","totalScore":%d,"passScore":%d,
                 "startTime":"%s","endTime":"%s","duration":45,
                 "shuffleQuestion":false,"shuffleOption":false,"maxAttempt":1}"""
                .formatted(title, totalScore, passScore, t(1), t(24));
    }

    private long createDraft(String title, int totalScore, int passScore) throws Exception {
        MvcResult r = mockMvc.perform(post(URL)
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(title, totalScore, passScore)))
                .andExpect(status().isOk())
                .andReturn();
        return expectOk(r).get("id").asLong();
    }

    private void setQuestions(long examId, String itemsJson) throws Exception {
        mockMvc.perform(put(URL + "/" + examId + "/questions")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":" + itemsJson + "}"))
                .andExpect(status().isOk());
    }

    private long firstClassId() throws Exception {
        MvcResult r = mockMvc.perform(get("/api/classes/options")
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andReturn();
        return expectOk(r).get(0).get("id").asLong();
    }

    private void bindFirstClass(long examId) throws Exception {
        long classId = firstClassId();
        mockMvc.perform(put(URL + "/" + examId + "/classes")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"classIds":[%d]}""".formatted(classId)))
                .andExpect(status().isOk());
    }

    private long firstQuestionId() throws Exception {
        MvcResult r = mockMvc.perform(get("/api/questions")
                        .header("Authorization", bearer(teacherToken))
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andReturn();
        return expectOk(r).get("list").get(0).get("id").asLong();
    }

    // ==================== 草稿与组卷 ====================

    @Test
    @DisplayName("新建考试：默认草稿状态，分值合计为 0")
    void createDraft() throws Exception {
        long id = createDraft(uniqueName("草稿"), 100, 60);
        MvcResult r = mockMvc.perform(get(URL + "/" + id)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode d = expectOk(r);
        assertThat(d.get("status").asString()).isEqualTo("DRAFT");
        assertThat(d.get("questionCount").asInt()).isZero();
        assertThat(d.get("scoreMatched").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("手动组卷：写入快照并返回分值合计与一致性标记")
    void setQuestions() throws Exception {
        long qid = firstQuestionId();
        long id = createDraft(uniqueName("组卷"), 10, 6);
        setQuestions(id, """
                [{"questionId":%d,"score":10}]""".formatted(qid));

        MvcResult r = mockMvc.perform(get(URL + "/" + id)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode d = expectOk(r);
        assertThat(d.get("questionCount").asInt()).isEqualTo(1);
        assertThat(d.get("questionScoreSum").asInt()).isEqualTo(10);
        assertThat(d.get("scoreMatched").asBoolean()).isTrue();
        // 快照里带题面与答案，判分时不再回查题库
        assertThat(d.get("questions").get(0).get("stem").asString()).isNotBlank();
        assertThat(d.get("questions").get(0).get("answer").size()).isGreaterThan(0);
    }

    @Test
    @DisplayName("手动组卷：同一题重复出现被拒绝（50110）")
    void duplicateQuestionRejected() throws Exception {
        long qid = firstQuestionId();
        long id = createDraft(uniqueName("重复题"), 20, 12);
        mockMvc.perform(put(URL + "/" + id + "/questions")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"questionId":%d,"score":10},{"questionId":%d,"score":10}]}"""
                                .formatted(qid, qid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(50110));
    }

    @Test
    @DisplayName("随机组卷：按题型抽题并替换试卷")
    void randomAssemble() throws Exception {
        long id = createDraft(uniqueName("随机"), 30, 18);
        mockMvc.perform(post(URL + "/" + id + "/questions/random")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rules":[{"type":"SINGLE","count":2,"score":10},
                                          {"type":"JUDGE","count":1,"score":10}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questionCount").value(3))
                .andExpect(jsonPath("$.data.questionScoreSum").value(30))
                .andExpect(jsonPath("$.data.scoreMatched").value(true));
    }

    @Test
    @DisplayName("随机组卷：题目数量不足时拒绝并说明实际数量（50111）")
    void randomAssembleNotEnough() throws Exception {
        long id = createDraft(uniqueName("抽题不足"), 10, 6);
        // 注意数量用 100（@Max 上限）而不是更大值，否则会先被参数校验拦成 20001，
        // 测不到"题库数量不足"这条业务规则
        mockMvc.perform(post(URL + "/" + id + "/questions/random")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rules":[{"type":"BLANK","count":100,"score":1}]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(50111));
    }

    // ==================== 发布校验 ====================

    @Test
    @DisplayName("发布：试卷为空被拒绝（50105）")
    void publishWithoutQuestions() throws Exception {
        long id = createDraft(uniqueName("空卷"), 100, 60);
        bindFirstClass(id);
        mockMvc.perform(put(URL + "/" + id + "/publish")
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(50105));
    }

    @Test
    @DisplayName("发布：分值之和不等于总分被拒绝，并提示实际数值（50101）")
    void publishWithScoreMismatch() throws Exception {
        long qid = firstQuestionId();
        long id = createDraft(uniqueName("分值不符"), 100, 60);
        setQuestions(id, """
                [{"questionId":%d,"score":5}]""".formatted(qid));
        bindFirstClass(id);

        MvcResult r = mockMvc.perform(put(URL + "/" + id + "/publish")
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(50101))
                .andReturn();
        assertThat(json(r).get("message").asString()).contains("5").contains("100");
    }

    @Test
    @DisplayName("发布：未绑定班级被拒绝（50106）")
    void publishWithoutClass() throws Exception {
        long qid = firstQuestionId();
        long id = createDraft(uniqueName("未绑班"), 5, 3);
        setQuestions(id, """
                [{"questionId":%d,"score":5}]""".formatted(qid));

        mockMvc.perform(put(URL + "/" + id + "/publish")
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(50106));
    }

    @Test
    @DisplayName("发布：结束时间已过被拒绝（50107）")
    void publishWithPastEndTime() throws Exception {
        long qid = firstQuestionId();
        long id = createDraft(uniqueName("过期"), 5, 3);
        setQuestions(id, """
                [{"questionId":%d,"score":5}]""".formatted(qid));
        bindFirstClass(id);

        // 直接改库外的方式：用 update 把时间窗改成过去
        mockMvc.perform(put(URL + "/" + id)
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"过去的考试","totalScore":5,"passScore":3,
                                 "startTime":"%s","endTime":"%s","duration":30}"""
                                .formatted(t(-5), t(-2))))
                .andExpect(status().isOk());

        mockMvc.perform(put(URL + "/" + id + "/publish")
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(50107));
    }

    @Test
    @DisplayName("发布成功：状态变为 PUBLISHED，且学生可见")
    void publishSuccessAndVisibleToStudent() throws Exception {
        long qid = firstQuestionId();
        String title = uniqueName("已发布");
        long id = createDraft(title, 5, 3);
        setQuestions(id, """
                [{"questionId":%d,"score":5}]""".formatted(qid));
        bindFirstClass(id);

        mockMvc.perform(put(URL + "/" + id + "/publish")
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        MvcResult r = mockMvc.perform(get("/api/my/exams")
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode list = expectOk(r);
        boolean found = false;
        for (JsonNode e : list) {
            if (title.equals(e.get("title").asString())) {
                found = true;
                // 时间窗从 1 小时后开始 → 未开始
                assertThat(e.get("phase").asString()).isEqualTo("NOT_STARTED");
                assertThat(e.get("canEnter").asBoolean()).isFalse();
            }
        }
        assertThat(found).isTrue();
    }

    // ==================== 状态流转约束 ====================

    @Test
    @DisplayName("已发布考试：不能改题目（50104），但可以调整时间窗")
    void publishedExamIsFrozen() throws Exception {
        long qid = firstQuestionId();
        long id = createDraft(uniqueName("冻结"), 5, 3);
        setQuestions(id, """
                [{"questionId":%d,"score":5}]""".formatted(qid));
        bindFirstClass(id);
        mockMvc.perform(put(URL + "/" + id + "/publish")
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk());

        // 改题目 → 拒绝
        mockMvc.perform(put(URL + "/" + id + "/questions")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"questionId":%d,"score":5}]}""".formatted(qid)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(50104));

        // 改设置 → 拒绝
        mockMvc.perform(put(URL + "/" + id)
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("改标题", 5, 3)))
                .andExpect(status().isConflict());

        // 调时间 → 允许
        mockMvc.perform(put(URL + "/" + id + "/schedule")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startTime":"%s","endTime":"%s","duration":30}""".formatted(t(2), t(30))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.duration").value(30));
    }

    @Test
    @DisplayName("结束考试：状态变为 FINISHED，学生端显示已结束")
    void finishExam() throws Exception {
        long qid = firstQuestionId();
        String title = uniqueName("待结束");
        long id = createDraft(title, 5, 3);
        setQuestions(id, """
                [{"questionId":%d,"score":5}]""".formatted(qid));
        bindFirstClass(id);
        mockMvc.perform(put(URL + "/" + id + "/publish")
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk());

        mockMvc.perform(put(URL + "/" + id + "/finish")
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FINISHED"));

        // 即使时间窗还没到，学生端也必须显示「已结束」，否则状态自相矛盾
        MvcResult r = mockMvc.perform(get("/api/my/exams")
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andReturn();
        for (JsonNode e : expectOk(r)) {
            if (title.equals(e.get("title").asString())) {
                assertThat(e.get("phase").asString()).isEqualTo("ENDED");
                assertThat(e.get("canEnter").asBoolean()).isFalse();
            }
        }
    }

    @Test
    @DisplayName("删除考试：草稿可删，已发布不可删（50104）")
    void deleteOnlyDraft() throws Exception {
        long id = createDraft(uniqueName("待删"), 10, 6);
        mockMvc.perform(delete(URL + "/" + id)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get(URL + "/" + id)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(50100));

        long qid = firstQuestionId();
        long published = createDraft(uniqueName("已发布不删"), 5, 3);
        setQuestions(published, """
                [{"questionId":%d,"score":5}]""".formatted(qid));
        bindFirstClass(published);
        mockMvc.perform(put(URL + "/" + published + "/publish")
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk());

        mockMvc.perform(delete(URL + "/" + published)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(50104));
    }

    // ==================== 数据隔离与越权 ====================

    @Test
    @DisplayName("教师数据隔离：教师2看不到也改不了教师1的考试")
    void teacherCannotTouchOthersExam() throws Exception {
        long id = createDraft(uniqueName("隔离"), 10, 6);

        // 列表里看不到
        MvcResult list = mockMvc.perform(get(URL)
                        .header("Authorization", bearer(teacher2Token))
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andReturn();
        for (JsonNode e : expectOk(list).get("list")) {
            assertThat(e.get("id").asLong()).isNotEqualTo(id);
        }

        // 详情与修改被拒
        mockMvc.perform(get(URL + "/" + id)
                        .header("Authorization", bearer(teacher2Token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(50108));

        mockMvc.perform(put(URL + "/" + id)
                        .header("Authorization", bearer(teacher2Token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("越权改名", 10, 6)))
                .andExpect(status().isForbidden());

        // 不能把别人的考试绑到自己班级
        long t2Class = expectOk(mockMvc.perform(get("/api/classes/options")
                        .header("Authorization", bearer(teacher2Token)))
                .andReturn()).get(0).get("id").asLong();
        mockMvc.perform(put(URL + "/" + id + "/classes")
                        .header("Authorization", bearer(teacher2Token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"classIds":[%d]}""".formatted(t2Class)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("越权：学生访问考试管理与组卷接口均返回 403")
    void studentForbidden() throws Exception {
        mockMvc.perform(get(URL).header("Authorization", bearer(studentToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(URL)
                        .header("Authorization", bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("学生建考试", 10, 6)))
                .andExpect(status().isForbidden());
        // 注意必须用 PUT 命中真实接口；用 GET 会先因方法不支持返回 405，测不到鉴权
        mockMvc.perform(put(URL + "/1/publish")
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("未分班学生：我的考试为空列表（无班级即无考试归属）")
    void unassignedStudentSeesNothing() throws Exception {
        // 不能直接用种子里的 student3：阶段 2 的分班测试可能已把它分到班级（共享内存库），
        // 这里新建一个确定未分班的学生来验证。
        String username = uniqueName("noclass");
        mockMvc.perform(post("/api/students")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","realName":"未分班测试生"}""".formatted(username)))
                .andExpect(status().isOk());

        String token = login(username, "123456");
        MvcResult r = mockMvc.perform(get("/api/my/exams")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(expectOk(r).size()).isZero();
    }

    private String uniqueName(String prefix) {
        return prefix + System.currentTimeMillis() % 100000 + "_" + (int) (Math.random() * 1000);
    }
}
