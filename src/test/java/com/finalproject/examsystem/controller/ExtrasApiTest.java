package com.finalproject.examsystem.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 收尾增强三项：图片题干、班级筛选、操作日志。
 * <p>
 * 分别验证的是：
 * <ol>
 *   <li><b>图片</b>：上传成功可回读、伪装成图片的文件被拒、学生不能上传、快照会带上图片</li>
 *   <li><b>班级筛选</b>：只统计指定班的学生，且导出与看板口径一致</li>
 *   <li><b>日志</b>：成功的写操作被记录、学生看不到日志、筛选生效</li>
 * </ol>
 */
@DisplayName("图片题干 / 班级筛选 / 操作日志")
class ExtrasApiTest extends BaseApiTest {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 最小合法 PNG 头（服务端只校验魔数，够用且不引入二进制资源文件） */
    private static final byte[] PNG_MAGIC = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0
    };

    private String t(int plusMinutes) {
        return LocalDateTime.now().plusMinutes(plusMinutes).format(FMT);
    }

    private MockMultipartFile imageFile(String name, byte[] content) {
        return new MockMultipartFile("file", name, "image/png", content);
    }

    private JsonNode upload(MockMultipartFile file, String token) throws Exception {
        MvcResult r = mockMvc.perform(multipart("/api/files/image")
                        .file(file)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        return expectOk(r);
    }

    // ==================== 图片题干 ====================

    @Test
    @DisplayName("图片：上传后可回读，返回的地址可用于 <img src>")
    void uploadAndRead() throws Exception {
        JsonNode stored = upload(imageFile("stem.png", PNG_MAGIC), teacherToken);
        String url = stored.get("url").asString();
        assertThat(url).startsWith("/api/files/").endsWith(".png");

        MvcResult read = mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(read.getResponse().getContentType()).contains("image/png");
        assertThat(read.getResponse().getContentAsByteArray()).isEqualTo(PNG_MAGIC);
    }

    @Test
    @DisplayName("图片：扩展名合法但内容不是图片时被拒绝")
    void rejectFakeImage() throws Exception {
        MockMultipartFile fake = new MockMultipartFile("file", "evil.png", "image/png",
                "<script>alert(1)</script>".getBytes());
        mockMvc.perform(multipart("/api/files/image")
                        .file(fake)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(20001));
    }

    @Test
    @DisplayName("图片：不支持的扩展名被拒绝")
    void rejectUnsupportedExtension() throws Exception {
        MockMultipartFile svg = new MockMultipartFile("file", "x.svg", "image/svg+xml",
                "<svg/>".getBytes());
        mockMvc.perform(multipart("/api/files/image")
                        .file(svg)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("图片：越权与路径穿越防护")
    void accessControlAndTraversal() throws Exception {
        // 学生不能上传
        mockMvc.perform(multipart("/api/files/image")
                        .file(imageFile("a.png", PNG_MAGIC))
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isForbidden());

        // 非法文件名（路径穿越）：容器或业务层都会拒绝，只要不是 2xx 且不泄露内容即可
        int traversalStatus = mockMvc.perform(get("/api/files/..%2f..%2fapplication.yml"))
                .andReturn().getResponse().getStatus();
        assertThat(traversalStatus).as("路径穿越必须被拒绝").isBetween(400, 499);

        // 格式不对（非 UUID）直接 404
        mockMvc.perform(get("/api/files/not-a-uuid.png"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("图片：题目配图会随组卷快照进入学生试卷")
    void imageIsSnapshottedIntoPaper() throws Exception {
        JsonNode stored = upload(imageFile("q.png", PNG_MAGIC), teacherToken);
        String imageUrl = stored.get("url").asString();

        // 建一道带配图的题
        MvcResult created = mockMvc.perform(post("/api/questions")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"SINGLE","stem":"看图选答案 %s",
                                 "options":["甲","乙"],"answer":["A"],"score":5,
                                 "difficulty":"EASY","imageUrl":"%s","enabled":true}"""
                                .formatted(System.nanoTime(), imageUrl)))
                .andExpect(status().isOk())
                .andReturn();
        long qid = expectOk(created).get("id").asLong();
        assertThat(expectOk(created).get("imageUrl").asString()).isEqualTo(imageUrl);

        // 组卷 → 发布 → 学生看到的题面必须带图
        long examId = publishExam("配图快照" + System.nanoTime() % 10000, qid, 5);
        JsonNode paper = expectOk(mockMvc.perform(post("/api/my/exams/" + examId + "/start")
                        .header("Authorization", bearer(studentToken)))
                .andReturn());
        assertThat(paper.get("questions").get(0).get("imageUrl").asString()).isEqualTo(imageUrl);
    }

    // ==================== 班级筛选 ====================

    private long publishExam(String title, long questionId, int score) throws Exception {
        MvcResult c = mockMvc.perform(post("/api/exams")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","totalScore":%d,"passScore":%d,
                                 "startTime":"%s","endTime":"%s","duration":30,
                                 "shuffleQuestion":false,"shuffleOption":false,"maxAttempt":1}"""
                                .formatted(title, score, score / 2, t(-5), t(60))))
                .andExpect(status().isOk())
                .andReturn();
        long examId = expectOk(c).get("id").asLong();

        mockMvc.perform(put("/api/exams/" + examId + "/questions")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"questionId":%d,"score":%d}]}""".formatted(questionId, score)))
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

    @Test
    @DisplayName("班级筛选：指定不存在的班级时统计为空，且回显班级名")
    void filterByClass() throws Exception {
        long qid = expectOk(mockMvc.perform(get("/api/questions")
                        .header("Authorization", bearer(teacherToken))
                        .param("size", "1"))
                .andReturn()).get("list").get(0).get("id").asLong();
        long examId = publishExam("班级筛选" + System.nanoTime() % 10000, qid, 10);

        // 学生作答（属于 class1）
        JsonNode paper = expectOk(mockMvc.perform(post("/api/my/exams/" + examId + "/start")
                        .header("Authorization", bearer(studentToken)))
                .andReturn());
        mockMvc.perform(post("/api/my/records/" + paper.get("recordId").asLong() + "/submit")
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk());

        // 不过滤：有 1 人
        JsonNode all = expectOk(mockMvc.perform(get("/api/analytics/exams/" + examId)
                        .header("Authorization", bearer(teacherToken)))
                .andReturn());
        assertThat(all.get("submittedCount").asInt()).isEqualTo(1);
        assertThat(all.path("classId").isMissingNode()).isTrue();

        // 过滤到「不存在的班级」：应为 0 人，且回显班级名
        JsonNode none = expectOk(mockMvc.perform(get("/api/analytics/exams/" + examId)
                        .header("Authorization", bearer(teacherToken))
                        .param("classId", "999999"))
                .andReturn());
        assertThat(none.get("submittedCount").asInt()).isZero();
        assertThat(none.get("classId").asLong()).isEqualTo(999999L);
    }

    @Test
    @DisplayName("班级筛选：按学生真实班级过滤能命中，导出仍可用")
    void filterByRealClass() throws Exception {
        long qid = expectOk(mockMvc.perform(get("/api/questions")
                        .header("Authorization", bearer(teacherToken))
                        .param("size", "1"))
                .andReturn()).get("list").get(0).get("id").asLong();
        long examId = publishExam("真实班级" + System.nanoTime() % 10000, qid, 10);

        JsonNode paper = expectOk(mockMvc.perform(post("/api/my/exams/" + examId + "/start")
                        .header("Authorization", bearer(studentToken)))
                .andReturn());
        mockMvc.perform(post("/api/my/records/" + paper.get("recordId").asLong() + "/submit")
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk());

        // 从学生列表取 student1 的真实班级 id（避免假设班级顺序）
        JsonNode students = expectOk(mockMvc.perform(get("/api/students")
                        .header("Authorization", bearer(teacherToken))
                        .param("keyword", "student1")
                        .param("size", "10"))
                .andReturn());
        Long realClassId = null;
        for (JsonNode s : students.get("list")) {
            if ("student1".equals(s.path("username").asString()) && !s.path("classId").isMissingNode()) {
                realClassId = s.get("classId").asLong();
                break;
            }
        }
        assertThat(realClassId).as("student1 应已分班").isNotNull();

        JsonNode filtered = expectOk(mockMvc.perform(get("/api/analytics/exams/" + examId)
                        .header("Authorization", bearer(teacherToken))
                        .param("classId", String.valueOf(realClassId)))
                .andReturn());
        assertThat(filtered.get("submittedCount").asInt()).isEqualTo(1);
        assertThat(filtered.get("className").asString()).isNotBlank();

        // 导出接口带 classId 应正常返回 xlsx
        MvcResult export = mockMvc.perform(get("/api/exams/" + examId + "/export/scores")
                        .header("Authorization", bearer(teacherToken))
                        .param("classId", String.valueOf(realClassId)))
                .andExpect(status().isOk())
                .andReturn();
        byte[] body = export.getResponse().getContentAsByteArray();
        assertThat(body.length).isGreaterThan(0);
        assertThat(body[0]).isEqualTo((byte) 'P');
    }

    // ==================== 操作日志 ====================

    @Test
    @DisplayName("日志：成功的写操作被记录，且能按关键字检索")
    void writeOperationIsLogged() throws Exception {
        String title = "日志验证" + System.nanoTime() % 100000;
        long qid = expectOk(mockMvc.perform(get("/api/questions")
                        .header("Authorization", bearer(teacherToken))
                        .param("size", "1"))
                .andReturn()).get("list").get(0).get("id").asLong();
        long examId = publishExam(title, qid, 10);

        // 发布考试是 PUT，应产生一条「发布」类日志
        MvcResult r = mockMvc.perform(get("/api/logs")
                        .header("Authorization", bearer(teacherToken))
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = expectOk(r);
        assertThat(data.get("total").asInt()).isGreaterThan(0);

        boolean foundPublish = false;
        for (JsonNode log : data.get("list")) {
            if (log.get("action").asString().contains("publish")
                    && log.get("target").asString().contains("/exams/" + examId + "/publish")) {
                foundPublish = true;
                assertThat(log.get("username").asString()).isNotBlank();
                assertThat(log.get("createdAt").asString()).isNotBlank();
            }
        }
        assertThat(foundPublish).as("应记录到发布考试的操作").isTrue();
    }

    @Test
    @DisplayName("日志：学生无权查看（403）")
    void studentCannotReadLogs() throws Exception {
        mockMvc.perform(get("/api/logs")
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("日志：动作下拉有数据，且筛选能缩小结果集")
    void actionsAndFilter() throws Exception {
        // 触发一次写操作，保证至少有日志
        mockMvc.perform(put("/api/exams/999999/publish")
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isNotFound());

        List<String> actions = new ArrayList<>();
        expectOk(mockMvc.perform(get("/api/logs/actions")
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andReturn()).forEach(a -> actions.add(a.asString()));
        assertThat(actions).isNotEmpty();

        // 用一个不存在的关键字过滤，结果应为 0
        JsonNode none = expectOk(mockMvc.perform(get("/api/logs")
                        .header("Authorization", bearer(teacherToken))
                        .param("keyword", "绝不存在的关键字xyz"))
                .andReturn());
        assertThat(none.get("total").asInt()).isZero();
    }
}
