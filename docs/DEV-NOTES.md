# 开发笔记：踩坑记录与技术决策

本文件记录开发过程中实际遇到的问题、解决办法，以及需要提前知道的决策背景。
**答辩时如果被问到"遇到过什么困难"，这里基本可以直接当素材。**

---

## 一、Spring Boot 4 与 3.x 的差异（最容易踩）

### 1. starter 已改名为按模块拆分

| 用途 | Boot 3.x 写法 | Boot 4.x 写法（本项目） |
|---|---|---|
| Web MVC | `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
| JSON | 随 web 传递引入 | 需显式 `spring-boot-starter-jackson` |
| 测试 | `spring-boot-starter-test` 一把梭 | `spring-boot-starter-test` + 各模块的 `-test` |

Boot 4 起测试 starter 按模块拆分为 `spring-boot-starter-webmvc-test`、
`spring-boot-starter-data-jpa-test`、`spring-boot-starter-security-test` 等，
写测试时**必须把对应模块的 `-test` 依赖加进 pom**，否则 `@AutoConfigureMockMvc` 等注解找不到。

`@AutoConfigureMockMvc` 的包名也变了：

```java
// Boot 3.x
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
// Boot 4.x
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
```

> 结论：本项目的依赖坐标不要照着网上 3.x 的教程抄，会直接编译失败。

### 2. Jackson 已升级到 Jackson 3（包名从 `com.fasterxml` 变成 `tools.jackson`）

这是阶段 0 耗时最多的一个坑。现象是：

```
找不到符号: 类 ObjectMapper
位置: 程序包 com.fasterxml.jackson.databind
```

原因：Boot 4 默认使用 Jackson 3，其坐标与包名都变了：

| | Jackson 2 | Jackson 3 |
|---|---|---|
| Maven 坐标 | `com.fasterxml.jackson.core:jackson-databind` | `tools.jackson.core:jackson-databind` |
| 包名 | `com.fasterxml.jackson.databind` | `tools.jackson.databind` |
| java.time 支持 | 需额外引 `jackson-datatype-jsr310` | **已内置**在 databind 中，无需额外依赖 |
| JsonNode 取字符串 | `asText()` | `asString()` |

项目里还间接存在 Jackson 2 —— `jjwt-jackson` 会传递引入它（runtime 作用域，只给 jjwt 自己用）。
所以看到依赖树里同时有 `com.fasterxml` 和 `tools.jackson` **是正常的**，不要试图统一。

日期格式化也不能只靠 `spring.jackson.date-format`（那只对 `java.util.Date` 生效），
本项目用 `JacksonConfig` 显式注册 `LocalDateTime` 的格式化器。参考文件：
`src/main/java/com/finalproject/examsystem/config/JacksonConfig.java`

### 3. `UserDetails` 与 record 的签名不匹配

用 record 实现 `UserDetails` 时会报"未覆盖抽象方法 `getUsername()`"：
record 自动生成的访问器是 `username()` / `password()`，而接口要求 `getUsername()` / `getPassword()`，
**两者不会自动互相满足**，必须显式实现。见 `UserPrincipal.java`。

---

## 二、前后端分离的相关决策

### 1. 用 Vite 代理解决跨域，而不是 `@CrossOrigin`

`frontend/vite.config.js` 把 `/api` 代理到 `http://localhost:8080`，
浏览器视角所有请求同源，因此：

- 后端**不需要**任何 CORS 配置（本项目的 `SecurityConfig` 里是 `cors(cors -> cors.disable())`）
- 不需要处理 `allowCredentials` 与 `origins("*")` 冲突这类问题

打包交付时前端产物输出到后端 `src/main/resources/static`，同源部署，依然没有跨域。

### 2. 401/403 必须返回 JSON，不能是 HTML

若不定制，Spring Security 在未登录时会重定向到它自带的 HTML 登录页，
前端 Axios 解析响应就会报"格式错误"。本项目通过 `RestAuthenticationEntryPoint`
与 `RestAccessDeniedHandler` 保证 401/403 也返回统一的 `{ code, message, data }`。

### 3. 「登出」在无状态 JWT 下无法真正吊销 token

删除 Redis 后没有黑名单可用，所以：

- 点"登出"只是前端丢弃本地 token，**旧 token 在过期前仍可继续使用**
- 为此在 `sys_user` 上加了 `token_version` 字段：**改密码时 +1**，
  过滤器每次校验 token 里的 `tv` 与库中是否一致，不一致即视为失效

这是可接受的工程取舍，**答辩时应主动说明**，而不是等老师问。
如果老师追问"如何做到服务端立即失效"，答案就是 `token_version`，或引入 Redis 做黑名单。

### 4. 密码错误与用户不存在返回同一提示

