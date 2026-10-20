-- ============================================================================
--  学生在线考试系统 · MySQL 8 建表脚本
--  说明：
--    1. 这是交付物之一（毕设通常要求提交数据库脚本），与实体类保持一致。
--    2. 开发环境默认用 H2 内存库自动建表；本脚本用于 MySQL 交付/答辩演示。
--    3. 字符集必须是 utf8mb4，否则中文题干会乱码。
--  使用方法：
--    mysql -uroot -p < docs/sql/schema.sql
--    然后以 prod profile 启动：mvnw spring-boot:run -Dspring-boot.run.profiles=prod
-- ============================================================================

CREATE DATABASE IF NOT EXISTS exam_system
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

USE exam_system;

-- 按依赖反序删除，避免外键约束报错
DROP TABLE IF EXISTS answer;
DROP TABLE IF EXISTS exam_record;
DROP TABLE IF EXISTS exam_class;
DROP TABLE IF EXISTS exam_question;
DROP TABLE IF EXISTS exam;
DROP TABLE IF EXISTS question_knowledge;
DROP TABLE IF EXISTS knowledge_point;
DROP TABLE IF EXISTS question;
DROP TABLE IF EXISTS course;
DROP TABLE IF EXISTS student_class;
DROP TABLE IF EXISTS sys_user;
DROP TABLE IF EXISTS operation_log;

