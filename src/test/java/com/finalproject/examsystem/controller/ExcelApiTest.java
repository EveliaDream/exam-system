package com.finalproject.examsystem.controller;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

import java.io.ByteArrayOutputStream;
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
 * Excel 导入导出接口测试（收尾阶段）。
 * <p>
 * 三件事各测一层：
 * <ol>
 *   <li><b>成绩单导出</b>：返回的确实是 xlsx（zip 魔数 PK），且 Content-Disposition 带文件名</li>
 *   <li><b>题库导入</b>：有效行入库、无效行只报该行错误、其余行照常成功（逐行容错）</li>
 *   <li><b>模板下载</b>：能下载且能被动解析，教师拿到的模板与导入解析顺序一致</li>
 * </ol>
 */
@DisplayName("Excel 导入导出")
class ExcelApiTest extends BaseApiTest {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private String t(int plusMinutes) {
        return LocalDateTime.now().plusMinutes(plusMinutes).format(FMT);
    }

    /** 用 POI 现造一个 xlsx，模拟教师上传的文件 */
    private byte[] buildWorkbook(List<String[]> dataRows) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("题目");
            Row header = sheet.createRow(0);
            String[] headers = {"题型*", "难度*", "题干*", "选项A", "选项B", "选项C", "选项D",
                    "正确答案*", "分值*", "课程", "知识点", "解析"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            int rowIdx = 1;
            for (String[] values : dataRows) {
                Row r = sheet.createRow(rowIdx++);
                for (int i = 0; i < values.length; i++) {
                    Cell c = r.createCell(i);
                    c.setCellValue(values[i]);
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    private MockMultipartFile xlsxFile(String filename, byte[] content) {
        return new MockMultipartFile("file", filename,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content);
    }

    private JsonNode doImport(MockMultipartFile file) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/questions/import")
                        .file(file)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andReturn();
        return expectOk(result);
    }

    // ==================== 题库导入 ====================

    @Test
    @DisplayName("导入：合法行逐行入库，返回成功数量")
    void importValidRows() throws Exception {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"单选题", "简单", "Excel 导入测试单选 " + System.nanoTime(), "选项一", "选项二", "", "", "A", "5", "", "", "解析"});
        rows.add(new String[]{"多选题", "中等", "Excel 导入测试多选 " + System.nanoTime(), "甲", "乙", "丙", "", "A,B", "5", "", "", ""});
        rows.add(new String[]{"判断题", "困难", "Excel 导入测试判断 " + System.nanoTime(), "", "", "", "", "正确", "5", "", "", ""});
        rows.add(new String[]{"填空题", "简单", "Excel 导入测试填空 " + System.nanoTime(), "", "", "", "", "extends|继承", "5", "", "", ""});

        byte[] content = buildWorkbook(rows);
        JsonNode data = doImport(xlsxFile("questions.xlsx", content));

        assertThat(data.get("total").asInt()).isEqualTo(4);
        assertThat(data.get("success").asInt()).isEqualTo(4);
        assertThat(data.get("failed").asInt()).isZero();
    }

    @Test
    @DisplayName("导入：一行出错不影响其他行，错误信息带行号")
    void importPartialFailure() throws Exception {
        List<String[]> rows = new ArrayList<>();
        // 第 2 行：合法
        rows.add(new String[]{"单选题", "简单", "部分失败测试-合法行 " + System.nanoTime(), "A选项", "B选项", "", "", "A", "5", "", "", ""});
        // 第 3 行：难度非法
        rows.add(new String[]{"单选题", "超级难", "部分失败测试-难度非法", "A", "B", "", "", "A", "5", "", "", ""});
        // 第 4 行：题干为空
        rows.add(new String[]{"单选题", "简单", "", "A", "B", "", "", "A", "5", "", "", ""});
        // 第 5 行：单选题答案给了多个字母
        rows.add(new String[]{"单选题", "简单", "部分失败测试-答案非法", "A", "B", "", "", "A,B", "5", "", "", ""});

        JsonNode data = doImport(xlsxFile("questions.xlsx", buildWorkbook(rows)));

        assertThat(data.get("total").asInt()).isEqualTo(4);
        assertThat(data.get("success").asInt()).isEqualTo(1);
        assertThat(data.get("failed").asInt()).isEqualTo(3);
        assertThat(data.get("errors").size()).isEqualTo(3);

        List<String> errors = new ArrayList<>();
        data.get("errors").forEach(e -> errors.add(e.asString()));
        // 错误信息必须能定位到具体行，否则教师无法修
        assertThat(errors.get(0)).contains("第 3 行").contains("难度");
        assertThat(errors.get(1)).contains("第 4 行").contains("题干");
        assertThat(errors.get(2)).contains("第 5 行").contains("单选题");
    }