`AuthService` 对两种情况都返回 `40101 用户名或密码错误`，避免账号枚举攻击。

---

## 三、数据模型的关键决策

### 1. `question` 单表多题型

四种题型（单选/多选/判断/填空）共用一张表，用 `type` 区分，
`options` 与 `answer` 以 JSON 字符串存 Text 列（`StringListConverter`）。

如果为每种题型建一张表，判分逻辑与统计查询需要写四套分支与 JOIN，维护成本会迅速失控。

### 2. `exam_question` 是快照表（**本项目最重要的设计点**）

组卷时把题干、选项、正确答案、分值**各复制一份**存进 `exam_question`。

原因：如果判分时回查题库，那么教师后续修改题目会导致**已经考完的考试成绩发生变化**。
这是这类系统最容易被答辩抓到的逻辑漏洞。

### 3. 试卷实例化时机

学生**点击"开始考试"时**才创建 `exam_record` 并预填 `answer` 行，
而不是发布考试时给全班一次性生成，避免数据量无谓膨胀。

自动保存直接 UPDATE 这些行，因此断线重进可以恢复已答内容，**不需要额外中间件**。

### 4. 权威倒计时在服务端

`deadline = min(开始作答时间 + duration, exam.end_time)` 由服务端计算并每次校验，
前端倒计时只是显示。否则学生可以用 Postman 绕过前端超时提交。

### 5. 填空题判分不用 AI

标准化处理后与 `answer` 数组逐一比对即可：

```
去首尾空格 → 去全部空白字符 → 统一大小写 → 全角转半角 → 命中任一备选答案即得分
```

### 6. 表名 `sys_user` 而不是 `user`

`user` 在 MySQL 8 与 H2 中都是保留字，直接建表会报错。
同理，JPQL 里引用实体要写全限定名，否则容易被误认为表名。

---

## 四、环境与工具链（本机特有）

这几条是为了在当前环境下把工程跑起来所做的处理，**换机器时可能需要调整**。

### 1. Maven 本地仓库被放到工作区内

`.mvn/maven.config` 里设置了：

```
-Dmaven.repo.local=D:/exam-system/.tools/m2/repository
```

背景：当前开发环境为受限沙箱，Maven 默认写 `C:\Users\<用户>\.m2\repository` 会被拒绝，
新依赖（如 jjwt）无法落盘。指向工作区后构建完全自包含。

换机器时删掉 `.tools/` 目录即可回退到默认的 `~/.m2/repository`。

### 2. npm 相关的两个坑

**(a) `EALLOWSCRIPTS`**：本机环境预设了环境变量 `npm_config_allow_scripts`，
npm 11 在"项目内安装"时会把 env 层视为 CLI 层而直接拒绝：

```
npm error code EALLOWSCRIPTS
npm error --allow-scripts is not allowed in project-scoped installs.
```

解决办法是在安装前清掉该变量（`.npmrc` 的覆盖无效，因为 npm 把它归到 CLI/env 层）：

```powershell
$env:npm_config_allow_scripts = $null
cd frontend
npm install
```

注意此时会提示 `esbuild`/`vue-demi` 的 postinstall 脚本未执行 —— 实测**不影响使用**
（`@esbuild/win32-x64` 平台包已随 optionalDependencies 装好，`vite --version` 正常）。

**(b) `vite` 需要派生子进程**：受限沙箱下 `vite dev` 会报 `spawn EPERM`
（esbuild 必须启动子服务进程来转换代码）。在正常环境下不会有这个问题。

### 3. `spring-boot:stop` 在未引入 actuator 时不会真正停止应用

它只是尝试用 JMX/actuator 端点停机，本项目没引 actuator，所以该命令无效，
需要手动结束进程（或直接 Ctrl+C 停掉 `spring-boot:run`）。

---

## 五、写测试时的一个必踩坑：`non_null` 让字段"消失"

项目配置了 `spring.jackson.default-property-inclusion=non_null`，值为 null 的字段**不会出现在 JSON 里**。
写断言时如果用 Jackson 的 `get()` 取值，拿到的是 `null`，紧接着调用 `.isNull()` 就会 NPE：

```
NullPointer Cannot invoke "JsonNode.isNull()" because the return value of "JsonNode.get(String)" is null
```

正确写法是用 `path()`（字段不存在时返回 MissingNode）并用一个语义明确的辅助方法：

```java
protected boolean isAbsentOrNull(JsonNode node, String field) {
    return node.path(field).isMissingNode() || node.path(field).isNull();
}
```

这个坑在阶段 0 与阶段 2 各踩了一次。**根因是想当然地认为"字段值为 null"就等于"JSON 里有这个字段且值为 null"**。
前端同理：判断"学生未分班"要用 `row.className` 是否为 truthy，而不是 `=== null`。

