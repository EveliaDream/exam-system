#!/usr/bin/env node
/**
 * 生成学情分析用的演示作答数据。
 *
 * 为什么需要这个脚本：学情分析要看到「分数段分布」「知识点掌握度差异」「题目难度判断」，
 * 至少需要几个不同水平的学生作答才有意义。手工在界面上点很多次不现实，
 * 这里用接口幂等地造数据，可重复执行（已作答的学生会跳过）。
 *
 * 用法：
 *   node docs/scripts/seed-demo-attempts.mjs [examId]
 *
 * 默认对「Java 程序设计 期中测验（示例）」生成 2 份不同水平的作答：
 *   student1  答对前 70% 的题（中等）
 *   student2  答对前 40% 的题（偏弱）
 *
 * 注意：这是**演示数据**，仅用于本地演示看板效果，不代表真实学情。
 */

const BASE = process.env.API_BASE || 'http://127.0.0.1:5173'
const PASSWORD = '123456'

/** 各演示学生的正确率（按题目顺序取前 N 题答对） */
const PROFILES = [
    { username: 'student1', correctRatio: 0.7, timePerQuestion: 45 },
    { username: 'student2', correctRatio: 0.4, timePerQuestion: 70 }
]

async function api(path, options = {}) {
    const res = await fetch(BASE + path, options)
    const text = await res.text()
    let body
    try {
        body = JSON.parse(text)
    } catch {
        body = text
    }
    if (!res.ok) {
        const err = new Error(`${path} -> HTTP ${res.status}: ${body?.message || text.slice(0, 200)}`)
        err.status = res.status
        err.code = body?.code
        throw err
    }
    return body.data
}

async function login(username) {
    const data = await api('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password: PASSWORD })
    })
    return data.token
}

async function main() {
    const teacherToken = await login('teacher')
    const authHeader = { Authorization: `Bearer ${teacherToken}` }

    const exams = await api('/api/exams?page=1&size=100', { headers: authHeader })
    const exam = process.argv[2]
        ? exams.list.find(e => e.id === Number(process.argv[2]))
        : exams.list.find(e => e.title.includes('示例')) || exams.list[0]

    if (!exam) {
        console.error('找不到可用的考试，请先启动后端并确认已有已发布的考试')
        process.exit(1)
    }

    const detail = await api(`/api/exams/${exam.id}`, { headers: authHeader })
    const questions = detail.questions
    console.log(`目标考试：${exam.title}（${questions.length} 题 / ${detail.totalScore} 分）`)

    for (const profile of PROFILES) {
        const token = await login(profile.username)
        const headers = { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }

        let paper
        try {
            paper = await api(`/api/my/exams/${exam.id}/start`, { method: 'POST', headers })
        } catch (e) {
            console.log(`  ${profile.username}: 跳过（${e.message}）`)
            continue
        }
        if (paper.status !== 'IN_PROGRESS') {
            console.log(`  ${profile.username}: 已有作答记录，跳过`)
            continue
        }

        const correctCount = Math.max(1, Math.round(questions.length * profile.correctRatio))
        const answers = paper.questions.map((q, idx) => {
            const bank = questions.find(x => x.questionId === q.questionId)
            const shouldBeCorrect = idx < correctCount
            let answer
            if (bank && shouldBeCorrect) {
                answer = bank.answer
            } else if (q.type === 'BLANK') {
                answer = ['不太确定']
            } else if (q.type === 'MULTIPLE') {
                answer = ['A']
            } else if (q.type === 'JUDGE') {
                answer = [bank?.answer?.[0] === 'T' ? 'F' : 'T']
            } else {
                // 故意选一个与正确答案不同的选项
                const correct = bank?.answer?.[0]
                answer = [correct === 'A' ? 'B' : 'A']
            }
            return {
                questionId: q.questionId,
                answer,
                timeSpentSec: profile.timePerQuestion + (idx % 3) * 10,
                submitCount: 1 + (idx % 4)
            }
        })

        await api(`/api/my/records/${paper.recordId}/answers`, {
            method: 'PUT',
            headers,
            body: JSON.stringify({ answers })
        })
        const result = await api(`/api/my/records/${paper.recordId}/submit`, {
            method: 'POST',
            headers
        })
        console.log(`  ${profile.username}: 提交完成，得分 ${result.objectiveScore}/${result.totalScore}，答对 ${result.correctCount}/${result.questionCount}`)
    }

    const analytics = await api(`/api/analytics/exams/${exam.id}`, { headers: authHeader })
    console.log(`\n看板数据：已交卷 ${analytics.submittedCount} 人，平均分 ${analytics.avgScore}，样本充足=${analytics.sufficient}`)
    if (analytics.knowledgeStats.length) {
        console.log('知识点掌握度：')
        for (const k of analytics.knowledgeStats) {
            console.log(`  ${k.name}: ${Math.round(k.mastery * 100)}%${k.weak ? '（薄弱）' : ''}`)
        }
    }
}

main().catch(e => {
    console.error('执行失败：', e.message)
    process.exit(1)
})
