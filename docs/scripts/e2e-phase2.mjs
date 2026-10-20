/**
 * 阶段 2 端到端联调验证脚本：用户与班级管理。
 *
 * 运行前提：后端 8080 与前端 5173 都已启动。
 * 运行方式：node docs/scripts/e2e-phase2.mjs
 *
 * 与 e2e-check.mjs（阶段 0）的区别：
 *   阶段 0 验证"能不能登录 + 权限拦不拦"，本脚本验证"业务规则对不对"。
 *   走 5173 代理，顺带证明前端链路依然同源可用。
 *
 * 数据清理约定：
 *   本脚本会在持久化数据库（MySQL）上创建真实数据，因此**必须自清理**。
 *   做法是本次运行使用统一的标记 RUN_ID，所有新建的用户名与班级名都包含它；
 *   结尾按该标记精确回收，跑完后库内数据量与运行前一致。
 *   若中间断言失败导致脚本提前退出，可手工执行：
 *     DELETE FROM sys_user WHERE username LIKE '%<RUN_ID>%';
 *     DELETE FROM student_class WHERE name LIKE '%<RUN_ID>%';
 */
const BASE = 'http://127.0.0.1:5173'

/** 本次运行的唯一标记：6 位时间戳 + 3 位随机，避免与历史运行或他人运行冲突 */
const RUN_ID = `${Date.now().toString().slice(-6)}${Math.floor(Math.random() * 900 + 100)}`

let failed = 0
const results = []

function check(name, ok, detail) {
    if (!ok) failed++
    results.push(`[${ok ? 'PASS' : 'FAIL'}] ${name}${detail ? ' -> ' + detail : ''}`)
}

async function api(path, { method = 'GET', token, body, raw } = {}) {
    const headers = {}
    if (token) headers.Authorization = `Bearer ${token}`
    if (body !== undefined && !raw) headers['Content-Type'] = 'application/json'
    if (raw) headers['Content-Type'] = 'text/plain;charset=UTF-8'

    const res = await fetch(BASE + path, {
        method,
        headers,
        body: body === undefined ? undefined : (raw ? body : JSON.stringify(body))
    })
    const text = await res.text()
    let json = null
    try {
        json = JSON.parse(text)
    } catch {
        json = text
    }
    return { status: res.status, body: json, text }
}

async function login(username, password = '123456') {
    const r = await api('/api/auth/login', {
        method: 'POST',
        body: { username, password }
    })
    return r.body?.data?.token
}

// ============ 登录两个教师 + 一个学生 ============
const teacher = await login('teacher')
const teacher2 = await login('teacher2')
const student = await login('student1')
check('三个账号均可登录（教师 A / 教师 B / 学生）', !!teacher && !!teacher2 && !!student)

// ============ 1. 班级管理 ============
const className = `E2E 测试班 ${RUN_ID}`
const created = await api('/api/classes', {
    method: 'POST', token: teacher, body: { name: className, grade: '2024' }
})
check('新增班级', created.status === 200 && created.body?.data?.name === className,
    `HTTP ${created.status}, studentCount=${created.body?.data?.studentCount}`)
const classId = created.body?.data?.id

const dupClass = await api('/api/classes', {
    method: 'POST', token: teacher, body: { name: className, grade: '2024' }
})
check('班级重名被拒绝（30201）', dupClass.status === 409 && dupClass.body?.code === 30201,
    `HTTP ${dupClass.status}, code=${dupClass.body?.code}`)

const classPage = await api(`/api/classes?page=1&size=10`, { token: teacher })
check('班级分页结构与总数正确',
    classPage.status === 200 && Array.isArray(classPage.body?.data?.list) && classPage.body?.data?.total >= 1,
    `total=${classPage.body?.data?.total}, page=${classPage.body?.data?.page}`)

// ============ 2. 学生管理 ============
// 用户名也带上 RUN_ID，便于结尾按标记精确回收（此前正是因为命名标记不统一导致清理不干净）
const stuUsername = `e2e_main_${RUN_ID}`
const stu = await api('/api/students', {
    method: 'POST', token: teacher,
    body: { username: stuUsername, realName: '端到端测试生', classId }
})
check('新增学生并分班', stu.status === 200 && stu.body?.data?.className === className,
    `HTTP ${stu.status}, className=${stu.body?.data?.className}`)
const studentId = stu.body?.data?.id

const dupStu = await api('/api/students', {
    method: 'POST', token: teacher, body: { username: stuUsername, realName: '重复' }
})
check('学生用户名重复被拒绝（30101）', dupStu.status === 409 && dupStu.body?.code === 30101,
    `HTTP ${dupStu.status}, code=${dupStu.body?.code}`)

const badStu = await api('/api/students', {
    method: 'POST', token: teacher, body: { username: 'a b', realName: '非法' }
})
check('学生参数校验失败（20001）', badStu.status === 400 && badStu.body?.code === 20001,
    `HTTP ${badStu.status}, message=${badStu.body?.message}`)

