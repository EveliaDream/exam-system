# 学生在线考试系统

基于 Spring Boot 4 + Vue 3 的在线考试系统。面向大学软件工程综合实践，功能聚焦"客观题考试"闭环，不涉及 AI 判分等复杂技术。

**当前进度：全部完成**（阶段 0~6 + 收尾完善 + 增强四项）。
后端 141 个测试全绿，端到端检查 41 项全通过。

---

## 技术栈

| 层 | 选型 | 版本说明 |
|---|---|---|
| 后端 | Spring Boot | 4.1.1（注意：与网上 3.x 教程差异较大，见 [开发笔记](docs/DEV-NOTES.md)） |
| JDK | Java | 25（Boot 4 支持 17~25） |
| 持久层 | Spring Data JPA + Hibernate | 7.4.5 |
| 数据库 | MySQL 8（默认，数据持久化）/ H2 内存库（可选） | 通过 profile 切换 |
| 安全 | Spring Security + JWT | jjwt 0.12.6 |
| 前端 | Vue 3 + Vite 6 + Element Plus + Pinia + Axios | |
| 构建 | Maven（mvnw）+ npm | |

---

## 快速开始

> **数据库说明**：项目默认使用 **MySQL 8**（数据持久化）。
> 如果手边没有 MySQL，可以切到内置的内存库，一条命令即可跑起来，见下方"方式 B"。

### 前置：初始化数据库（仅第一次需要）

```bash
mysql -uroot -p < docs/sql/schema.sql
```

脚本会创建 `exam_system` 库（`utf8mb4`）并建立 10 张表。**不要手工建库**，否则中文题干会乱码。

MySQL 账号密码在 `src/main/resources/application-dev.yml` 中配置（当前为 `root` / `admin`），
也可以用环境变量覆盖：

```powershell
$env:MYSQL_USERNAME="root"; $env:MYSQL_PASSWORD="你的密码"
```

### 1. 启动后端

**方式 A：MySQL（默认）**

```bash
./mvnw spring-boot:run
```

首次启动会自动写入种子数据（见下方测试账号）。之后重启**数据会保留**，
种子数据是幂等的（`sys_user` 非空就跳过），不会重复插入。

**方式 B：H2 内存库（不需要 MySQL）**

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2
```

进程内数据库，每次重启数据清空后重新写入种子数据，适合快速冒烟验证。
H2 控制台：http://localhost:8080/h2-console （JDBC URL `jdbc:h2:mem:examdb`，用户名 `sa`，密码空）。

两种方式启动后，接口地址都是 http://localhost:8080 。

### 2. 启动前端

```bash
cd frontend
npm install
npm run dev
```

打开 http://localhost:5173 ，用测试账号登录。

> 前端通过 Vite 代理把 `/api` 转发到 8080，因此**开发期不存在跨域问题**，后端无需任何 CORS 配置。
> ⚠️ 后端与前端必须**同时运行**：Spring Boot 只提供 API，不会在 5173 上提供页面。

### 3. 测试账号（密码均为 `123456`）

| 账号 | 角色 | 姓名 | 班级 |
|---|---|---|---|
| `teacher` | 教师 | 张老师 | —（名下：软件工程 2301/2302 班） |
| `teacher2` | 教师 | 刘老师 | —（名下：网络工程 2301 班，用于验证数据隔离） |
| `student1` | 学生 | 李小明 | 软件工程 2301 班 |
| `student2` | 学生 | 王小红 | 软件工程 2302 班 |
| `student3` | 学生 | 赵小刚 | 未分班（用于验证分班流程） |

种子数据同时包含 3 个班级、2 门课程、20 道题（单选/多选/判断/填空各若干），供后续阶段联调使用。

> **数据隔离验证**：用 `teacher` 与 `teacher2` 分别登录，学生管理页只能看到自己名下班级的学生。
> 这是阶段 2 的核心安全约束，前后端都做了处理（后端 SQL 层隔离 + 前端菜单）。

---

## 验证与测试

```bash
# 后端测试（126 个用例，覆盖认证、权限、数据隔离、题库校验、组卷发布、作答判分、学情分析、Excel 导入导出）
./mvnw test

