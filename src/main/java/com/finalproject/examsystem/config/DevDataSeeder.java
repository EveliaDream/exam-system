package com.finalproject.examsystem.config;

import com.finalproject.examsystem.domain.Course;
import com.finalproject.examsystem.domain.Difficulty;
import com.finalproject.examsystem.domain.KnowledgePoint;
import com.finalproject.examsystem.domain.Question;
import com.finalproject.examsystem.domain.QuestionKnowledge;
import com.finalproject.examsystem.domain.QuestionType;
import com.finalproject.examsystem.domain.Role;
import com.finalproject.examsystem.domain.StudentClass;
import com.finalproject.examsystem.domain.User;
import com.finalproject.examsystem.repository.CourseRepository;
import com.finalproject.examsystem.repository.KnowledgePointRepository;
import com.finalproject.examsystem.repository.QuestionKnowledgeRepository;
import com.finalproject.examsystem.repository.QuestionRepository;
import com.finalproject.examsystem.repository.StudentClassRepository;
import com.finalproject.examsystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 开发环境种子数据。
 * <p>
 * 幂等：仅当 sys_user 表为空时写入，重启不会产生重复数据。
 * 生产环境通过 exam.seed.enabled=false 关闭（见 application-prod.yml）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "exam.seed.enabled", havingValue = "true")
public class DevDataSeeder implements ApplicationRunner {

    /** 所有测试账号的统一初始密码 */
    public static final String DEFAULT_PASSWORD = "123456";

