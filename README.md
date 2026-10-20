# 学生在线考试系统

基于 Spring Boot 4 + Vue 3 的在线考试系统。面向大学软件工程综合实践，功能聚焦"客观题考试"闭环，不涉及 AI 判分等复杂技术。

**当前进度：阶段 0（工程骨架）、阶段 1（认证与权限）、阶段 2（用户与班级管理）、阶段 3（题库与知识点管理）已完成并验证通过。**
后端 59 个集成测试全绿，端到端检查 41 项全通过。

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
# 后端集成测试（59 个用例，覆盖认证、权限、数据隔离、题库校验、业务规则）
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

---

## 已实现的功能模块

| 模块 | 状态 | 主要接口 |
|---|---|---|
| 认证与权限 | ✅ | `POST /api/auth/login`、`GET /api/auth/me`、`PUT /api/auth/password` |
| 学生管理（教师） | ✅ | `GET/POST/PUT/DELETE /api/students`、`PUT /api/students/{id}/password/reset`、`POST /api/students/import` |
| 班级管理（教师） | ✅ | `GET/POST/PUT/DELETE /api/classes`、`GET /api/classes/options` |
| 知识点管理（教师） | ✅ | `GET/POST/PUT/DELETE /api/knowledge-points`、`GET /api/knowledge-points/options` |
| 题库管理（教师） | ✅ | `GET/POST/PUT/DELETE /api/questions`、`PUT /api/questions/{id}/enabled` |
| 组卷与考试发布 | ⬜ 阶段 4 | — |
| 在线作答与判分 | ⬜ 阶段 5 | — |
| 学情分析（知识点掌握度、难度贴合度） | ⬜ 阶段 6 | — |

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
│   ├── controller/              Auth / User / Student / Class / Question / KnowledgePoint / Course / Health
│   ├── domain/                  12 张表的实体 + 枚举 + JSON 转换器
│   ├── dto/                     请求/响应对象（与实体分离）
│   │   ├── auth/                认证相关
│   │   ├── user/                学生管理相关（查询、新增、修改、导入结果）
│   │   ├── clazz/               班级管理相关
│   │   ├── knowledge/           知识点相关（学情分析的主干）
│   │   └── question/            题库相关（含知识点标签）
│   ├── repository/              11 个 Spring Data JPA 接口（含教师数据范围查询）
│   ├── security/                JWT 生成校验 / 过滤器 / 用户主体 / 401·403 处理器
│   └── service/                 AuthService / UserService / ClassService / QuestionService / KnowledgePointService
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
│   └── QuestionBankApiTest.java 知识点与题库规则
└── frontend/
    ├── vite.config.js           代理 /api → 8080；build 输出到后端 static
    └── src/
        ├── api/                 接口定义（auth / student / class / question）
        ├── layouts/             AdminLayout（侧边菜单 + 顶栏）
        ├── router/              路由 + 登录/角色守卫
        ├── stores/              Pinia 登录态
        ├── utils/               Axios 封装（自动带 token、401 跳登录）
        └── views/               登录页 / 工作台 / 学生管理 / 班级管理 / 404
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

---

## 下一步

详见 [docs/ROADMAP.md](docs/ROADMAP.md)。当前进度：**阶段 0、1、2、3 已完成**。

- **阶段 4** 组卷与发布：手动/随机抽题、分值校验、绑定班级
- **阶段 5** 在线作答与判分（核心）：预填答案、自动保存、断线恢复、服务端权威倒计时、客观题判分，
  **并上报每题作答耗时与修改次数**（阶段 6 交叉分析的输入）
- **阶段 6** 学情分析与收尾：知识点掌握度聚合、薄弱知识点推送、题目难度 × 学生水平贴合度、
  样本量门槛、成绩单与图表、答辩材料

> 不阻塞后续开发的小尾巴：「学生自己修改密码」的前端页面尚未做（后端接口 `PUT /api/auth/password` 已完成）；
> 题库 Excel 批量导入与图片题干上传留作可选扩展。