# 端到端联调检查（需先启动后端 8080 与前端 5173）
node docs/scripts/e2e-check.mjs      # 阶段 0/1：认证与权限边界，11 项
node docs/scripts/e2e-phase2.mjs     # 阶段 2：用户与班级管理业务规则，30 项
```

`e2e-check.mjs` 验证：Vite 代理连通性、教师登录、中文编码、token 鉴权、
无 token 401、教师访问教师接口 200、**学生访问教师接口 403**、密码错误 40101、参数校验 400。

`e2e-phase2.mjs` 验证：班级增删改与重名拦截、学生增删改查与筛选、分页结构、
**响应不含密码等敏感字段**、**教师间数据隔离（跨教师改/删/重置密码/分班全部 403）**、
班级删除保护（有学生时拒绝）、批量导入逐行处理与班级自动创建，以及运行后自清理。

阶段 3 的知识点与题库规则由 `QuestionBankApiTest`（13 个用例）覆盖：
知识点同课程重名拦截、被引用禁止删除、题型答案结构校验（单选唯一、多选≥2、判断 T/F、
答案越界）、删除题目后释放知识点关联、按知识点筛选、学生越权 403。

阶段 4 的组卷与发布由 `ExamApiTest`（16 个用例）覆盖，重点校验「发布前的五项把关」：
试卷非空、**题目分值之和等于试卷总分**、已绑定班级、时间窗合法且未过期、及格线不超总分；
以及「发布即冻结」——已发布考试不能改题目（50104）但可调整时间窗；
教师数据隔离（跨教师查看/修改/绑班全部 403 或 50108）；
学生只能看到本班考试、未分班学生列表为空。

---

## 已实现的功能模块

| 模块 | 状态 | 主要接口 |
|---|---|---|
| 认证与权限 | ✅ | `POST /api/auth/login`、`GET /api/auth/me`、`PUT /api/auth/password` |
| 学生管理（教师） | ✅ | `GET/POST/PUT/DELETE /api/students`、`PUT /api/students/{id}/password/reset`、`POST /api/students/import` |
| 班级管理（教师） | ✅ | `GET/POST/PUT/DELETE /api/classes`、`GET /api/classes/options` |
| 知识点管理（教师） | ✅ | `GET/POST/PUT/DELETE /api/knowledge-points`、`GET /api/knowledge-points/options` |
| 题库管理（教师） | ✅ | `GET/POST/PUT/DELETE /api/questions`、`PUT /api/questions/{id}/enabled`、`POST /api/questions/import`、`GET /api/questions/import/template` |
| 组卷与考试发布（教师） | ✅ | `GET/POST/PUT/DELETE /api/exams`、`PUT /api/exams/{id}/questions`、`POST /api/exams/{id}/questions/random`、`PUT /api/exams/{id}/classes`、`PUT /api/exams/{id}/publish`、`PUT /api/exams/{id}/finish` |
| 我的考试（学生） | ✅ | `GET /api/my/exams` |
| 在线作答与判分（学生） | ✅ | `POST /api/my/exams/{id}/start`、`GET /api/my/exams/{id}/paper`、`PUT /api/my/records/{id}/answers`、`POST /api/my/records/{id}/submit`、`GET /api/my/records/{id}/result`、`POST /api/my/records/{id}/switch-screen` |
| 我的成绩（学生） | ✅ | `GET /api/my/records` |
| 学情分析（教师） | ✅ | `GET /api/analytics/exams/{examId}` |
| 学情分析（学生） | ✅ | `GET /api/my/records/{id}/analysis`、`GET /api/my/records/{id}/detail` |
| 成绩单导出（教师） | ✅ | `GET /api/exams/{id}/export/scores`（xlsx，支持 `?classId=` 按班导出） |
| 自助改密（教师/学生） | ✅ | `PUT /api/auth/password`（顶栏「修改密码」） |
| 多轮考试（补考） | ✅ | 由考试 `maxAttempt` 控制，进入考试时自动分配轮次 |
| 题干配图 | ✅ | `POST /api/files/image`（上传）、`GET /api/files/{name}`（匿名读取） |
| 操作日志（教师） | ✅ | `GET /api/logs`、`GET /api/logs/actions` |

> **知识点与学情分析的关系**：系统的学情分析口径是「按知识点聚合作答正确率」。
> 因此知识点必须在录题时就标注（一道题可挂多个），否则该题的作答不会计入任何统计。
> 知识点被题目引用时禁止删除，避免历史统计出现空洞。

---

## 数据库配置详解

项目用 Spring Boot 的 profile 切换数据库，三份配置各司其职：

| profile | 数据库 | ddl-auto | 种子数据 | 适用场景 |
|---|---|---|---|---|
| `dev`（默认） | MySQL 8 | `validate` | 写入（幂等） | 日常开发，数据持久保留 |
| `prod` | MySQL 8 | `validate` | 不写 | 交付演示 / 答辩（不打印 SQL） |
| `h2` | H2 内存库 | `create-drop` | 写入 | 无 MySQL 环境 / 快速冒烟验证 |

配置文件：`application-dev.yml`、`application-prod.yml`、`application-h2.yml`。

### 为什么用 validate 而不是 update

`validate` 让应用启动时**校验**实体与表结构是否一致，但**不会自动改表**。
好处是"表结构漂移"会在启动阶段立刻暴露，而不是运行中悄悄出问题。
代价是：新增实体字段后必须同步更新 `docs/sql/schema.sql`。

```bash
# 新增实体字段后，重新执行脚本（会 DROP 重建，会清空数据！）
mysql -uroot -p < docs/sql/schema.sql
```

> 脚本开头有 `DROP TABLE IF EXISTS`，**执行会清空全部数据**。
> 只想改结构不想丢数据时，请手工 `ALTER TABLE`，或先把数据导出。

### 重置演示数据

需要把数据恢复到初始状态时，重新建表再启动即可：

```bash
mysql -uroot -p < docs/sql/schema.sql
./mvnw spring-boot:run
```

启动时检测到 `sys_user` 为空，会自动写入 5 个测试账号 + 3 个班级 + 2 门课程 + 20 道题。

### MySQL 连接配置

在 `application-dev.yml` / `application-prod.yml` 中修改，或用环境变量覆盖：

```powershell
$env:MYSQL_USERNAME="root"; $env:MYSQL_PASSWORD="你的密码"
```

> ⚠️ Windows PowerShell 用户注意：**不要用 `Get-Content schema.sql | mysql` 管道执行脚本**。
> PowerShell 会按 GBK 重编码管道文本，导致 SQL 里的中文注释变成乱码而报语法错误
> （`ERROR 1064 ... near 'BCrypt 瀵嗘枃'`）。请使用 `mysql -uroot -p < docs/sql/schema.sql`
> 的重定向方式，或加 `--default-character-set=utf8mb4`。

---

## 工程结构

```
exam-system/
├── pom.xml                      Maven 配置（含 Boot 4 依赖说明）
├── .mvn/maven.config            工作区内的 Maven 本地仓库设置（见开发笔记）
├── docs/
│   ├── ROADMAP.md               阶段 1~6 的完整开发路线图
│   ├── DEV-NOTES.md             踩坑记录与技术决策（Boot 4 / Jackson 3 等）
│   ├── sql/schema.sql           MySQL 建表脚本（交付物）
│   └── scripts/e2e-check.mjs    端到端联调验证脚本
├── src/main/java/com/finalproject/examsystem/
│   ├── common/                  Result / PageResult / ErrorCode / 全局异常处理
│   ├── config/                  SecurityConfig / JacksonConfig / DevDataSeeder
│   ├── controller/              Auth / User / Student / Class / Question / KnowledgePoint / Course /
│   │                            Exam / StudentExam / StudentAnswer / Analytics / Health
│   ├── domain/                  12 张表的实体 + 枚举 + JSON 转换器（含 ExamPhase 时间阶段）
│   ├── dto/                     请求/响应对象（与实体分离）
│   │   ├── auth/                认证相关
│   │   ├── user/                学生管理相关（查询、新增、修改、导入结果）
│   │   ├── clazz/               班级管理相关
│   │   ├── knowledge/           知识点相关（学情分析的主干）
│   │   ├── question/            题库相关（含知识点标签）
│   │   ├── exam/                组卷与考试发布相关
│   │   ├── answer/              作答与判分相关（题面 DTO 不含答案）
│   │   └── analytics/           学情分析相关（掌握度、难度贴合、作答详情）
│   ├── repository/              11 个 Spring Data JPA 接口（含教师数据范围查询）
│   ├── security/                JWT 生成校验 / 过滤器 / 用户主体 / 401·403 处理器
│   └── service/                 Auth / User / Class / Question / KnowledgePoint / Exam /
│                                 ExamTaking（作答与判分）/ ObjectiveGrader（判分纯函数）/
│                                 Analytics（学情分析）/ Excel（成绩单导出、题库导入）/
│                                 FileStorage（题干配图）/ OperationLog（审计日志）
├── src/main/resources/
│   ├── application.yml          公共配置
│   ├── application-dev.yml      MySQL（默认）+ 种子数据
│   ├── application-prod.yml     MySQL（交付）
│   └── application-h2.yml       H2 内存库（零依赖）
├── src/test/java/.../controller/
│   ├── BaseApiTest.java         测试基类（登录取 token、JSON 解析、唯一用户名）
│   ├── AuthApiTest.java         认证与权限
│   ├── StudentAdminApiTest.java 学生管理
│   ├── ClassAdminApiTest.java   班级管理
│   ├── DataScopeApiTest.java    教师数据隔离
│   ├── QuestionBankApiTest.java 知识点与题库规则
│   ├── ExamApiTest.java         组卷、发布校验与状态流转
│   ├── ExamTakingApiTest.java   作答、自动保存、判分与幂等
│   ├── AnalyticsApiTest.java    学情分析：掌握度、样本门槛、数据归属
│   ├── ExcelApiTest.java        Excel 导入导出：逐行容错、模板、越权
│   ├── MultiAttemptApiTest.java 多轮考试：轮次分配、次数用尽、成绩独立
│   └── ExtrasApiTest.java       配图上传与快照、班级筛选、操作日志
├── src/test/java/.../service/
│   └── ObjectiveGraderTest.java 判分规则单元测试（纯函数）
└── frontend/
    ├── vite.config.js           代理 /api → 8080；build 输出到后端静态目录
    └── src/
        ├── api/                 接口定义（auth / student / class / question / exam / answer / analytics / log / file）
        ├── layouts/             AdminLayout（侧边菜单 + 顶栏 + 修改密码）
        ├── router/              路由 + 登录/角色守卫
        ├── stores/              Pinia 登录态
        ├── utils/               Axios 封装（自动带 token、401 跳登录）+ 文件下载
        └── views/               登录 / 工作台 / 学生 / 班级 / 题库 / 知识点 /
                                 考试列表 / 考试编辑 / 学情分析 / 操作日志 /
                                 我的考试 / 在线作答 / 考试结果 / 我的成绩