const stuPage = await api(`/api/students?page=1&size=10&classId=${classId}`, { token: teacher })
check('按班级筛选学生', stuPage.status === 200
    && stuPage.body?.data?.list?.every(r => r.classId === classId),
    `total=${stuPage.body?.data?.total}`)

const kwPage = await api(`/api/students?keyword=${stuUsername}`, { token: teacher })
check('按关键词搜索学生命中唯一记录',
    kwPage.body?.data?.total === 1 && kwPage.body?.data?.list?.[0]?.username === stuUsername,
    `total=${kwPage.body?.data?.total}`)
// 关键安全断言：响应体不能出现密码或 token 版本
const raw = JSON.stringify(stuPage.body)
check('学生列表不泄露密码等敏感字段',
    !raw.includes('password') && !raw.includes('tokenVersion') && !raw.includes('$2a$'),
    `响应长度 ${raw.length}`)

// 注意：这里先"只改姓名、保留班级"。
// 取消分班与数据隔离断言的先后顺序很关键 —— 见第 3 节开头的说明。
const renamed = await api(`/api/students/${studentId}`, {
    method: 'PUT', token: teacher,
    body: { username: stuUsername, realName: '改名后的测试生', classId, enabled: true }
})
check('编辑学生姓名（保留班级）',
    renamed.status === 200 && renamed.body?.data?.realName === '改名后的测试生'
    && renamed.body?.data?.classId === classId,
    `classId=${renamed.body?.data?.classId}`)

// 教师本人保持用户名不变保存，不应误报"用户名已存在"
const sameName = await api(`/api/students/${studentId}`, {
    method: 'PUT', token: teacher,
    body: { username: stuUsername, realName: '改名后的测试生', classId, enabled: true }
})
check('保持用户名不变保存不误报重复', sameName.status === 200 && sameName.body?.code === 0,
    `HTTP ${sameName.status}`)

// ============ 3. 数据隔离（本阶段核心） ============
// 注意：这些断言必须在该学生"仍在班级内"时执行。
// 学生一旦被取消分班，就会被视为公共待分配资源，任何教师都可编辑，
// 隔离断言自然失效 —— 所以先验证隔离，再做取消分班的操作。
const crossList = await api(`/api/students?classId=${classId}`, { token: teacher2 })
check('教师 B 看不到教师 A 班级的学生（数据隔离）', crossList.body?.data?.total === 0,
    `teacher2 可见 total=${crossList.body?.data?.total}`)

const crossUpdate = await api(`/api/students/${studentId}`, {
    method: 'PUT', token: teacher2,
    body: { username: stuUsername, realName: '越权篡改' }
})
check('教师 B 不能修改教师 A 班级的学生（30203）',
    crossUpdate.status === 403 && crossUpdate.body?.code === 30203,
    `HTTP ${crossUpdate.status}, code=${crossUpdate.body?.code}`)

const crossReset = await api(`/api/students/${studentId}/password/reset`, {
    method: 'PUT', token: teacher2
})
check('教师 B 不能重置教师 A 班级学生的密码（30203）',
    crossReset.status === 403 && crossReset.body?.code === 30203,
    `HTTP ${crossReset.status}`)

const crossDelete = await api(`/api/students/${studentId}`, { method: 'DELETE', token: teacher2 })
check('教师 B 不能删除教师 A 班级的学生（30203）',
    crossDelete.status === 403 && crossDelete.body?.code === 30203,
    `HTTP ${crossDelete.status}`)

const crossAssign = await api('/api/students', {
    method: 'POST', token: teacher2,
    body: { username: `e2e_cross_${RUN_ID}`, realName: '非法分班', classId }
})
check('教师 B 不能把学生分到教师 A 的班级（30203）',
    crossAssign.status === 403 && crossAssign.body?.code === 30203,
    `HTTP ${crossAssign.status}`)

// ============ 4. 权限边界 ============
const stuAccess = await api('/api/students', { token: student })
check('学生访问学生管理接口被拦截（403）', stuAccess.status === 403 && stuAccess.body?.code === 40300,
    `HTTP ${stuAccess.status}`)

const anonAccess = await api('/api/students')
check('未登录访问返回 401', anonAccess.status === 401 && anonAccess.body?.code === 40100,
    `HTTP ${anonAccess.status}`)

// ============ 5. 班级删除保护 ============
const delClassBusy = await api(`/api/classes/${classId}`, { method: 'DELETE', token: teacher })
check('班级内仍有学生时禁止删除（30202）',
    delClassBusy.status === 409 && delClassBusy.body?.code === 30202,
    `HTTP ${delClassBusy.status}, message=${delClassBusy.body?.message}`)

// ============ 6. 编辑资料：取消分班 ============
const updated = await api(`/api/students/${studentId}`, {
    method: 'PUT', token: teacher,
    body: { username: stuUsername, realName: '改名后的测试生', classId: null, enabled: true }
})
check('编辑学生并取消分班',
    updated.status === 200 && updated.body?.data?.realName === '改名后的测试生'
    && (updated.body?.data?.className === undefined || updated.body?.data?.className === null),
    `className=${JSON.stringify(updated.body?.data?.className)}`)

