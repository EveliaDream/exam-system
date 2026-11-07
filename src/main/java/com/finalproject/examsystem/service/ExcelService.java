package com.finalproject.examsystem.service;

import com.finalproject.examsystem.common.BusinessException;
import com.finalproject.examsystem.common.ErrorCode;
import com.finalproject.examsystem.domain.Difficulty;
import com.finalproject.examsystem.domain.QuestionType;
import com.finalproject.examsystem.dto.analytics.ExamAnalyticsResponse;
import com.finalproject.examsystem.dto.question.QuestionSaveRequest;
import com.finalproject.examsystem.dto.user.ImportResult;
import com.finalproject.examsystem.repository.CourseRepository;
import com.finalproject.examsystem.repository.KnowledgePointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Excel 导入导出（成绩单导出、题库批量导入与模板下载）。
 * <p>
 * 三处实现要点：
 * <ol>
 *   <li><b>导入逐行容错</b>：一行出错不影响其余行，返回每行的错误信息，
 *       教师改完再传一次即可——整批回滚会让教师不知道到底哪行错了</li>
 *   <li><b>只接受 .xlsx</b>：POI 解析 .xls 需要额外依赖（poi-scratchpad），
 *       与其模糊支持不如明确拒绝并提示另存为 .xlsx</li>
 *   <li><b>知识点按名称匹配</b>：模板里让教师填知识点名称而不是 id，
 *       名称不存在时该行报错并提示先建知识点，避免静默丢关联</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelService {

    /** 单次导入上限：防止误传超大文件把服务拖住 */
    private static final int MAX_IMPORT_ROWS = 500;

    /** 模板列顺序，导入时按同一顺序解析 */
    private static final String[] QUESTION_HEADERS = {
            "题型*", "难度*", "题干*", "选项A", "选项B", "选项C", "选项D",
            "正确答案*", "分值*", "课程", "知识点", "解析"
    };

    private final AnalyticsService analyticsService;
    private final QuestionService questionService;
    private final CourseRepository courseRepository;
    private final KnowledgePointRepository knowledgePointRepository;

    // ==================== 成绩单导出 ====================

    /**
     * 导出某场考试的成绩单。
     * <p>
     * 数据直接复用 {@link AnalyticsService#analyzeExam}，保证「页面看到的数字」与
     * 「导出的数字」是同一套口径——如果另写一段统计逻辑，两边迟早不一致。
     */
    @Transactional(readOnly = true)
    public ExcelFile exportScores(Long examId, Long classId) {
        ExamAnalyticsResponse data = analyticsService.analyzeExam(examId, classId);

        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("成绩单");
            Styles styles = new Styles(wb);

            int rowIdx = 0;
            // 标题（按班级筛选时带上班级名，避免导出多份后分不清）
            String titleText = data.title() + " 成绩单"
                    + (data.className() == null ? "" : "（" + data.className() + "）");
            Row title = sheet.createRow(rowIdx++);
            title.setHeightInPoints(28);
            Cell titleCell = title.createCell(0);
            titleCell.setCellValue(titleText);
            titleCell.setCellStyle(styles.title);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 9));

            // 概要信息
            Row meta = sheet.createRow(rowIdx++);
            Cell metaCell = meta.createCell(0);
            metaCell.setCellValue(String.format(
                    "总分 %d ｜ 及格线 %d ｜ 已交卷 %d 人 ｜ 平均分 %.1f ｜ 及格率 %.0f%%",
                    data.totalScore() == null ? 0 : data.totalScore(),
                    data.passScore() == null ? 0 : data.passScore(),
                    data.submittedCount(),
                    data.avgScore() == null ? 0 : data.avgScore(),
                    (data.passRate() == null ? 0 : data.passRate()) * 100));
            metaCell.setCellStyle(styles.meta);
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 9));

            // 空一行：显式创建而不是只把行号 +1，避免文件里出现"缺失的行"，
            // 某些表格工具遇到跳行会显示异常
            sheet.createRow(rowIdx++);

            // 表头
            String[] headers = {"序号", "姓名", "账号", "班级", "得分", "得分率", "结果", "交卷方式", "切屏次数", "交卷时间"};
            Row header = sheet.createRow(rowIdx++);
            header.setHeightInPoints(20);
            for (int i = 0; i < headers.length; i++) {
                Cell c = header.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(styles.header);
            }

            // 数据行
            int index = 1;
            for (ExamAnalyticsResponse.StudentRow s : data.students()) {
                Row r = sheet.createRow(rowIdx++);
                setCell(r, 0, String.valueOf(index++), styles.center);
                setCell(r, 1, s.studentName(), styles.center);
                setCell(r, 2, s.username(), styles.center);
                setCell(r, 3, s.className(), styles.center);
                setCell(r, 4, String.valueOf(s.score()), styles.center);
                setCell(r, 5, String.format("%.0f%%", (s.rate() == null ? 0 : s.rate()) * 100), styles.center);
                setCell(r, 6, Boolean.TRUE.equals(s.passed()) ? "通过" : "未通过", styles.center);
                setCell(r, 7, Boolean.TRUE.equals(s.autoSubmitted()) ? "超时自动" : "主动", styles.center);
                setCell(r, 8, String.valueOf(s.switchScreenCount() == null ? 0 : s.switchScreenCount()), styles.center);
                setCell(r, 9, s.submitTime() == null ? "" : s.submitTime(), styles.center);
            }

            if (data.students().isEmpty()) {
                Row r = sheet.createRow(rowIdx);
                Cell c = r.createCell(0);
                c.setCellValue("暂无学生交卷");
                c.setCellStyle(styles.meta);
                sheet.addMergedRegion(new CellRangeAddress(rowIdx, rowIdx, 0, 9));
            }

            // 列宽
            int[] widths = {6, 12, 16, 20, 8, 9, 9, 11, 10, 20};
            for (int i = 0; i < widths.length; i++) {
                sheet.setColumnWidth(i, widths[i] * 256);
            }
            sheet.createFreezePane(0, 4);

            wb.write(out);
            String safeTitle = (data.title() == null ? "考试" : data.title())
                    .replaceAll("[\\\\/:*?\"<>|]", "_");
            String suffix = data.className() == null ? "" : "-" + data.className().replaceAll("[\\\\/:*?\"<>|]", "_");
            return new ExcelFile(out.toByteArray(), safeTitle + suffix + "-成绩单.xlsx");
        } catch (IOException e) {
            log.error("导出成绩单失败, examId={}", examId, e);
            throw BusinessException.of(ErrorCode.SYSTEM_ERROR, "导出失败，请稍后重试");
        }
    }

    // ==================== 题库导入模板 ====================

    /** 生成题库导入模板：含示例行与填写说明，教师照着填即可 */
    public ExcelFile questionTemplate() {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles styles = new Styles(wb);

            Sheet sheet = wb.createSheet("题目");
            Row header = sheet.createRow(0);
            header.setHeightInPoints(20);
            for (int i = 0; i < QUESTION_HEADERS.length; i++) {
                Cell c = header.createCell(i);
                c.setCellValue(QUESTION_HEADERS[i]);
                c.setCellStyle(styles.header);
            }

            // 三种题型的示例行，教师可以直接覆盖
            String[][] samples = {
                    {"单选题", "简单", "Java 中用于继承父类的关键字是？", "extends", "implements", "instanceof", "super",
                            "A", "5", "", "Java 基础", "继承使用 extends 关键字。"},
                    {"多选题", "中等", "以下哪些属于面向对象三大特征？", "封装", "继承", "多态", "重载",
                            "A,B,C", "5", "", "面向对象", "重载是方法层面的概念，不属于三大特征。"},
                    {"判断题", "简单", "软件测试的目的是证明程序没有错误。", "", "", "", "",
                            "错误", "5", "", "软件测试", "测试的目的是发现错误，而非证明无错。"},
                    {"填空题", "中等", "Java 程序的入口方法是 ______。", "", "", "", "",
                            "main|main方法", "5", "", "Java 基础", "多个可接受答案用 | 分隔。"}
            };
            int rowIdx = 1;
            for (String[] sample : samples) {
                Row r = sheet.createRow(rowIdx++);
                for (int i = 0; i < sample.length; i++) {
                    setCell(r, i, sample[i], styles.normal);
                }
            }

            int[] widths = {10, 8, 40, 14, 14, 14, 14, 14, 8, 16, 20, 30};
            for (int i = 0; i < widths.length; i++) {
                sheet.setColumnWidth(i, widths[i] * 256);
            }
            sheet.createFreezePane(0, 1);

            // 说明页
            Sheet tips = wb.createSheet("填写说明");
            String[][] lines = {
                    {"列名", "说明"},
                    {"题型*", "必填。单选题 / 多选题 / 判断题 / 填空题（也可写 SINGLE/MULTIPLE/JUDGE/BLANK）"},
                    {"难度*", "必填。简单 / 中等 / 困难（也可写 EASY/MEDIUM/HARD）"},
                    {"题干*", "必填，最多 2000 字"},
                    {"选项A~D", "单选与多选必填（至少两项）；判断题与填空题留空"},
                    {"正确答案*", "单选：一个选项字母，如 A；多选：多个字母用 , 分隔，如 A,C；判断题：正确/错误（或 T/F）；填空题：多个可接受答案用 | 分隔"},
                    {"分值*", "必填，1~100 的整数"},
                    {"课程", "可选。填课程名称，需已在系统中存在；留空表示不归属课程"},
                    {"知识点", "可选。多个用 , 分隔，必须已存在（先到「知识点管理」创建）"},
                    {"解析", "可选，最多 2000 字"},
                    {"", ""},
                    {"注意", "一行出错不会影响其他行；导入结果会逐行列出失败原因，改完后可重新导入"},
                    {"注意", "单次最多导入 " + MAX_IMPORT_ROWS + " 行"}
            };
            int tipIdx = 0;
            for (String[] line : lines) {
                Row r = tips.createRow(tipIdx);
                CellStyle style = tipIdx == 0 ? styles.header : styles.normal;
                setCell(r, 0, line[0], style);
                setCell(r, 1, line[1], style);
                tipIdx++;
            }
            tips.setColumnWidth(0, 14 * 256);
            tips.setColumnWidth(1, 90 * 256);

            wb.write(out);
            return new ExcelFile(out.toByteArray(), "题库导入模板.xlsx");
        } catch (IOException e) {
            log.error("生成题库模板失败", e);
            throw BusinessException.of(ErrorCode.SYSTEM_ERROR, "生成模板失败");
        }
    }

    // ==================== 题库批量导入 ====================

    /**
     * 批量导入题目。
     * <p>
     * 逐行处理、逐行容错：某一行不合法只记录该行错误，其余行照常入库。
     * 教师拿到「第 5 行：选项至少需要两项」这样的提示，改完重传即可。
     */
    @Transactional
    public ImportResult importQuestions(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "请选择要导入的文件");
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!filename.endsWith(".xlsx")) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID,
                    "仅支持 .xlsx 文件；如果是 .xls，请在 Excel 中另存为 .xlsx 后再上传");
        }

        List<String> errors = new ArrayList<>();
        int total = 0;
        int success = 0;

        try (InputStream in = file.getInputStream();
             Workbook wb = WorkbookFactory.create(in)) {

            Sheet sheet = wb.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            // 行上限保护
            int lastRow = sheet.getLastRowNum();
            if (lastRow > MAX_IMPORT_ROWS) {
                throw BusinessException.of(ErrorCode.PARAM_INVALID,
                        "单次最多导入 " + MAX_IMPORT_ROWS + " 行，当前文件有 " + lastRow + " 行，请分批导入");
            }

            for (int i = 1; i <= lastRow; i++) {
                Row row = sheet.getRow(i);
                if (row == null || isBlankRow(row, formatter)) {
                    continue;
                }
                total++;
                int excelRowNo = i + 1;
                try {
                    QuestionSaveRequest request = parseRow(row, formatter);
                    questionService.create(request);
                    success++;
                } catch (BusinessException e) {
                    errors.add("第 " + excelRowNo + " 行：" + e.getMessage());
                } catch (Exception e) {
                    errors.add("第 " + excelRowNo + " 行：解析失败（" + e.getClass().getSimpleName() + "）");
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            log.error("解析题库导入文件失败", e);
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "文件无法解析，请确认是有效的 .xlsx 文件");
        }

        if (total == 0) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "文件里没有可导入的数据行");
        }
        log.info("题库导入完成：共 {} 行，成功 {} 行，失败 {} 行", total, success, errors.size());
        return ImportResult.of(total, success, errors);
    }

    /** 解析一行 → QuestionSaveRequest；枚举与结构校验交给 QuestionService */
    private QuestionSaveRequest parseRow(Row row, DataFormatter fmt) {
        QuestionType type = parseType(cell(row, 0, fmt));
        Difficulty difficulty = parseDifficulty(cell(row, 1, fmt));
        String stem = cell(row, 2, fmt);
        if (stem.isBlank()) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "题干不能为空");
        }

        List<String> rawOptions = new ArrayList<>();
        for (int col = 3; col <= 6; col++) {
            String v = cell(row, col, fmt);
            if (!v.isBlank()) {
                rawOptions.add(v);
            }
        }

        String answerText = cell(row, 7, fmt);
        if (answerText.isBlank()) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "正确答案不能为空");
        }

        Integer score = parseScore(cell(row, 8, fmt));
        Long courseId = resolveCourse(cell(row, 9, fmt));
        List<Long> kpIds = resolveKnowledge(cell(row, 10, fmt));
        String analysis = cell(row, 11, fmt);

        List<String> options = switch (type) {
            // 判断题的选项是固定的，模板里留空即可
            case JUDGE -> List.of("正确", "错误");
            case BLANK -> List.of();
            default -> rawOptions;
        };

        List<String> answer = parseAnswer(type, answerText, rawOptions);

        return new QuestionSaveRequest(type, stem, options, answer, score, difficulty,
                analysis.isBlank() ? null : analysis, null, courseId, kpIds, true);
    }

    private QuestionType parseType(String raw) {
        String v = raw.trim();
        if (v.isEmpty()) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "题型不能为空");
        }
        return switch (v) {
            case "单选题", "单选", "SINGLE", "single" -> QuestionType.SINGLE;
            case "多选题", "多选", "MULTIPLE", "multiple" -> QuestionType.MULTIPLE;
            case "判断题", "判断", "JUDGE", "judge" -> QuestionType.JUDGE;
            case "填空题", "填空", "BLANK", "blank" -> QuestionType.BLANK;
            default -> throw BusinessException.of(ErrorCode.PARAM_INVALID,
                    "无法识别的题型「" + v + "」，应为 单选题/多选题/判断题/填空题");
        };
    }

    private Difficulty parseDifficulty(String raw) {
        String v = raw.trim();
        if (v.isEmpty()) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "难度不能为空");
        }
        return switch (v) {
            case "简单", "容易", "EASY", "easy" -> Difficulty.EASY;
            case "中等", "一般", "MEDIUM", "medium" -> Difficulty.MEDIUM;
            case "困难", "难", "HARD", "hard" -> Difficulty.HARD;
            default -> throw BusinessException.of(ErrorCode.PARAM_INVALID,
                    "无法识别的难度「" + v + "」，应为 简单/中等/困难");
        };
    }

    private Integer parseScore(String raw) {
        String v = raw.trim();
        if (v.isEmpty()) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "分值不能为空");
        }
        try {
            // 兼容 Excel 把 5 存成 5.0 的情况
            double d = Double.parseDouble(v);
            if (d != Math.floor(d)) {
                throw BusinessException.of(ErrorCode.PARAM_INVALID, "分值必须是整数");
            }
            return (int) d;
        } catch (NumberFormatException e) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "分值「" + v + "」不是数字");
        }
    }

    /**
     * 解析答案。
     * <p>
     * 设计成「按题型分别解析」而不是统一 split：
     * 选择题的答案与选项字母相关、判断题是中文/字母布尔值、填空题允许多个可接受写法，
     * 三种语义完全不同，混在一起解析必然出错。
     */
    private List<String> parseAnswer(QuestionType type, String raw, List<String> options) {
        String v = raw.trim();
        return switch (type) {
            case SINGLE -> {
                String letter = v.replaceAll("[,，;；\\s]", "").toUpperCase();
                if (letter.length() != 1) {
                    throw BusinessException.of(ErrorCode.PARAM_INVALID,
                            "单选题答案应为单个选项字母，如 A");
                }
                yield List.of(letter);
            }
            case MULTIPLE -> {
                List<String> letters = Arrays.stream(v.split("[,，;；\\s]+"))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(s -> s.toUpperCase())
                        .distinct()
                        .collect(Collectors.toList());
                if (letters.size() < 2) {
                    throw BusinessException.of(ErrorCode.PARAM_INVALID,
                            "多选题答案至少两个选项字母，如 A,C");
                }
                yield letters;
            }
            case JUDGE -> {
                String s = v.toUpperCase();
                if (s.equals("T") || s.equals("TRUE") || v.equals("正确") || v.equals("对") || v.equals("√")) {
                    yield List.of("T");
                }
                if (s.equals("F") || s.equals("FALSE") || v.equals("错误") || v.equals("错") || v.equals("×")) {
                    yield List.of("F");
                }
                throw BusinessException.of(ErrorCode.PARAM_INVALID,
                        "判断题答案应为 正确/错误（或 T/F）");
            }
            case BLANK -> {
                // 填空题：| 分隔多个可接受答案
                List<String> accepted = Arrays.stream(v.split("[|｜]"))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .distinct()
                        .collect(Collectors.toList());
                if (accepted.isEmpty()) {
                    throw BusinessException.of(ErrorCode.PARAM_INVALID, "填空题答案不能为空");
                }
                yield accepted;
            }
        };
    }

    private Long resolveCourse(String name) {
        String v = name.trim();
        if (v.isEmpty()) {
            return null;
        }
        Map<String, Long> byName = courseRepository.findAll().stream()
                .filter(c -> !c.getDeleted())
                .collect(Collectors.toMap(c -> c.getName().trim(), c -> c.getId(), (a, b) -> a));
        Long id = byName.get(v);
        if (id == null) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID,
                    "课程「" + v + "」不存在，请先创建课程或留空该列");
        }
        return id;
    }

    private List<Long> resolveKnowledge(String names) {
        String v = names.trim();
        if (v.isEmpty()) {
            return List.of();
        }
        Map<String, Long> byName = knowledgePointRepository.findAll().stream()
                .filter(k -> !k.getDeleted())
                .collect(Collectors.toMap(k -> k.getName().trim(), k -> k.getId(), (a, b) -> a));

        List<Long> ids = new ArrayList<>();
        for (String raw : v.split("[,，;；、|]+")) {
            String name = raw.trim();
            if (name.isEmpty()) {
                continue;
            }
            Long id = byName.get(name);
            if (id == null) {
                throw BusinessException.of(ErrorCode.PARAM_INVALID,
                        "知识点「" + name + "」不存在，请先到「知识点管理」创建，或留空该列");
            }
            if (!ids.contains(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    private String cell(Row row, int index, DataFormatter fmt) {
        Cell c = row.getCell(index);
        return c == null ? "" : fmt.formatCellValue(c).trim();
    }

    private boolean isBlankRow(Row row, DataFormatter fmt) {
        for (int i = 0; i <= 11; i++) {
            if (!cell(row, i, fmt).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private void setCell(Row row, int index, String value, CellStyle style) {
        Cell c = row.createCell(index);
        c.setCellValue(value == null ? "" : value);
        c.setCellStyle(style);
    }

    /** 导出的文件内容 + 建议文件名 */
    public record ExcelFile(byte[] content, String filename) {
    }

    /** 复用同一套单元格样式，避免每个单元格都新建 style（POI 的 style 数量有上限） */
    private static final class Styles {
        final CellStyle title;
        final CellStyle meta;
        final CellStyle header;
        final CellStyle normal;
        final CellStyle center;

        Styles(Workbook wb) {
            Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 15);
            title = wb.createCellStyle();
            title.setFont(titleFont);
            title.setAlignment(HorizontalAlignment.CENTER);
            title.setVerticalAlignment(VerticalAlignment.CENTER);

            Font metaFont = wb.createFont();
            metaFont.setFontHeightInPoints((short) 10);
            metaFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            meta = wb.createCellStyle();
            meta.setFont(metaFont);
            meta.setAlignment(HorizontalAlignment.CENTER);

            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            header = wb.createCellStyle();
            header.setFont(headerFont);
            header.setAlignment(HorizontalAlignment.CENTER);
            header.setVerticalAlignment(VerticalAlignment.CENTER);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            applyBorder(header);

            normal = wb.createCellStyle();
            normal.setVerticalAlignment(VerticalAlignment.CENTER);
            applyBorder(normal);

            center = wb.createCellStyle();
            center.setAlignment(HorizontalAlignment.CENTER);
            center.setVerticalAlignment(VerticalAlignment.CENTER);
            applyBorder(center);
        }

        private void applyBorder(CellStyle style) {
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
        }
    }
}
