<template>
    <div v-loading="loading" class="take-page">
        <!-- 顶栏：标题 + 倒计时 + 交卷 -->
        <el-card shadow="never" class="mb-12">
            <div class="head">
                <div class="head-left">
                    <span class="title">{{ paper.title }}</span>
                    <el-tag size="small" type="info">共 {{ paper.questions?.length || 0 }} 题 / {{ paper.totalScore }} 分</el-tag>
                    <el-tag size="small" :type="saveStateType">{{ saveStateText }}</el-tag>
                </div>
                <div class="head-right">
                    <span class="countdown" :class="{ danger: remaining <= 300 }">
                        <el-icon><Timer/></el-icon>
                        {{ formatRemaining(remaining) }}
                    </span>
                    <el-button type="primary" :loading="submitting" @click="onSubmitClick">交卷</el-button>
                </div>
            </div>
        </el-card>

        <el-row :gutter="12">
            <!-- 左：题目 -->
            <el-col :span="17">
                <el-card v-for="(q, idx) in paper.questions" :key="q.questionId" :id="`q-${q.questionId}`"
                         shadow="never" class="q-card"
                         :class="{ active: activeQid === q.questionId }"
                         @focusin="setActive(q.questionId)">
                    <div class="q-head">
                        <span class="q-index">{{ idx + 1 }}</span>
                        <el-tag size="small" :type="typeTagType(q.type)">{{ q.typeLabel }}</el-tag>
                        <span class="q-score">{{ q.score }} 分</span>
                        <el-tag v-if="isAnswered(q.questionId)" size="small" type="success" effect="plain">已答</el-tag>
                    </div>

                    <div class="q-stem">{{ q.stem }}</div>

                    <el-image v-if="q.imageUrl" :src="q.imageUrl" fit="contain" class="q-image"
                              :preview-src-list="[q.imageUrl]" preview-teleported/>

                    <!-- 单选 -->
                    <el-radio-group v-if="q.type === 'SINGLE'" v-model="answers[q.questionId][0]"
                                    class="opt-group" @change="onAnswerChange(q.questionId)">
                        <el-radio v-for="(opt, i) in q.options" :key="i" :value="label(i)" class="opt">
                            {{ label(i) }}. {{ opt }}
                        </el-radio>
                    </el-radio-group>

                    <!-- 多选 -->
                    <el-checkbox-group v-else-if="q.type === 'MULTIPLE'" v-model="answers[q.questionId]"
                                       class="opt-group" @change="onAnswerChange(q.questionId)">
                        <el-checkbox v-for="(opt, i) in q.options" :key="i" :value="label(i)" class="opt">
                            {{ label(i) }}. {{ opt }}
                        </el-checkbox>
                    </el-checkbox-group>

                    <!-- 判断 -->
                    <el-radio-group v-else-if="q.type === 'JUDGE'" v-model="answers[q.questionId][0]"
                                    class="opt-group" @change="onAnswerChange(q.questionId)">
                        <el-radio value="T" class="opt">正确</el-radio>
                        <el-radio value="F" class="opt">错误</el-radio>
                    </el-radio-group>

                    <!-- 填空 -->
                    <div v-else-if="q.type === 'BLANK'">
                        <el-input v-model="answers[q.questionId][0]" placeholder="请输入答案"
                                  class="blank-input" @input="onAnswerChange(q.questionId)"/>
                    </div>
                </el-card>
            </el-col>

            <!-- 右：答题卡 -->
            <el-col :span="7">
                <el-card shadow="never" class="sticky">
                    <template #header>
                        <div class="card-header">
                            <span>答题卡</span>
                            <span class="sub">已答 {{ answeredCount }} / {{ paper.questions?.length || 0 }}</span>
                        </div>
                    </template>
                    <div class="navigator">
                        <div v-for="(q, idx) in paper.questions" :key="q.questionId"
                             class="nav-cell" :class="{ done: isAnswered(q.questionId), active: activeQid === q.questionId }"
                             @click="focusQuestion(q.questionId)">
                            {{ idx + 1 }}
                        </div>
                    </div>
                    <el-divider/>
                    <div class="legend">
                        <span><i class="dot done"/></span>已答
                        <span class="ml"><i class="dot"/></span>未答
                    </div>
                    <el-alert type="info" :closable="false" class="mt-12" show-icon
                              title="答案自动保存，可随时刷新或断线重进"/>
                </el-card>
            </el-col>
        </el-row>
    </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Timer } from '@element-plus/icons-vue'
import { takingApi } from '@/api/answer'

const route = useRoute()
const router = useRouter()
const examId = Number(route.params.examId)

const LABELS = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H']

const loading = ref(false)
const submitting = ref(false)
const paper = reactive({
    recordId: null,
    title: '',
    totalScore: 0,
    passScore: 0,
    questions: [],
    status: ''
})

/** 答案：questionId -> string[]（多选为多元素，其余单元素） */
const answers = reactive({})
/** 每题的过程数据：作答耗时与修改次数，随自动保存上报给服务端 */
const meta = reactive({})