-- ---------------------------------------------------------------------------
-- 班级
-- ---------------------------------------------------------------------------
CREATE TABLE student_class
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    name       VARCHAR(50)  NOT NULL COMMENT '班级名称',
    grade      VARCHAR(20) COMMENT '年级',
    teacher_id BIGINT COMMENT '班主任/任课教师',
    created_at DATETIME(6)  NOT NULL,
    deleted    BIT(1)       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_class_teacher (teacher_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='班级';

-- ---------------------------------------------------------------------------
-- 用户（学生与教师共用，表名避开保留字 user）
-- ---------------------------------------------------------------------------
CREATE TABLE sys_user
(
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    username      VARCHAR(50)  NOT NULL COMMENT '登录名',
    password      VARCHAR(100) NOT NULL COMMENT 'BCrypt 密文',
    real_name     VARCHAR(50)  NOT NULL COMMENT '真实姓名',
    role          ENUM ('STUDENT','TEACHER') NOT NULL COMMENT '角色',
    class_id      BIGINT COMMENT '学生所属班级，教师为空',
    enabled       BIT(1)       NOT NULL DEFAULT 1 COMMENT '是否启用',
    token_version INT          NOT NULL DEFAULT 0 COMMENT 'token 版本，改密码时 +1 使旧 token 失效',
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6),
    deleted       BIT(1)       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username),
    KEY idx_user_class (class_id),
    CONSTRAINT fk_user_class FOREIGN KEY (class_id) REFERENCES student_class (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='用户';

-- ---------------------------------------------------------------------------
-- 课程
-- ---------------------------------------------------------------------------
CREATE TABLE course
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    name       VARCHAR(100) NOT NULL,
    code       VARCHAR(50),
    teacher_id BIGINT,
    created_at DATETIME(6)  NOT NULL,
    deleted    BIT(1)       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_course_teacher (teacher_id),
    CONSTRAINT fk_course_teacher FOREIGN KEY (teacher_id) REFERENCES sys_user (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='课程';

-- ---------------------------------------------------------------------------
-- 题目（四种题型共用一张表）
--   SINGLE/MULTIPLE：options=["选项A内容",...]，answer=["A"] / ["A","C"]
--   JUDGE：options=["正确","错误"]，answer=["T"] 或 ["F"]
--   BLANK：options 为空，answer=["标准答案","可接受的其他写法"]
-- ---------------------------------------------------------------------------
CREATE TABLE question
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    type       ENUM ('SINGLE','MULTIPLE','JUDGE','BLANK') NOT NULL,
    stem       TEXT        NOT NULL COMMENT '题干',
    options    TEXT COMMENT 'JSON 数组字符串：选项列表',
    answer     TEXT COMMENT 'JSON 数组字符串：正确答案',
    score      INT         NOT NULL DEFAULT 5 COMMENT '建议分值',
    difficulty ENUM ('EASY','MEDIUM','HARD') NOT NULL DEFAULT 'MEDIUM',
    analysis   TEXT COMMENT '解析',
    image_url  VARCHAR(255),
    course_id  BIGINT,
    created_by BIGINT,
    enabled    BIT(1)      NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6),
    deleted    BIT(1)      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_question_course (course_id),
    KEY idx_question_type (type),
    CONSTRAINT fk_question_course FOREIGN KEY (course_id) REFERENCES course (id),
    CONSTRAINT fk_question_creator FOREIGN KEY (created_by) REFERENCES sys_user (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='题库';

-- ---------------------------------------------------------------------------
-- 知识点
--   学情分析的主干：学生的掌握程度 = 其作答题目所覆盖知识点的正确率。
--   一道题可挂多个知识点（综合题常见），故用 question_knowledge 建立多对多关系。
--   course_id 为空表示「全校通用」知识点，适用于公共课。
-- ---------------------------------------------------------------------------
CREATE TABLE knowledge_point
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL COMMENT '知识点名称，如「面向对象·多态」',
    description VARCHAR(500) COMMENT '知识点说明，可写清考查范围',
    course_id   BIGINT COMMENT '所属课程，为空表示通用',
    sort_order  INT          NOT NULL DEFAULT 0 COMMENT '同一课程内的展示顺序',
    enabled     BIT(1)       NOT NULL DEFAULT 1,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6),
    deleted     BIT(1)       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_kp_course (course_id),
    KEY idx_kp_name (name),
    CONSTRAINT fk_kp_course FOREIGN KEY (course_id) REFERENCES course (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='知识点';

-- ---------------------------------------------------------------------------
-- 题目 × 知识点（多对多）
--   删除题目或知识点时按逻辑删除处理，关联表记录保留，便于历史统计追溯。
-- ---------------------------------------------------------------------------
CREATE TABLE question_knowledge
(
    question_id         BIGINT NOT NULL,
    knowledge_point_id  BIGINT NOT NULL,
    PRIMARY KEY (question_id, knowledge_point_id),
    KEY idx_qk_kp (knowledge_point_id),
    CONSTRAINT fk_qk_question FOREIGN KEY (question_id) REFERENCES question (id),
    CONSTRAINT fk_qk_kp FOREIGN KEY (knowledge_point_id) REFERENCES knowledge_point (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='题目知识点关联';

-- ---------------------------------------------------------------------------
-- 考试（试卷定义）
-- ---------------------------------------------------------------------------
CREATE TABLE exam
(
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    title            VARCHAR(200) NOT NULL,
    description      TEXT,
    course_id        BIGINT,
    total_score      INT          NOT NULL DEFAULT 100,
    pass_score       INT          NOT NULL DEFAULT 60,
    start_time       DATETIME(6)  NOT NULL COMMENT '考试开放时间',
    end_time         DATETIME(6)  NOT NULL COMMENT '考试关闭时间',
    duration         INT          NOT NULL DEFAULT 60 COMMENT '作答时长（分钟）',
    shuffle_question BIT(1)       NOT NULL DEFAULT 0,
    shuffle_option   BIT(1)       NOT NULL DEFAULT 0,
    max_attempt      INT          NOT NULL DEFAULT 1 COMMENT '最大作答次数',
    status           ENUM ('DRAFT','PUBLISHED','FINISHED') NOT NULL DEFAULT 'DRAFT',
    created_by       BIGINT,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6),
    deleted          BIT(1)       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_exam_course (course_id),
    CONSTRAINT fk_exam_course FOREIGN KEY (course_id) REFERENCES course (id),
    CONSTRAINT fk_exam_creator FOREIGN KEY (created_by) REFERENCES sys_user (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='考试';

-- ---------------------------------------------------------------------------
-- 组卷快照
--   关键：stem/options/answer 在这里各存一份副本。
--   否则教师后续修改题库，已考完的考试成绩会跟着变化 —— 这是最容易被答辩抓到的漏洞。
-- ---------------------------------------------------------------------------
CREATE TABLE exam_question
(
    attempt_no  INT         NOT NULL DEFAULT 1 COMMENT '作答轮次，补考时递增',
    question_id BIGINT      NOT NULL,
    exam_id     BIGINT      NOT NULL,
    type        ENUM ('SINGLE','MULTIPLE','JUDGE','BLANK') NOT NULL,
    stem        TEXT        NOT NULL,
    options     TEXT,
    answer      TEXT        NOT NULL COMMENT '正确答案快照，判分只读这里',
    analysis    TEXT,
    score       INT         NOT NULL COMMENT '该题在本场考试中的分值',
    sort_order  INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (attempt_no, question_id),
    KEY idx_eq_exam (exam_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='组卷快照';

-- ---------------------------------------------------------------------------
-- 考试发布范围（exam × class）
-- ---------------------------------------------------------------------------
CREATE TABLE exam_class
(
    exam_id  BIGINT NOT NULL,
    class_id BIGINT NOT NULL,
    PRIMARY KEY (exam_id, class_id),
    KEY idx_ec_class (class_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='考试发布范围';

-- ---------------------------------------------------------------------------
-- 作答记录（学生 × 考试 × 轮次）
-- ---------------------------------------------------------------------------
CREATE TABLE exam_record
(
    id                  BIGINT      NOT NULL AUTO_INCREMENT,
    exam_id             BIGINT      NOT NULL,
    student_id          BIGINT      NOT NULL,
    attempt_no          INT         NOT NULL DEFAULT 1,
    start_time          DATETIME(6) COMMENT '开始作答时间，服务端据此计算截止时间',
    submit_time         DATETIME(6),
    objective_score     INT COMMENT '客观题得分，本系统即最终成绩',
    status              ENUM ('NOT_STARTED','IN_PROGRESS','SUBMITTED','AUTO_SUBMITTED') NOT NULL DEFAULT 'NOT_STARTED',
    switch_screen_count INT         NOT NULL DEFAULT 0 COMMENT '切屏次数，仅供教师参考',
    created_at          DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_record_exam_student_attempt (exam_id, student_id, attempt_no),
    KEY idx_record_student (student_id),
    CONSTRAINT fk_record_exam FOREIGN KEY (exam_id) REFERENCES exam (id),
    CONSTRAINT fk_record_student FOREIGN KEY (student_id) REFERENCES sys_user (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='作答记录';

-- ---------------------------------------------------------------------------
-- 作答明细（进入考试时按 exam_question 预填空行，自动保存时 UPDATE）
-- ---------------------------------------------------------------------------
CREATE TABLE answer
(
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    record_id       BIGINT      NOT NULL,
    question_id     BIGINT      NOT NULL,
    student_answer  TEXT COMMENT 'JSON 数组字符串：学生答案',
    score           INT COMMENT '该题得分',
    is_correct      BIT(1)      NOT NULL DEFAULT 0,
    answered_at     DATETIME(6) COMMENT '最后一次作答时间',
    first_answered_at DATETIME(6) COMMENT '首次作答时间，与 answered_at 之差即该题耗时',
    time_spent_sec  INT COMMENT '该题累计作答耗时（秒），前端上报；用于难度与专注度分析',
    submit_count    INT         NOT NULL DEFAULT 0 COMMENT '该题提交/修改次数，反映犹豫程度',
    PRIMARY KEY (id),
    UNIQUE KEY uk_answer_record_question (record_id, question_id),
    CONSTRAINT fk_answer_record FOREIGN KEY (record_id) REFERENCES exam_record (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='作答明细';

-- ---------------------------------------------------------------------------
-- 操作日志（审计：成绩被人改了怎么追溯）
-- ---------------------------------------------------------------------------
CREATE TABLE operation_log
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT,
    username   VARCHAR(50),
    action     VARCHAR(50)  NOT NULL COMMENT 'LOGIN / SUBMIT_EXAM / CREATE_QUESTION ...',
    target     VARCHAR(200) COMMENT '操作对象，如 exam#12',
    ip         VARCHAR(50),
    created_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_log_user (user_id),
    KEY idx_log_created (created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='操作日志';