另外 `MockMvc` 的 `getContentAsString()` 默认按 ISO-8859-1 解码，读取含中文的响应必须显式传 UTF-8：

```java
result.getResponse().getContentAsString(StandardCharsets.UTF_8)
```

---

## 六、阶段 2 的关键设计决策：权限边界要做两层

阶段 2 只做"学生管理"看起来简单，但真正的难点是**教师之间的数据隔离**。

只加 `@PreAuthorize("hasRole('TEACHER')")` 是不够的：它只能拦住学生，
教师 A 依然可以改教师 B 名下的学生。因此本项目把边界做成两层：

1. **接口层**：`@PreAuthorize` 控制"哪个角色能访问这个模块"。
2. **数据层**：在 JPQL 里用 `left join c.teacher t ... and (c is null or t.id = :teacherId)`
   把查询范围限定在当前教师名下，并在 UPDATE / DELETE 前用 `requireClassOwnedByCurrentTeacher()` 二次校验。

两个容易忽略的细节：

- **隔离条件必须写进查询，不能查完再过滤**。否则 `total` 会把别的教师的数据算进去，前端页码与总数对不上。
- **未分班学生要单独定义归属**。本项目把"没有班级"的学生视为公共待分配资源（任何教师可编辑），
  否则教师将无法给新建的账号分班。这条规则在测试里被显式覆盖
  （见 `DataScopeApiTest.unassignedStudentIsManageable`）。

还有一个业务上的保护：**已有考试作答记录的学生不允许删除**，只能停用。
否则成绩单里会出现指向不存在学生的孤儿记录，成绩争议时无法追溯。

### 端到端脚本的顺序陷阱

`e2e-phase2.mjs` 第一次运行时有 3 项失败，但**代码是对的，是脚本的步骤顺序有问题**：
脚本先执行了"取消分班"，导致后续"教师 B 越权修改该生"必然成功（未分班学生任何教师可编辑），
"班级有学生不能删"也自然落空。

教训：**验证数据隔离类规则时，"改变归属状态"的操作必须放在隔离断言之后**。
Java 测试里这些是相互独立的用例，所以没有暴露这个问题 —— 这也说明脚本式端到端验证有独立价值。

---

## 七、已验证与未验证的部分

**已验证**：

- **46 个后端测试全部通过**：`./mvnw test`（测试固定跑在 h2 profile 上）
  - 阶段 0/1：`AuthApiTest` 8 个
  - 阶段 2：`StudentAdminApiTest` 18 个、`ClassAdminApiTest` 11 个、`DataScopeApiTest` 8 个
- **端到端检查**：`node docs/scripts/e2e-check.mjs` 11 项；`node docs/scripts/e2e-phase2.mjs` 26 项
- **前端构建通过**：`vite build` 成功，各页面按路由拆分为独立 chunk
- H2 下自动建表 + 种子数据写入正常
- 登录/401/403/参数校验/越权拦截全部符合预期
- **MySQL 8.0.46 全链路验证通过**（账号 `root` / 密码 `admin`）：
  - `docs/sql/schema.sql` 执行成功，10 张表、9 个外键、utf8mb4 字符集
  - 表注释与列注释中的中文正确无乱码
  - `ddl-auto=validate` 下应用正常启动 —— 说明手写 SQL 与 JPA 实体定义**完全一致**
  - 登录链路全部正常，数据真实落库（BCrypt `$2a$10$` 密文、班级外键关联生效）
  - 中文题干以 JSON 存 Text 列后读回无乱码，例如 `["正确","错误"]`
  - **阶段 2 全部接口已在 MySQL 下回归**：两个端到端脚本共 37 项全部通过

**未验证**：

- 后端 `static` 目录部署形态（前端构建产物已确认可生成，但未在 jar 中实测访问）

---

## 八、把默认数据库从 H2 改成 MySQL 时的两个坑

### 1. 测试必须显式指定 profile

测试会大量增删数据（学生、班级），**绝不能跑在开发用的 MySQL 上**，
否则会把演示数据搅乱。因此 `BaseApiTest` 与 `AuthApiTest` 都标注了：

```java
@ActiveProfiles("h2")   // 固定用内存库，与 application.yml 的默认 profile 解耦
```

两个测试类的 profile 必须保持一致，否则 Spring 会创建两个不同的上下文、启动两次，
整体测试时间翻倍（46 个用例从 ~20 秒变成 ~70 秒）。

### 2. 种子数据的幂等判断是"整表为空"，不是"逐条存在"

`DevDataSeeder` 的逻辑是：