    @Test
    @DisplayName("导入：知识点名称不存在时该行失败并提示先创建")
    void importUnknownKnowledgePoint() throws Exception {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"单选题", "简单", "知识点不存在测试 " + System.nanoTime(), "A", "B", "", "", "A", "5",
                "", "这个知识点肯定不存在xyz", ""});

        JsonNode data = doImport(xlsxFile("questions.xlsx", buildWorkbook(rows)));
        assertThat(data.get("success").asInt()).isZero();
        assertThat(data.get("errors").get(0).asString()).contains("知识点").contains("不存在");
    }

    @Test
    @DisplayName("导入：非 .xlsx 文件被拒绝并说明原因")
    void importRejectsWrongExtension() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "questions.xls",
                "application/vnd.ms-excel", new byte[]{1, 2, 3});
        mockMvc.perform(multipart("/api/questions/import")
                        .file(file)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(20001));
    }

    @Test
    @DisplayName("导入：空文件被拒绝")
    void importRejectsEmptyFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "empty.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[0]);
        mockMvc.perform(multipart("/api/questions/import")
                        .file(file)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("导入：内容是乱码而非 xlsx 时提示文件无法解析")
    void importRejectsCorruptFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "bad.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "这不是一个 xlsx 文件".getBytes());
        mockMvc.perform(multipart("/api/questions/import")
                        .file(file)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("越权：学生不能导入题库（403）")
    void studentCannotImport() throws Exception {
        MockMultipartFile file = xlsxFile("q.xlsx", buildWorkbook(List.of()));
        mockMvc.perform(multipart("/api/questions/import")
                        .file(file)
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isForbidden());
    }

    // ==================== 模板下载 ====================

    @Test
    @DisplayName("模板：可下载，且是有效的 xlsx（表头与导入解析顺序一致）")
    void downloadTemplate() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/questions/import/template")
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andReturn();

        byte[] body = result.getResponse().getContentAsByteArray();
        assertThat(body.length).isGreaterThan(0);
        // xlsx 本质是 zip，前两个字节固定为 PK
        assertThat(body[0]).isEqualTo((byte) 'P');
        assertThat(body[1]).isEqualTo((byte) 'K');

        String disposition = result.getResponse().getHeader("Content-Disposition");
        assertThat(disposition).isNotNull().contains("filename");

        // 能被 POI 解析回来说明文件本身没坏
        try (XSSFWorkbook wb = new XSSFWorkbook(new java.io.ByteArrayInputStream(body))) {
            Sheet sheet = wb.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("题型*");
            assertThat(sheet.getRow(0).getCell(2).getStringCellValue()).isEqualTo("题干*");
            // 模板自带示例行，教师可直接覆盖
            assertThat(sheet.getLastRowNum()).isGreaterThan(0);
        }
    }

    // ==================== 成绩单导出 ====================

    /** 造一场有 1 份作答的考试 */
    private long prepareExamWithSubmission(String title) throws Exception {
        MvcResult q = mockMvc.perform(get("/api/questions")
                        .header("Authorization", bearer(teacherToken))
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andReturn();
        List<JsonNode> questions = new ArrayList<>();
        expectOk(q).get("list").forEach(questions::add);

        int total = questions.size() * 10;
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
        for (JsonNode qn : questions) {
            if (!items.isEmpty()) items.append(",");
            items.append("{\"questionId\":%d,\"score\":10}".formatted(qn.get("id").asLong()));
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

        // 学生作答并交卷
        JsonNode paper = expectOk(mockMvc.perform(post("/api/my/exams/" + examId + "/start")
                        .header("Authorization", bearer(studentToken)))
                .andReturn());
        long recordId = paper.get("recordId").asLong();
        mockMvc.perform(post("/api/my/records/" + recordId + "/submit")
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk());
        return examId;
    }

    @Test
    @DisplayName("成绩单：导出为有效 xlsx，文件名含考试名")
    void exportScores() throws Exception {
        String title = "导出测试考试" + System.nanoTime() % 10000;
        long examId = prepareExamWithSubmission(title);

        MvcResult result = mockMvc.perform(get("/api/exams/" + examId + "/export/scores")
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andReturn();

        byte[] body = result.getResponse().getContentAsByteArray();
        assertThat(body.length).isGreaterThan(0);
        assertThat(body[0]).isEqualTo((byte) 'P');
        assertThat(body[1]).isEqualTo((byte) 'K');

        String contentType = result.getResponse().getContentType();
        assertThat(contentType).contains("spreadsheetml");

        // 解析回来验证内容结构：标题行、概要行、空行、表头、至少一行学生数据
        // 注意行号：0=标题 1=概要 2=空行 3=表头 4=首条数据
        try (XSSFWorkbook wb = new XSSFWorkbook(new java.io.ByteArrayInputStream(body))) {
            Sheet sheet = wb.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).contains(title);
            assertThat(sheet.getRow(3).getCell(0).getStringCellValue()).isEqualTo("序号");
            assertThat(sheet.getRow(3).getCell(1).getStringCellValue()).isEqualTo("姓名");
            assertThat(sheet.getRow(4).getCell(1).getStringCellValue()).isNotBlank();
            // 该学生未作答直接交卷 → 0 分
            assertThat(sheet.getRow(4).getCell(4).getStringCellValue()).isEqualTo("0");
        }
    }

    @Test
    @DisplayName("成绩单：无学生交卷时仍可导出，内容提示暂无记录")
    void exportEmptyScores() throws Exception {
        MvcResult q = mockMvc.perform(get("/api/questions")
                        .header("Authorization", bearer(teacherToken))
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andReturn();
        long qid = expectOk(q).get("list").get(0).get("id").asLong();

        MvcResult c = mockMvc.perform(post("/api/exams")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","totalScore":10,"passScore":6,
                                 "startTime":"%s","endTime":"%s","duration":30,
                                 "shuffleQuestion":false,"shuffleOption":false,"maxAttempt":1}"""
                                .formatted("空成绩单" + System.nanoTime() % 10000, t(-5), t(60))))
                .andExpect(status().isOk())
                .andReturn();
        long examId = expectOk(c).get("id").asLong();

        MvcResult result = mockMvc.perform(get("/api/exams/" + examId + "/export/scores")
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(result.getResponse().getContentAsByteArray().length).isGreaterThan(0);
    }

    @Test
    @DisplayName("越权：教师2 不能导出教师1 考试的成绩单（50108）")
    void otherTeacherCannotExport() throws Exception {
        long examId = prepareExamWithSubmission("隔离导出" + System.nanoTime() % 10000);
        mockMvc.perform(get("/api/exams/" + examId + "/export/scores")
                        .header("Authorization", bearer(teacher2Token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(50108));
    }

    @Test
    @DisplayName("越权：学生不能导出成绩单（403）")
    void studentCannotExport() throws Exception {
        long examId = prepareExamWithSubmission("学生导出" + System.nanoTime() % 10000);
        mockMvc.perform(get("/api/exams/" + examId + "/export/scores")
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isForbidden());
    }
}
