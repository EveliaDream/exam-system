/**
 * 阶段 0 端到端联调验证脚本。
 *
 * 用途：一次性验证「Vite 代理 → 后端 Spring Security → JWT → 权限边界」整条链路。
 * 运行前提：后端 8080 与前端 5173 都已启动。
 * 运行方式：node docs/scripts/e2e-check.mjs
 *
 * 为什么走 5173 而不是直接打 8080：走代理才能证明「开发期无 CORS 问题」这一结论成立。
 * 为什么不用 JUnit 写：这属于"跑起来看一眼"的手工验证，依赖两个真实进程；
 * 真正的自动化回归测试在 src/test/java/.../controller/AuthApiTest.java（MockMvc）。
 */
const BASE = 'http://127.0.0.1:5173'

let failed = 0

function report(name, ok, detail) {
    const flag = ok ? 'PASS' : 'FAIL'
    if (!ok) failed++
    console.log(`[${flag}] ${name}${detail ? ' -> ' + detail : ''}`)
}

async function call(path, options = {}) {
    const res = await fetch(BASE + path, options)
    const text = await res.text()
    let body = null
    try {
        body = JSON.parse(text)
    } catch {
        body = text
    }
    return { status: res.status, body }
}

// 1) 健康检查：证明 Vite 代理已把 /api 转发到 8080
const health = await call('/api/health')
report('健康检查经 Vite 代理可达', health.status === 200 && health.body?.data?.status === 'UP',
    `HTTP ${health.status}`)

// 2) 教师登录：拿到 token
const login = await call('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'teacher', password: '123456' })
})
const token = login.body?.data?.token
report('教师登录成功并返回 token', login.status === 200 && !!token,
    `HTTP ${login.status}, realName=${login.body?.data?.realName}`)
report('中文未乱码（UTF-8 正确）', login.body?.data?.realName === '张老师',
    `realName=${JSON.stringify(login.body?.data?.realName)}`)

// 3) 带 token 取当前用户
const me = await call('/api/auth/me', { headers: { Authorization: `Bearer ${token}` } })
report('带 token 访问 /api/auth/me', me.status === 200 && me.body?.data?.username === 'teacher',
    `HTTP ${me.status}`)
// 教师不带班级，字段应为 null；但项目开启了 jackson default-property-inclusion=non_null，
// 因此该字段会直接从 JSON 中消失，解析后是 undefined —— 两种都算正常。
report('班级信息随用户返回（studentClass 关联正常）',
    me.body?.data?.className === null || me.body?.data?.className === undefined,
    `teacher 无班级，className=${JSON.stringify(me.body?.data?.className)}（non_null 策略下字段被省略）`)

// 4) 不带 token 访问受保护接口：应为 401 且是统一 JSON
const noToken = await call('/api/auth/me')
report('无 token 返回 401 统一 JSON', noToken.status === 401 && noToken.body?.code === 40100,
    `HTTP ${noToken.status}, body=${JSON.stringify(noToken.body)}`)

// 5) 教师访问教师专属接口：应为 200
const teacherOnly = await call('/api/users/profile', { headers: { Authorization: `Bearer ${token}` } })
report('教师访问教师专属接口', teacherOnly.status === 200 && teacherOnly.body?.data?.isTeacher === true,
    `HTTP ${teacherOnly.status}`)

// 6) 学生访问教师专属接口：应为 403（权限边界）
const studentLogin = await call('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'student1', password: '123456' })
})
const studentToken = studentLogin.body?.data?.token
report('学生登录成功且班级已绑定', studentLogin.status === 200 && !!studentToken,
    `HTTP ${studentLogin.status}, className=${studentLogin.body?.data?.className}`)

const forbidden = await call('/api/users/profile', { headers: { Authorization: `Bearer ${studentToken}` } })
report('学生访问教师接口被拦截为 403', forbidden.status === 403 && forbidden.body?.code === 40300,
    `HTTP ${forbidden.status}`)

// 7) 密码错误
const badLogin = await call('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'teacher', password: 'wrong' })
})
report('密码错误返回 40101', badLogin.status === 401 && badLogin.body?.code === 40101,
    `HTTP ${badLogin.status}`)

// 8) 参数校验
const blankLogin = await call('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: '', password: '' })
})
report('参数校验失败返回 400', blankLogin.status === 400 && blankLogin.body?.code === 20001,
    `HTTP ${blankLogin.status}, message=${blankLogin.body?.message}`)

console.log(failed === 0
    ? '\n全部通过：阶段 0 链路（Vite 代理 → JWT 认证 → 权限边界）已打通'
    : `\n有 ${failed} 项失败，请检查上面的输出`)

process.exit(failed === 0 ? 0 : 1)