    private final UserRepository userRepository;
    private final StudentClassRepository classRepository;
    private final CourseRepository courseRepository;
    private final QuestionRepository questionRepository;
    private final KnowledgePointRepository knowledgePointRepository;
    private final QuestionKnowledgeRepository questionKnowledgeRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            log.info("已存在用户数据，跳过种子数据初始化");
            return;
        }

        // ===== 用户 =====
        User teacher = userRepository.save(User.create(
                "teacher", encode(DEFAULT_PASSWORD), "张老师", Role.TEACHER));
        // 第二个教师，用于验证数据隔离：教师 A 不应看到教师 B 名下的班级与学生
        User teacher2 = userRepository.save(User.create(
                "teacher2", encode(DEFAULT_PASSWORD), "刘老师", Role.TEACHER));
        User student1 = userRepository.save(User.create(
                "student1", encode(DEFAULT_PASSWORD), "李小明", Role.STUDENT));
        User student2 = userRepository.save(User.create(
                "student2", encode(DEFAULT_PASSWORD), "王小红", Role.STUDENT));
        // 未分班学生，用于验证"待分配学生"的编辑与分班流程
        User student3 = userRepository.save(User.create(
                "student3", encode(DEFAULT_PASSWORD), "赵小刚", Role.STUDENT));

        // ===== 班级 =====
        StudentClass class1 = classRepository.save(StudentClass.create("软件工程 2301 班", "2023", teacher));
        StudentClass class2 = classRepository.save(StudentClass.create("软件工程 2302 班", "2023", teacher));
        // 归属第二个教师的班级
        StudentClass class3 = classRepository.save(StudentClass.create("网络工程 2301 班", "2023", teacher2));

        student1.setStudentClass(class1);
        student2.setStudentClass(class2);
        userRepository.save(student1);
        userRepository.save(student2);
        userRepository.save(student3);

        // ===== 课程 =====
        Course java = courseRepository.save(Course.create("Java 程序设计", "SE101", teacher));
        Course se = courseRepository.save(Course.create("软件工程导论", "SE102", teacher));

        // ===== 知识点 =====
        // 学情分析的主干：题目的正确/错误会按知识点聚合，得出学生的掌握程度。
        // 种子知识点按课程归类，录题与组卷时可直接选用。
        KnowledgePoint kpOop = knowledgePointRepository.save(
                KnowledgePoint.create("面向对象基础", "封装、继承、多态等面向对象基本概念", java.getId(), 10));
        KnowledgePoint kpInterface = knowledgePointRepository.save(
                KnowledgePoint.create("接口与抽象类", "接口定义、实现与抽象类的区别", java.getId(), 20));
        KnowledgePoint kpString = knowledgePointRepository.save(
                KnowledgePoint.create("String 与不可变性", "String 对象的不可变特性与字符串操作", java.getId(), 30));
        KnowledgePoint kpCollection = knowledgePointRepository.save(
                KnowledgePoint.create("集合框架", "List、Set、Map 的实现与线程安全", java.getId(), 40));
        KnowledgePoint kpType = knowledgePointRepository.save(
                KnowledgePoint.create("基本数据类型与修饰符", "八大基本类型、访问修饰符与作用域", java.getId(), 50));
        KnowledgePoint kpProgram = knowledgePointRepository.save(
                KnowledgePoint.create("程序结构与入口", "类结构、main 方法与方法签名", java.getId(), 60));
        KnowledgePoint kpJvm = knowledgePointRepository.save(
                KnowledgePoint.create("内存管理与垃圾回收", "对象生命周期、可达性分析与内存泄漏", java.getId(), 70));
        KnowledgePoint kpLifecycle = knowledgePointRepository.save(
                KnowledgePoint.create("软件生命周期", "需求、设计、编码、测试、维护各阶段", se.getId(), 10));
        KnowledgePoint kpProcess = knowledgePointRepository.save(
                KnowledgePoint.create("软件过程模型", "瀑布、敏捷、V 模型等开发过程", se.getId(), 20));
        KnowledgePoint kpTest = knowledgePointRepository.save(
                KnowledgePoint.create("软件测试", "测试目的、测试类型与用例设计", se.getId(), 30));
        KnowledgePoint kpRequirement = knowledgePointRepository.save(
                KnowledgePoint.create("需求分析", "需求获取、分析与需求规格说明", se.getId(), 40));
        KnowledgePoint kpDesign = knowledgePointRepository.save(
                KnowledgePoint.create("概要设计", "由需求推导软件体系结构", se.getId(), 50));

        // ===== 题库：四种题型各若干，用于阶段 3、4 的联调 =====
        List<Question> questions = List.of(
                // --- 单选题 ---
                single(java, teacher, "下列哪个关键字用于在 Java 中实现接口？",
                        List.of("extends", "implements", "inherits", "instanceof"), "B",
                        Difficulty.EASY, "Java 中类实现接口使用 implements，继承类使用 extends。"),
                single(java, teacher, "Java 中 String 类型的对象具有什么特性？",
                        List.of("可变的", "不可变的", "只能存数字", "必须用 new 创建"), "B",
                        Difficulty.EASY, "String 是不可变类，任何修改都会产生新对象。"),
                single(java, teacher, "下列哪个集合类是线程安全的？",
                        List.of("ArrayList", "HashMap", "Vector", "LinkedList"), "C",
                        Difficulty.MEDIUM, "Vector 的方法加了 synchronized；其余为非线程安全实现。"),
                single(java, teacher, "int 类型在 Java 中占多少字节？",
                        List.of("2", "4", "8", "取决于操作系统"), "B",
                        Difficulty.EASY, "Java 基本类型长度与平台无关，int 固定为 4 字节。"),
                single(java, teacher, "以下哪个不是 Java 的访问修饰符？",
                        List.of("public", "protected", "friendly", "private"), "C",
                        Difficulty.MEDIUM, "Java 没有 friendly，包级私有通过不写修饰符实现。"),
                single(se, teacher, "软件生命周期中，最早开展的阶段是？",
                        List.of("详细设计", "需求分析", "编码实现", "单元测试"), "B",
                        Difficulty.EASY, "需求分析是软件生命周期的起点。"),
                single(se, teacher, "下列哪个模型强调迭代与增量交付？",
                        List.of("瀑布模型", "敏捷开发模型", "V 模型", "结构化模型"), "B",
                        Difficulty.MEDIUM, "敏捷开发通过短迭代持续交付可用软件。"),

                // --- 多选题 ---
                multiple(java, teacher, "下列属于 Java 基本数据类型的有？",
                        List.of("int", "String", "boolean", "double"), List.of("A", "C", "D"),
                        Difficulty.MEDIUM, "String 是引用类型，不是基本数据类型。"),
                multiple(java, teacher, "面向对象的三大特征包括？",
                        List.of("封装", "继承", "多态", "编译"), List.of("A", "B", "C"),
                        Difficulty.EASY, "面向对象三大特征：封装、继承、多态。"),
                multiple(java, teacher, "关于接口与抽象类，说法正确的有？",
                        List.of("接口可以多实现", "抽象类可以多继承", "接口中的方法默认 public",
                                "抽象类可以有构造方法"), List.of("A", "C", "D"),
                        Difficulty.HARD, "Java 类只能单继承，因此 B 错误。"),
                multiple(se, teacher, "常见的软件测试类型包括？",
                        List.of("单元测试", "集成测试", "系统测试", "验收测试"), List.of("A", "B", "C", "D"),
                        Difficulty.EASY, "按测试阶段划分的经典四类测试。"),
                multiple(se, teacher, "需求分析阶段常见的产出物有？",
                        List.of("用例图", "数据流图", "需求规格说明书", "可执行程序"), List.of("A", "B", "C"),
                        Difficulty.MEDIUM, "可执行程序属于实现阶段产物。"),

                // --- 判断题 ---
                judge(java, teacher, "Java 支持类的多继承。", false,
                        Difficulty.EASY, "Java 类只支持单继承，多继承通过接口实现。"),
                judge(java, teacher, "接口中的变量默认是 public static final 的。", true,
                        Difficulty.MEDIUM, "接口中定义的都是常量，隐式 public static final。"),
                judge(java, teacher, "垃圾回收机制可以完全避免内存泄漏。", false,
                        Difficulty.MEDIUM, "GC 只能回收不可达对象，静态集合持有引用等仍会造成泄漏。"),
                judge(se, teacher, "软件测试的目的是证明程序没有错误。", false,
                        Difficulty.EASY, "测试的目的是发现错误，而非证明无错。"),
                judge(se, teacher, "UML 用例图主要用于描述系统的功能需求。", true,
                        Difficulty.EASY, "用例图从用户视角描述系统功能。"),

                // --- 填空题 ---
                blank(java, teacher, "Java 中用于继承父类的关键字是 ______。",
                        List.of("extends", "EXTENDS"), Difficulty.EASY, "继承使用 extends 关键字。"),
                blank(java, teacher, "Java 程序的入口方法是 ______。",
                        List.of("main", "main方法", "main()"), Difficulty.EASY,
                        "入口方法签名为 public static void main(String[] args)。"),
                blank(se, teacher, "软件工程中，将需求转化为软件结构的过程称为 ______ 设计。",
                        List.of("概要", "总体"), Difficulty.MEDIUM, "概要设计（总体设计）确定软件体系结构。")
        );
        questionRepository.saveAll(questions);

        // ===== 题目关联知识点 =====
        // 按题目内容把上面创建的知识点挂到对应题目上，供录题页展示与后续学情统计使用。
        // 注意：综合题可以挂多个知识点，这里保持每题 1 个，便于演示与统计。
        List<List<KnowledgePoint>> bindings = List.of(
                List.of(kpInterface),      // 1 实现接口的关键字
                List.of(kpString),         // 2 String 特性
                List.of(kpCollection),     // 3 线程安全集合
                List.of(kpType),           // 4 int 字节数
                List.of(kpType),           // 5 访问修饰符
                List.of(kpLifecycle),      // 6 生命周期最早阶段
                List.of(kpProcess),        // 7 迭代增量模型
                List.of(kpType),           // 8 基本数据类型
                List.of(kpOop),            // 9 面向对象三大特征
                List.of(kpInterface),      // 10 接口与抽象类
                List.of(kpTest),           // 11 测试类型
                List.of(kpRequirement),    // 12 需求分析产出物
                List.of(kpOop),            // 13 多继承
                List.of(kpInterface),      // 14 接口变量修饰符
                List.of(kpJvm),            // 15 垃圾回收
                List.of(kpTest),           // 16 测试目的
                List.of(kpRequirement),    // 17 用例图
                List.of(kpOop),            // 18 extends 关键字
                List.of(kpProgram),        // 19 main 方法
                List.of(kpDesign)          // 20 概要设计
        );
        for (int i = 0; i < questions.size() && i < bindings.size(); i++) {
            Long qid = questions.get(i).getId();
            for (KnowledgePoint kp : bindings.get(i)) {
                questionKnowledgeRepository.save(QuestionKnowledge.of(qid, kp.getId()));
            }
        }

        log.info("""
                        
                        ==================== 种子数据初始化完成 ====================
                        测试账号（密码均为 {}）：
                          teacher  / {}  教师  张老师（名下班级：软件工程 2301/2302 班）
                          teacher2 / {}  教师  刘老师（名下班级：网络工程 2301 班，用于验证数据隔离）
                          student1 / {}  学生  李小明（软件工程 2301 班）
                          student2 / {}  学生  王小红（软件工程 2302 班）
                          student3 / {}  学生  赵小刚（未分班，用于验证分班流程）
                        班级 {} 个，课程 {} 个，题目 {} 道，知识点 {} 个
                        ============================================================
                        """,
                DEFAULT_PASSWORD, DEFAULT_PASSWORD, DEFAULT_PASSWORD,
                DEFAULT_PASSWORD, DEFAULT_PASSWORD, DEFAULT_PASSWORD,
                classRepository.count(), courseRepository.count(),
                questionRepository.count(), knowledgePointRepository.count());
    }

    private String encode(String raw) {
        return passwordEncoder.encode(raw);
    }

    // ===== 构造题目的小工具方法，避免大段重复代码 =====

    private Question single(Course course, User teacher, String stem, List<String> options,
                            String answer, Difficulty difficulty, String analysis) {
        return build(course, teacher, QuestionType.SINGLE, stem, options, List.of(answer), difficulty, analysis);
    }

    private Question multiple(Course course, User teacher, String stem, List<String> options,
                              List<String> answers, Difficulty difficulty, String analysis) {
        return build(course, teacher, QuestionType.MULTIPLE, stem, options, answers, difficulty, analysis);
    }

    private Question judge(Course course, User teacher, String stem, boolean correct,
                           Difficulty difficulty, String analysis) {
        return build(course, teacher, QuestionType.JUDGE, stem,
                List.of("正确", "错误"), List.of(correct ? "T" : "F"), difficulty, analysis);
    }

    private Question blank(Course course, User teacher, String stem, List<String> acceptableAnswers,
                           Difficulty difficulty, String analysis) {
        return build(course, teacher, QuestionType.BLANK, stem, List.of(), acceptableAnswers, difficulty, analysis);
    }

    private Question build(Course course, User teacher, QuestionType type, String stem,
                           List<String> options, List<String> answer, Difficulty difficulty, String analysis) {
        Question question = new Question();
        question.setCourse(course);
        question.setCreatedBy(teacher);
        question.setType(type);
        question.setStem(stem);
        question.setOptions(List.copyOf(options));
        question.setAnswer(List.copyOf(answer));
        question.setDifficulty(difficulty);
        question.setAnalysis(analysis);
        question.setScore(type == QuestionType.MULTIPLE ? 10 : 5);
        return question;
    }
}