const remaining = ref(0)
const activeQid = ref(null)
const saveState = ref('idle') // idle | saving | saved | error

let countdownTimer = null
let autosaveTimer = null
let debounceTimer = null
let activeSince = null
let dirty = new Set()
let finished = false

const answeredCount = computed(() =>
    paper.questions.filter(q => isAnswered(q.questionId)).length)

const saveStateText = computed(() => ({
    idle: '未保存',
    saving: '保存中…',
    saved: '已保存',
    error: '保存失败'
}[saveState.value]))

const saveStateType = computed(() => ({
    idle: 'info', saving: 'warning', saved: 'success', error: 'danger'
}[saveState.value]))

function label(i) {
    return LABELS[i] || ''
}

function typeTagType(t) {
    return { SINGLE: 'primary', MULTIPLE: 'success', JUDGE: 'warning', BLANK: 'info' }[t] || ''
}

function isAnswered(qid) {
    const v = answers[qid]
    return Array.isArray(v) && v.some(s => s != null && String(s).trim() !== '')
}

function formatRemaining(sec) {
    const s = Math.max(0, sec || 0)
    const h = Math.floor(s / 3600)
    const m = Math.floor((s % 3600) / 60)
    const ss = s % 60
    const p = n => String(n).padStart(2, '0')
    return h > 0 ? `${p(h)}:${p(m)}:${p(ss)}` : `${p(m)}:${p(ss)}`
}

// ---------- 计时：按「当前聚焦的题目」累计耗时 ----------
function setActive(qid) {
    const now = Date.now()
    if (activeQid.value && activeSince) {
        const elapsed = Math.round((now - activeSince) / 1000)
        if (elapsed > 0 && meta[activeQid.value]) {
            meta[activeQid.value].timeSpentSec += elapsed
        }
    }
    if (activeQid.value !== qid) {
        activeQid.value = qid
        activeSince = now
    }
}

function flushActiveTime() {
    if (activeQid.value && activeSince) {
        const elapsed = Math.round((Date.now() - activeSince) / 1000)
        if (elapsed > 0 && meta[activeQid.value]) {
            meta[activeQid.value].timeSpentSec += elapsed
        }
        activeSince = Date.now()
    }
}

function onAnswerChange(qid) {
    if (finished) return
    if (meta[qid]) {
        meta[qid].submitCount += 1
    }
    dirty.add(qid)
    // 防抖：连续输入时只在停顿后保存一次
    clearTimeout(debounceTimer)
    debounceTimer = setTimeout(() => save(false), 1500)
}