```java
if (userRepository.count() > 0) {
    log.info("已存在用户数据，跳过种子数据初始化");
    return;   // 整体跳过
}
```

切到 MySQL 后踩了一次：库里还留着**阶段 0 时期**写入的 3 个账号（`teacher`/`student1`/`student2`），
而阶段 2 新增的 `teacher2`、`student3` 并不存在。由于表非空，种子数据整体跳过，
结果 `e2e-phase2.mjs` 因 `teacher2` 登录失败而级联出 5 项 401 失败。

**判断方法**：切换数据库或新增了种子账号后，先确认账号齐全：

```sql
SELECT username FROM exam_system.sys_user ORDER BY id;
```

**处理办法**：重新建表让种子数据完整写入（会清空数据）：

```bash
mysql -uroot -p < docs/sql/schema.sql   # 脚本内含 DROP TABLE
./mvnw spring-boot:run
```

> 这个坑的教训是：**"幂等"不等于"自愈"**。表非空就整体跳过，实现简单但无法补齐缺失的种子数据。
> 若后续需要更健壮的初始化，可改成"逐条判断存在性"（upsert），代价是逻辑变复杂。

### 顺带记录：Windows PowerShell 执行 SQL 脚本的编码坑

用管道把脚本喂给 mysql 客户端会踩坑：

```powershell
# ✗ 错误做法：PowerShell 按 GBK 重编码管道文本，中文注释变乱码
Get-Content docs/sql/schema.sql -Raw | & mysql -uroot -padmin
# 报错：ERROR 1064 (42000) at line 43: ... near 'BCrypt 瀵嗘枃'
```

正确做法是重定向或加字符集参数：

```bash
mysql -uroot -p --default-character-set=utf8mb4 < docs/sql/schema.sql
```

（这与数据库本身无关，纯属 shell 管道编码问题；排查时容易被误导去怀疑建库字符集。）

---

## 九、阶段 3 的设计转折：为什么把「知识点」提前做进来

### 背景：学情分析的口径决定了数据模型

最初的设计里，学情分析是阶段 6 的一个附属模块，题目上只需要一个「难度」字段。
后来把分析口径确定为：

> **学生的知识点掌握程度 = 其作答题目所覆盖知识点的正确率**

这个口径带来一个**不可逆的顺序依赖**：历史作答记录只有在产生时就关联了知识点，才能被统计。
如果阶段 5 已经跑出几千条作答、阶段 6 才补知识点，那批数据将无法参与任何分析。
因此知识点必须在阶段 3（题库）就落地。

### 三个由此确定的设计决策

**1. 知识点是独立的一等实体，不是题干上的文本标签。**
需要可枚举、可关联、可统计，且同一课程内名称唯一——否则统计时会分裂成两个「同名但不同 id」的知识点，
掌握度被拆成两半。

**2. 题目与知识点用多对多关联表，不用题目表上的单字段。**
综合题往往同时考 2~3 个知识点，压缩成一个字段会丢失信息。
用独立关联实体（`QuestionKnowledge`）而非 `@ManyToMany`，是为了能直接写 JPQL 做聚合统计。

**3. 被引用的知识点禁止删除，只允许停用。**
删除会让历史作答的知识点关联失效，学情统计出现「总数对不上」的空洞。
同理，删除题目时要**同时解除知识点关联**，否则该知识点的「被引用题数」会包含已删除的题，
导致它既删不掉、统计口径也不准。

### 「区分度」被「贴合度」替代

原计划在阶段 6 计算心理学指标「区分度 D」（高低分组得分率之差）。实际评估后放弃，原因：

- 区分度需要按总分排序取高低分组，**班级只有 30~50 人时每组不足 8 人，D 值抖动极大**；
- 给教师的结论如果是「区分度 −0.03」，既难解释也难以行动。

替代方案是教师每天都会问的问题——**这套卷子对这帮学生来说，太难了还是太简单？**
用「题目平均得分率」与「班级平均得分率」做相对比较，输出「偏难 / 贴合 / 偏易」，
并提示是否复核题目。样本量不足时标注「数据不足，仅供参考」。

同时保留了**耗时交叉判断**：单看得分率会误判——得分率低但耗时也短，说明学生直接放弃，
可能是题目超纲或表述不清；得分率低但耗时长，才是真实难点，不必改题。

### 作答耗时字段的两个实现要点

- 耗时用 `answer.time_spent_sec` 存，**必须由前端用「离开该题时间戳 − 进入该题时间戳」计算**。
  不要用 `setInterval` 累加：浏览器会把后台标签页的定时器降频，导致耗时严重偏小。
- 另存 `first_answered_at`，作为客户端数据缺失时服务端兜底估算的依据。

这两个字段在阶段 3 只建表，真正写入在阶段 5（答题页）。