```

---

## 已建立的开发约定

后续所有模块必须遵守，避免风格分裂：

1. **统一响应体**：`{ code, message, data }`，`code = 0` 表示成功。前端 Axios 拦截器只认这一种结构。
2. **错误码分段**：`1xxxx` 通用、`2xxxx` 认证权限、`3xxxx` 用户班级、`4xxxx` 题库、`5xxxx` 考试、`6xxxx` 作答判分。
3. **异常处理集中**：Service 抛 `BusinessException`，由 `GlobalExceptionHandler` 统一转换；Controller 不写 try/catch。
4. **DTO 与实体分离**：Controller 出入参一律用 `dto` 包下的 record，不直接暴露实体。
5. **身份从服务端取**：业务层用 `SecurityUtils.currentUserId()`，绝不信任前端传来的 userId。
6. **时间格式统一**：`yyyy-MM-dd HH:mm:ss`，由 `JacksonConfig` 保证。
7. **逻辑删除**：实体带 `deleted` 字段，查询统一加 `deleted = false`。
8. **分页页码从 1 开始**：与前端 Element Plus 一致，由 Service 转成 Spring Data 的 0 基页码。
9. **权限边界做两层**：`@PreAuthorize` 控制角色能访问哪些模块；
   数据范围（教师只能操作自己名下的班级与学生）写进 JPQL 查询，并在写操作前二次校验。
10. **敏感操作要影响登录态**：停用账号、重置密码、删除学生都要让 `tokenVersion + 1`，使已签发的 token 立即失效。
11. **统计型数据必须可聚合**：知识点、分值、作答耗时等字段是学情分析的口径，录入时必须强校验
    （例如题目必须挂至少一个知识点、题型答案结构必须合法），否则脏数据会让后续统计结果不可信。
12. **被引用数据不物理删除**：知识点被题目引用、学生有作答记录、班级内有学生时，
    一律拒绝删除并提示改为停用，避免历史统计出现"孤儿数据"。
13. **组卷即快照、发布即冻结**：组卷时把题干/选项/答案/分值复制进 `exam_question`；
    发布后题目与总分不再可变，只允许调整时间窗。这是保证历史成绩可追溯的核心约束。
14. **状态机单一路径**：考试只能 `DRAFT → PUBLISHED → FINISHED`，
    每个写操作都在 Service 内校验当前状态，非法流转一律 5xxxx 业务错误。
15. **学生端 DTO 永不带答案**：作答题目用独立的 `PaperQuestionResponse`，
    不含 `answer` / `analysis`。若直接返回实体，学生打开 F12 就能看到正确答案。
16. **判分只读快照**：判分从 `exam_question` 取标准答案，绝不回查题库；
    判分逻辑抽成无框架依赖的 `ObjectiveGrader` 纯函数，便于逐条单测。
17. **权威计时在服务端**：截止时间由服务端按「开始作答 + 时长」与「考试结束时间」取较早者计算，
    每次保存与交卷都重新校验；前端倒计时仅用于显示。
18. **统计口径必须写清楚**：掌握度按**分值加权**（Σ答对题分值 / Σ该知识点全部题分值），
    不按题数平均；所有判断类结论在样本不足时标注「仅供参考」，不硬给结论。
19. **判断题给依据**：每个"偏难/偏易"都同时给出得分率与平均耗时，
    便于教师复核——只有一个标签而没有数据的判断，教师无法验证，也无法据此行动。

---

## 学情分析的设计取舍

本项目刻意**没有**做心理测量学意义上的「区分度」：

- 区分度需要足够大的样本（通常 ≥ 30 人且分数呈正态分布）才有意义，
  而课程项目的演示数据量远远不够，算出来的数字不可信。
- 取而代之的是**「题目难度 × 班级水平贴合度」**：把某题得分率与班级平均得分率比较，
  偏离超过 25% 就提示复核。判据简单、可解释、教师能直接验证。
- 并且**与作答耗时交叉判断**：得分率低且耗时很短 → 学生可能直接放弃或题干表述不清；
  得分率低但耗时长 → 属真实难点，不必改题。单看耗时没有意义，必须交叉。

样本门槛已实现：已交卷人数 < 30 时，看板顶部提示样本不足，每题判断标注「样本不足，仅供参考」。

---

## 演示数据

```bash
# 生成学情分析用的演示作答（student1 答对 70%、student2 答对 40%）
node docs/scripts/seed-demo-attempts.mjs
```

脚本通过接口造数据，可重复执行（已有作答的学生会跳过）。生成后到
「考试管理 → 学情分析」即可看到分数段分布、知识点掌握度差异与题目难度判断。

> 这是**演示数据**，仅用于展示看板效果，不代表真实学情。

---

## 收尾完善与增强功能

除六个阶段外，还补齐了日常使用必需的能力：

| 功能 | 入口 | 说明 |
|---|---|---|
| 自助改密 | 顶栏右上角菜单 →「修改密码」 | 教师与学生共用。改密后 `tokenVersion + 1`，当前登录状态立即失效，需用新密码重新登录 |
| 成绩单导出 | 考试管理 → 学情分析 →「导出成绩单」 | 生成 .xlsx：标题、概要（总分/及格线/人数/平均分/及格率）、表头冻结、学生明细；可按班级导出 |
| 题库批量导入 | 题库管理 →「Excel 导入」 | 支持四种题型；先下载模板；**逐行容错**，失败行返回行号与原因，其余行照常入库 |
| 多轮考试 | 组卷时设置「限考次数」 | 学生端显示「已用 / 限考」，支持下「再考一次」；每轮独立记录与成绩 |
| 题干配图 | 题目表单 →「上传图片」 | 支持 png/jpg/gif/webp，≤2MB；组卷时随题干一并快照，历史试卷不受题库改图影响 |
| 学生端班级筛选 | 学情分析 → 班级下拉 | 同一次考试发布给多个班时，可只看某个班的统计并单独导出 |
| 操作日志 | 侧边菜单 →「操作日志」 | 自动记录成功的写操作（谁、何时、对什么对象）；可按动作/关键字/时间筛选，日志只读不可改 |

Excel 相关实现要点见 [docs/DEV-NOTES.md](docs/DEV-NOTES.md) 第十三节，
多轮考试、配图、日志的设计取舍见第十四节。

---

## 下一步

详见 [docs/ROADMAP.md](docs/ROADMAP.md)。当前进度：**全部完成**。

可选的后续扩展（不影响交付）：

- 多套卷（`exam_question.attempt_no` 已预留，目前补考复用同一份快照以保证难度一致）
- 成绩单按分数段/关键字筛选后导出
- 操作日志导出与保留策略（`operation_log` 会持续增长，生产环境需定期归档）
- 图片题干的裁剪压缩（当前限制 2MB，未做服务端缩放）