// ============ 7. 删除学生与班级 ============
const delStu = await api(`/api/students/${studentId}`, { method: 'DELETE', token: teacher })
check('删除学生（逻辑删除）', delStu.status === 200, `HTTP ${delStu.status}`)

const afterDelete = await api(`/api/students/${studentId}`, { token: teacher })
check('删除后查询返回 30100', afterDelete.status === 404 && afterDelete.body?.code === 30100,
    `HTTP ${afterDelete.status}`)

const delClass = await api(`/api/classes/${classId}`, { method: 'DELETE', token: teacher })
check('清空学生后班级可删除', delClass.status === 200, `HTTP ${delClass.status}`)

// ============ 8. 批量导入 ============
const imp1 = `e2e_imp_a_${RUN_ID}`
const imp2 = `e2e_imp_b_${RUN_ID}`
const impClass = `E2E 导入班 ${RUN_ID}`
const importBody = [
    `${imp1},导入甲,2024,${impClass}`,
    `bad name,非法用户名`,
    `${imp1},重复用户名`,
    `${imp2},导入乙`
].join('\n')
const imported = await api('/api/students/import', {
    method: 'POST', token: teacher, body: importBody, raw: true
})
check('批量导入逐行处理（2 成功 2 失败）',
    imported.status === 200 && imported.body?.data?.total === 4
    && imported.body?.data?.success === 2 && imported.body?.data?.failed === 2,
    `total=${imported.body?.data?.total}, success=${imported.body?.data?.success}, failed=${imported.body?.data?.failed}`)

const impCheck = await api(`/api/students?keyword=${imp1}`, { token: teacher })
check('导入的学生自动创建并绑定班级',
    impCheck.body?.data?.total === 1
    && impCheck.body?.data?.list?.[0]?.className === impClass,
    `className=${impCheck.body?.data?.list?.[0]?.className}`)
// ============ 9. 回收本次运行产生的数据 ============
// 脚本跑在持久化的 MySQL 上，不清理会不断累积脏数据。
// 本次运行创建的学生用户名与班级名都带 RUN_ID，因此按标记精确回收；
// 先删学生（使班级变为空班），再删班级。
//
// 注意两个容易出错的点：
//   1. 删除是"逻辑删除"：被删行仍留在表里、只是 deleted=1，所以不能用表行数判断基线，
//      只能用接口返回的数据量判断。
//   2. 班级删除会因班内仍有学生而失败（409），**必须检查返回状态**，
//      否则会静默留下一个空班级（曾经就是这么漏掉的）。
async function cleanup() {
    const problems = []
    let studentCount = 0
    let classCount = 0

    const stuRes = await api('/api/students?page=1&size=100', { token: teacher })
    for (const row of (stuRes.body?.data?.list || [])) {
        if ((row.username || '').includes(RUN_ID)) {
            const r = await api(`/api/students/${row.id}`, { method: 'DELETE', token: teacher })
            if (r.status === 200) {
                studentCount++
            } else {
                problems.push(`删除学生 ${row.username} 失败：HTTP ${r.status} ${r.body?.message ?? ''}`)
            }
        }
    }

    const classRes = await api('/api/classes?page=1&size=100', { token: teacher })
    for (const c of (classRes.body?.data?.list || [])) {
        if ((c.name || '').includes(RUN_ID)) {
            const r = await api(`/api/classes/${c.id}`, { method: 'DELETE', token: teacher })
            if (r.status === 200) {
                classCount++
            } else {
                problems.push(`删除班级 ${c.name} 失败：HTTP ${r.status} ${r.body?.message ?? ''}`)
            }
        }
    }
    return { studentCount, classCount, problems }
}

const cleaned = await cleanup()
results.push(`[INFO] 已回收本次运行产生的数据：学生 ${cleaned.studentCount} 条、班级 ${cleaned.classCount} 个`)
check('回收过程无失败（含班级删除保护不该触发）',
    cleaned.problems.length === 0,
    cleaned.problems.join('；') || 'ok')

// 回收校验：接口层面不应再查到带 RUN_ID 的学生或班级
// （用接口而不是 SQL 计数：逻辑删除的记录仍在表里，SQL 计数无法反映真实可见数据）
const leftoverStu = await api(`/api/students?page=1&size=100&keyword=${RUN_ID}`, { token: teacher })
check('运行结束后无残留测试学生',
    (leftoverStu.body?.data?.total ?? -1) === 0,
    `残留 total=${leftoverStu.body?.data?.total}`)

const leftoverClass = await api(`/api/classes?page=1&size=100&keyword=${RUN_ID}`, { token: teacher })
check('运行结束后无残留测试班级',
    (leftoverClass.body?.data?.total ?? -1) === 0,
    `残留 total=${leftoverClass.body?.data?.total}`)

console.log(results.join('\n'))
console.log(failed === 0
    ? `\n全部通过（${results.length} 项）：阶段 2 用户与班级管理链路已打通`
    : `\n有 ${failed} 项失败，请检查上面的输出`)

process.exit(failed === 0 ? 0 : 1)