function focusQuestion(qid) {
    activeQid.value = qid
    const el = document.getElementById(`q-${qid}`)
    if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

// ---------- 自动保存 ----------
function collectPayload(all) {
    const ids = all
        ? paper.questions.map(q => q.questionId)
        : Array.from(dirty)
    return ids
        .filter(id => meta[id])
        .map(id => ({
            questionId: id,
            answer: answers[id] || [],
            timeSpentSec: meta[id].timeSpentSec || 0,
            submitCount: meta[id].submitCount || 0
        }))
}

async function save(all = false) {
    if (finished || submitting.value || !paper.recordId) return
    const payload = collectPayload(all)
    if (!payload.length) return

    saveState.value = 'saving'
    try {
        const ack = await takingApi.save(paper.recordId, payload)
        saveState.value = 'saved'
        // 以服务端返回的剩余时间校准本地倒计时，避免前端计时漂移
        if (typeof ack.remainingSeconds === 'number') {
            remaining.value = ack.remainingSeconds
        }
        if (ack.submitted) {
            handleAutoSubmitted()
            return
        }
        if (!all) dirty.clear()
    } catch (e) {
        saveState.value = 'error'
        // 401/403 由拦截器处理；超时类错误不弹窗，避免打断作答
        if (e?.response?.status === 409) {
            handleAutoSubmitted()
        }
    }
}

// ---------- 倒计时 ----------
function startCountdown() {
    stopCountdown()
    countdownTimer = setInterval(() => {
        if (finished) return
        remaining.value = Math.max(0, remaining.value - 1)
        if (remaining.value <= 0) {
            ElMessage.warning('考试时间已到，正在自动交卷')
            doSubmit(true)
        }
    }, 1000)
}

function stopCountdown() {
    if (countdownTimer) {
        clearInterval(countdownTimer)
        countdownTimer = null
    }
}

// ---------- 交卷 ----------
function onSubmitClick() {
    const unanswered = paper.questions.length - answeredCount.value
    ElMessageBox.confirm(
        unanswered > 0
            ? `还有 ${unanswered} 题未作答，确定交卷吗？交卷后不能修改。`
            : '确定交卷吗？交卷后不能修改。',
        '交卷确认',
        { type: unanswered > 0 ? 'warning' : 'info' }
    ).then(() => doSubmit(false)).catch(() => {})
}

async function doSubmit(auto) {
    if (finished || submitting.value) return
    submitting.value = true
    finished = true
    stopCountdown()
    clearTimeout(debounceTimer)
    clearInterval(autosaveTimer)
    try {
        // 交卷前把最后一段停留时间与未保存的改动落库
        flushActiveTime()
        if (!auto) {
            await save(true)
        }
        const result = await takingApi.submit(paper.recordId)
        ElMessage.success(auto ? '已自动交卷' : '交卷成功')
        router.replace(`/my-records/${result.recordId}`)
    } catch {
        finished = false
        submitting.value = false
    }
}

function handleAutoSubmitted() {
    if (finished) return
    finished = true
    stopCountdown()
    clearInterval(autosaveTimer)
    ElMessage.warning('答题时间已到，系统已自动交卷')
    router.replace(`/my-records/${paper.recordId}`)
}

/** 切屏/失焦只留痕，供教师参考，不自动判作弊 */
function onVisibilityChange() {
    if (document.visibilityState === 'hidden' && !finished && paper.recordId) {
        takingApi.reportSwitch(paper.recordId).catch(() => {})
    }
}

// ---------- 初始化 ----------
async function init() {
    loading.value = true
    try {
        const p = await takingApi.start(examId)
        // 已交卷或已超时：服务端返回结束状态的空试卷，直接跳成绩页
        if (p.status === 'SUBMITTED' || p.status === 'AUTO_SUBMITTED') {
            ElMessage.warning('本场考试已结束作答')
            router.replace(`/my-records/${p.recordId}`)
            return
        }

        paper.recordId = p.recordId
        paper.title = p.title
        paper.totalScore = p.totalScore
        paper.passScore = p.passScore
        paper.status = p.status
        paper.questions = p.questions || []
        remaining.value = p.remainingSeconds || 0

        for (const q of paper.questions) {
            answers[q.questionId] = []
            meta[q.questionId] = { timeSpentSec: 0, submitCount: 0 }
        }
        // 回填已保存答案（断线重进）
        for (const s of p.savedAnswers || []) {
            if (answers[s.questionId]) {
                answers[s.questionId] = [...(s.answer || [])]
            }
        }

        if (!paper.questions.length) {
            ElMessage.warning('该考试没有题目')
            return
        }
        if (remaining.value <= 0) {
            ElMessage.warning('答题时间已到，正在自动交卷')
            doSubmit(true)
            return
        }

        startCountdown()
        // 兜底轮询：即使学生长时间不操作，也定期把答案与耗时落库
        autosaveTimer = setInterval(() => save(true), 30000)
        document.addEventListener('visibilitychange', onVisibilityChange)
    } catch {
        router.replace('/my-exams')
    } finally {
        loading.value = false
    }
}

onMounted(init)

onBeforeUnmount(() => {
    stopCountdown()
    clearInterval(autosaveTimer)
    clearTimeout(debounceTimer)
    document.removeEventListener('visibilitychange', onVisibilityChange)
})
</script>

<style scoped>
.take-page { padding-bottom: 24px; }
.mb-12 { margin-bottom: 12px; }
.mt-12 { margin-top: 12px; }
.ml { margin-left: 14px; }
.head { display: flex; align-items: center; justify-content: space-between; }
.head-left { display: flex; align-items: center; gap: 10px; }
.head-right { display: flex; align-items: center; gap: 14px; }
.title { font-size: 16px; font-weight: 600; }
.countdown { font-size: 20px; font-weight: 700; color: #409eff; font-variant-numeric: tabular-nums; }
.countdown.danger { color: #f56c6c; }
.q-card { margin-bottom: 12px; border-left: 3px solid transparent; }
.q-card.active { border-left-color: #409eff; }
.q-head { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; }
.q-index { font-weight: 700; color: #909399; }
.q-score { color: #909399; font-size: 12px; }
.q-stem { line-height: 1.8; margin-bottom: 12px; white-space: pre-wrap; }
.q-image { max-width: 100%; max-height: 320px; margin-bottom: 12px; border-radius: 4px; }
.opt-group { display: flex; flex-direction: column; gap: 8px; }
.opt { display: flex; margin-right: 0; height: auto; white-space: normal; }
.blank-input { max-width: 420px; }
.sticky { position: sticky; top: 12px; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
.sub { font-size: 12px; color: #909399; }
.navigator { display: flex; flex-wrap: wrap; gap: 8px; }
.nav-cell {
  width: 34px; height: 34px; display: flex; align-items: center; justify-content: center;
  border: 1px solid #dcdfe6; border-radius: 4px; cursor: pointer; font-size: 13px; color: #606266;
}
.nav-cell.done { background: #f0f9eb; border-color: #67c23a; color: #67c23a; }
.nav-cell.active { outline: 2px solid #409eff; }
.legend { font-size: 12px; color: #909399; display: flex; align-items: center; }
.dot { display: inline-block; width: 10px; height: 10px; border: 1px solid #dcdfe6; border-radius: 2px; margin-right: 4px; }
.dot.done { background: #f0f9eb; border-color: #67c23a; }
</style>
