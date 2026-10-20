<template>
    <div v-loading="loading">
        <!-- 顶部：状态与操作 -->
        <el-card shadow="never" class="mb-12">
            <div class="head">
                <div class="head-left">
                    <el-button link :icon="ArrowLeft" @click="$router.push('/exams')">返回列表</el-button>
                    <span class="title">{{ detail.title || '未命名考试' }}</span>
                    <el-tag v-if="detail.status" :type="statusType(detail.status)" size="small">
                        {{ detail.statusLabel }}
                    </el-tag>
                    <el-tag v-if="isDraft && !detail.scoreMatched" type="warning" size="small">分值不一致</el-tag>
                </div>
                <div>
                    <el-button v-if="isDraft" type="primary" :loading="saving" @click="onSaveSettings">
                        保存设置
                    </el-button>
                    <el-button v-if="!isDraft" :icon="Clock" @click="scheduleVisible = true">调整时间</el-button>
                    <el-button v-if="isDraft" type="success" :icon="Promotion" @click="onPublish">发布考试</el-button>
                    <el-button v-if="detail.status === 'PUBLISHED'" type="warning" @click="onFinish">结束考试</el-button>
                </div>
            </div>
        </el-card>

        <el-row :gutter="12">
            <!-- 左：基本设置 -->
            <el-col :span="9">
                <el-card shadow="never" class="mb-12">
                    <template #header>考试设置</template>
                    <el-form ref="formRef" :model="form" :rules="rules" label-width="95px" :disabled="!isDraft">
                        <el-form-item label="标题" prop="title">
                            <el-input v-model="form.title" placeholder="如：Java 程序设计 期中测验"/>
                        </el-form-item>
                        <el-form-item label="课程">
                            <el-select v-model="form.courseId" placeholder="可不选" clearable style="width: 100%">
                                <el-option v-for="c in courseOptions" :key="c.id" :label="c.name" :value="c.id"/>
                            </el-select>
                        </el-form-item>
                        <el-form-item label="说明">
                            <el-input v-model="form.description" type="textarea" :rows="2"
                                      placeholder="考试范围、注意事项等"/>
                        </el-form-item>
                        <el-row>
                            <el-col :span="12">
                                <el-form-item label="试卷总分" prop="totalScore">
                                    <el-input-number v-model="form.totalScore" :min="1" :max="1000"/>
                                </el-form-item>
                            </el-col>
                            <el-col :span="12">
                                <el-form-item label="及格线" prop="passScore">
                                    <el-input-number v-model="form.passScore" :min="0" :max="1000"/>
                                </el-form-item>
                            </el-col>
                        </el-row>
                        <el-form-item label="考试时间" prop="range">
                            <el-date-picker
                                v-model="form.range"
                                type="datetimerange"
                                range-separator="至"
                                start-placeholder="开始时间"
                                end-placeholder="结束时间"
                                value-format="YYYY-MM-DD HH:mm:ss"
                                style="width: 100%"
                            />
                        </el-form-item>
                        <el-form-item label="作答时长" prop="duration">
                            <el-input-number v-model="form.duration" :min="1" :max="600"/>
                            <span class="unit">分钟</span>
                        </el-form-item>
                        <el-form-item label="限考次数">
                            <el-input-number v-model="form.maxAttempt" :min="1" :max="10"/>
                        </el-form-item>
                        <el-form-item label="乱序">
                            <el-checkbox v-model="form.shuffleQuestion">题目乱序</el-checkbox>
                            <el-checkbox v-model="form.shuffleOption">选项乱序</el-checkbox>
                        </el-form-item>
                    </el-form>
                </el-card>

                <!-- 参考班级 -->
                <el-card shadow="never">
                    <template #header>
                        <div class="card-header">
                            <span>参考班级</span>
                            <el-button v-if="isDraft" link type="primary" @click="openClassDialog">修改</el-button>
                        </div>
                    </template>
                    <template v-if="detail.classes?.length">
                        <el-tag v-for="c in detail.classes" :key="c.id" class="tag-gap" size="small">
                            {{ c.name }}
                        </el-tag>
                    </template>
                    <el-text v-else type="warning" size="small">尚未绑定班级，发布前必须至少选一个</el-text>
                </el-card>
            </el-col>

            <!-- 右：组卷 -->
            <el-col :span="15">
                <el-card shadow="never">
                    <template #header>
                        <div class="card-header">
                            <span>
                                试卷题目（{{ questions.length }} 题）
                                <el-tag :type="detail.scoreMatched ? 'success' : 'warning'" size="small" class="ml-8">
                                    合计 {{ scoreSum }} / 设定 {{ form.totalScore }} 分
                                </el-tag>
                            </span>
                            <div v-if="isDraft">
                                <el-button :icon="Plus" @click="openQuestionPicker">手动选题</el-button>
                                <el-button :icon="MagicStick" @click="randomVisible = true">随机组卷</el-button>
                                <el-button type="primary" :loading="savingQuestions" @click="onSaveQuestions">
                                    保存题目
                                </el-button>
                            </div>
                        </div>
                    </template>

                    <el-alert
                        v-if="isDraft && !detail.scoreMatched && questions.length > 0"
                        type="warning" :closable="false" show-icon class="mb-12"
                        :title="`题目分值合计 ${scoreSum} 分，与设定总分 ${form.totalScore} 分不一致，发布会被拒绝`"
                    />

                    <el-table :data="questions" border stripe size="small">
                        <el-table-column type="index" label="#" width="50" align="center"/>
                        <el-table-column label="题型" width="82">
                            <template #default="{ row }">
                                <el-tag size="small" :type="typeTagType(row.type)">{{ row.typeLabel }}</el-tag>
                            </template>
                        </el-table-column>
                        <el-table-column prop="stem" label="题干" min-width="240" show-overflow-tooltip/>
                        <el-table-column label="知识点" min-width="150">
                            <template #default="{ row }">
                                <el-tag v-for="n in row.knowledgePointNames" :key="n" size="small" class="tag-gap">
                                    {{ n }}
                                </el-tag>
                            </template>
                        </el-table-column>
                        <el-table-column label="分值" width="110" align="center">
                            <template #default="{ row }">
                                <el-input-number v-if="isDraft" v-model="row.score" :min="1" :max="100"
                                                 size="small" controls-position="right" style="width: 90px"/>
                                <span v-else>{{ row.score }}</span>
                            </template>
                        </el-table-column>
                        <el-table-column v-if="isDraft" label="操作" width="70" align="center">
                            <template #default="{ $index }">
                                <el-button link type="danger" size="small" @click="removeQuestion($index)">移除</el-button>
                            </template>
                        </el-table-column>
                        <template #empty>
                            <el-empty description="还没有题目，用「手动选题」或「随机组卷」添加"/>
                        </template>
                    </el-table>
                </el-card>
            </el-col>
        </el-row>

        <!-- 手动选题 -->
        <el-dialog v-model="pickerVisible" title="手动选题" width="900px" :close-on-click-modal="false">
            <el-form :inline="true" class="mb-12">
                <el-form-item label="题型">
                    <el-select v-model="pickerQuery.type" placeholder="全部" clearable style="width: 110px">
                        <el-option label="单选题" value="SINGLE"/>
                        <el-option label="多选题" value="MULTIPLE"/>
                        <el-option label="判断题" value="JUDGE"/>
                        <el-option label="填空题" value="BLANK"/>
                    </el-select>
                </el-form-item>
                <el-form-item label="难度">
                    <el-select v-model="pickerQuery.difficulty" placeholder="全部" clearable style="width: 100px">
                        <el-option label="简单" value="EASY"/>
                        <el-option label="中等" value="MEDIUM"/>
                        <el-option label="困难" value="HARD"/>
                    </el-select>
                </el-form-item>
                <el-form-item label="知识点">
                    <el-select v-model="pickerQuery.knowledgePointId" placeholder="全部" clearable style="width: 160px">
                        <el-option v-for="k in knowledgeOptions" :key="k.id" :label="k.name" :value="k.id"/>
                    </el-select>
                </el-form-item>
                <el-form-item label="关键词">
                    <el-input v-model="pickerQuery.keyword" placeholder="题干" clearable style="width: 140px"
                              @keyup.enter="loadPicker"/>
                </el-form-item>
                <el-form-item>
                    <el-button type="primary" @click="loadPicker">查询</el-button>
                </el-form-item>
            </el-form>

            <el-table :data="pickerRows" border size="small" max-height="380" @selection-change="onPickChange">
                <el-table-column type="selection" width="45" :selectable="row => !isPicked(row.id)"/>
                <el-table-column label="题型" width="82">
                    <template #default="{ row }">
                        <el-tag size="small" :type="typeTagType(row.type)">{{ row.typeLabel }}</el-tag>
                    </template>
                </el-table-column>
                <el-table-column prop="stem" label="题干" min-width="280" show-overflow-tooltip/>
                <el-table-column label="知识点" min-width="150">
                    <template #default="{ row }">
                        <el-tag v-for="k in row.knowledgePoints" :key="k.id" size="small" class="tag-gap">
                            {{ k.name }}
                        </el-tag>
                    </template>
                </el-table-column>
                <el-table-column prop="score" label="建议分值" width="90" align="center"/>
                <el-table-column label="状态" width="80" align="center">
                    <template #default="{ row }">
                        <el-tag v-if="isPicked(row.id)" size="small" type="success">已加入</el-tag>
                        <el-tag v-else size="small" type="info">可选</el-tag>
                    </template>
                </el-table-column>
            </el-table>
            <el-pagination
                class="pager"
                background
                layout="total, prev, pager, next"
                :total="pickerTotal"
                :current-page="pickerQuery.page"
                :page-size="pickerQuery.size"
                @current-change="p => { pickerQuery.page = p; loadPicker() }"
            />
            <template #footer>
                <span class="hint">已选 {{ pendingPicks.length }} 题；重复题不会重复加入</span>
                <el-button @click="pickerVisible = false">取消</el-button>
                <el-button type="primary" @click="applyPicks">加入试卷</el-button>
            </template>
        </el-dialog>

        <!-- 随机组卷 -->
        <el-dialog v-model="randomVisible" title="随机组卷" width="640px" :close-on-click-modal="false">
            <el-alert type="warning" :closable="false" show-icon class="mb-12"
                      title="随机组卷会替换当前试卷的全部题目。抽完后请人工检查，必要时手动调整。"/>
            <el-form label-width="90px">
                <el-form-item label="抽题课程">
                    <el-select v-model="randomForm.courseId" placeholder="不限课程" clearable style="width: 100%">
                        <el-option v-for="c in courseOptions" :key="c.id" :label="c.name" :value="c.id"/>
                    </el-select>
                </el-form-item>
                <el-form-item label="抽题规则">
                    <div class="rules">
                        <div v-for="(r, i) in randomForm.rules" :key="i" class="rule-row">
                            <el-select v-model="r.type" placeholder="题型" style="width: 110px">
                                <el-option label="单选题" value="SINGLE"/>
                                <el-option label="多选题" value="MULTIPLE"/>
                                <el-option label="判断题" value="JUDGE"/>
                                <el-option label="填空题" value="BLANK"/>
                            </el-select>
                            <el-select v-model="r.difficulty" placeholder="不限难度" clearable style="width: 110px">
                                <el-option label="简单" value="EASY"/>
                                <el-option label="中等" value="MEDIUM"/>
                                <el-option label="困难" value="HARD"/>
                            </el-select>
                            <el-input-number v-model="r.count" :min="1" :max="100" size="small"
                                             controls-position="right" style="width: 100px"/>
                            <span class="unit">题，每题</span>
                            <el-input-number v-model="r.score" :min="1" :max="100" size="small"
                                             controls-position="right" style="width: 100px"/>
                            <span class="unit">分</span>
                            <el-button link type="danger" :disabled="randomForm.rules.length <= 1"
                                       @click="randomForm.rules.splice(i, 1)">删除</el-button>
                        </div>
                        <el-button link type="primary" :icon="Plus" @click="addRule">添加规则</el-button>
                    </div>
                </el-form-item>
                <el-form-item label="预计总分">
                    <el-tag :type="randomSum === form.totalScore ? 'success' : 'info'">
                        {{ randomSum }} 分
                    </el-tag>
                    <span class="hint ml-8">（设定总分 {{ form.totalScore }} 分；抽完后仍可逐题调整分值）</span>
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="randomVisible = false">取消</el-button>
                <el-button type="primary" :loading="randomizing" @click="onRandom">开始抽题</el-button>
            </template>
        </el-dialog>

        <!-- 绑定班级 -->
        <el-dialog v-model="classVisible" title="选择参考班级" width="480px">
            <el-checkbox-group v-model="classSelection">
                <el-checkbox v-for="c in classOptions" :key="c.id" :value="c.id" class="class-item">
                    {{ c.name }}（{{ c.studentCount }} 人）
                </el-checkbox>
            </el-checkbox-group>
            <el-empty v-if="!classOptions.length" description="你名下还没有班级，请先到「班级管理」创建"/>
            <template #footer>
                <el-button @click="classVisible = false">取消</el-button>
                <el-button type="primary" :loading="savingClasses" @click="onSaveClasses">保存</el-button>
            </template>
        </el-dialog>

        <!-- 调整时间 -->
        <el-dialog v-model="scheduleVisible" title="调整考试时间" width="520px">
            <el-alert type="info" :closable="false" show-icon class="mb-12"
                      title="已发布的考试只允许调整时间窗与作答时长，题目与总分已冻结。"/>
            <el-form label-width="90px">
                <el-form-item label="考试时间">
                    <el-date-picker
                        v-model="scheduleForm.range"
                        type="datetimerange"
                        range-separator="至"
                        start-placeholder="开始时间"
                        end-placeholder="结束时间"
                        value-format="YYYY-MM-DD HH:mm:ss"
                        style="width: 100%"
                    />
                </el-form-item>
                <el-form-item label="作答时长">
                    <el-input-number v-model="scheduleForm.duration" :min="1" :max="600"/>
                    <span class="unit">分钟</span>
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="scheduleVisible = false">取消</el-button>
                <el-button type="primary" :loading="saving" @click="onSaveSchedule">保存</el-button>
            </template>
        </el-dialog>
    </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Clock, MagicStick, Plus, Promotion } from '@element-plus/icons-vue'
import { examApi } from '@/api/exam'
import { questionApi, knowledgePointApi, courseApi } from '@/api/question'
import { classApi } from '@/api/class'

const route = useRoute()
const router = useRouter()
const examId = Number(route.params.id)

const loading = ref(false)
const saving = ref(false)
const savingQuestions = ref(false)
const savingClasses = ref(false)
const randomizing = ref(false)

const detail = ref({})
const questions = ref([])
const courseOptions = ref([])
const knowledgeOptions = ref([])
const classOptions = ref([])

const formRef = ref(null)
const form = reactive({
    title: '', description: '', courseId: null,
    totalScore: 100, passScore: 60,
    range: [], duration: 60, maxAttempt: 1,
    shuffleQuestion: false, shuffleOption: false
})

const isDraft = computed(() => detail.value.status === 'DRAFT')
const scoreSum = computed(() => questions.value.reduce((s, q) => s + (q.score || 0), 0))

const rules = {
    title: [{ required: true, message: '请输入考试标题', trigger: 'blur' }],
    totalScore: [{ required: true, message: '请输入试卷总分', trigger: 'change' }],
    passScore: [{ required: true, message: '请输入及格线', trigger: 'change' }],
    duration: [{ required: true, message: '请输入作答时长', trigger: 'change' }],
    range: [{
        validator: (_, v, cb) => (v && v.length === 2) ? cb() : cb(new Error('请选择考试时间范围')),
        trigger: 'change'
    }]
}

function statusType(s) {
    return { DRAFT: 'info', PUBLISHED: 'success', FINISHED: 'warning' }[s] || ''
}

function typeTagType(t) {
    return { SINGLE: 'primary', MULTIPLE: 'success', JUDGE: 'warning', BLANK: 'info' }[t] || ''
}

async function loadDetail() {
    loading.value = true
    try {
        const d = await examApi.detail(examId)
        detail.value = d
        questions.value = (d.questions || []).map(q => ({ ...q }))
        Object.assign(form, {
            title: d.title,
            description: d.description || '',
            courseId: d.courseId,
            totalScore: d.totalScore,
            passScore: d.passScore,
            range: [d.startTime, d.endTime],
            duration: d.duration,
            maxAttempt: d.maxAttempt ?? 1,
            shuffleQuestion: !!d.shuffleQuestion,
            shuffleOption: !!d.shuffleOption
        })
    } catch {
        router.push('/exams')
    } finally {
        loading.value = false
    }
}

async function loadOptions() {
    try {
        courseOptions.value = await courseApi.options()
        knowledgeOptions.value = await knowledgePointApi.options()
        classOptions.value = await classApi.options()
    } catch {
        // 拦截器已提示
    }
}

async function onSaveSettings() {
    const valid = await formRef.value?.validate().catch(() => false)
    if (!valid) return
    saving.value = true
    try {
        await examApi.update(examId, buildSettingsPayload())
        ElMessage.success('设置已保存')
        await loadDetail()
    } catch {
        // 拦截器已提示
    } finally {
        saving.value = false
    }
}

function buildSettingsPayload() {
    return {
        title: form.title,
        description: form.description || null,
        courseId: form.courseId,
        totalScore: form.totalScore,
        passScore: form.passScore,
        startTime: form.range?.[0],
        endTime: form.range?.[1],
        duration: form.duration,
        shuffleQuestion: form.shuffleQuestion,
        shuffleOption: form.shuffleOption,
        maxAttempt: form.maxAttempt
    }
}

// ---------- 组卷 ----------
async function onSaveQuestions() {
    if (!questions.value.length) {
        ElMessage.warning('试卷还没有题目')
        return
    }
    if (questions.value.some(q => !q.score || q.score < 1)) {
        ElMessage.warning('每题分值必须大于 0')
        return
    }
    savingQuestions.value = true
    try {
        const d = await examApi.setQuestions(examId, questions.value.map(q => ({
            questionId: q.questionId,
            score: q.score
        })))
        detail.value = d
        questions.value = (d.questions || []).map(q => ({ ...q }))
        ElMessage.success(`已保存 ${questions.value.length} 道题`)
    } catch {
        // 拦截器已提示
    } finally {
        savingQuestions.value = false
    }
}

function removeQuestion(index) {
    questions.value.splice(index, 1)
}

async function onPublish() {
    // 先保存题目，保证发布的是当前编辑内容
    if (questions.value.length) {
        await onSaveQuestions()
    }
    try {
        await ElMessageBox.confirm(
            '发布后题目与总分将被冻结，学生可在时间窗内作答。确定发布吗？', '发布考试', { type: 'warning' })
    } catch { return }
    try {
        await examApi.publish(examId)
        ElMessage.success('已发布')
        await loadDetail()
    } catch {
        // 后端会指出具体原因：分值不一致 / 未绑班级 / 时间已过
    }
}

async function onFinish() {
    try {
        await ElMessageBox.confirm('结束后学生不能再进入该考试，确定结束吗？', '结束考试', { type: 'warning' })
    } catch { return }
    try {
        await examApi.finish(examId)
        ElMessage.success('已结束')
        await loadDetail()
    } catch {
        // 拦截器已提示
    }
}

async function onSaveSchedule() {
    if (!scheduleForm.range || scheduleForm.range.length !== 2) {
        ElMessage.warning('请选择考试时间范围')
        return
    }
    saving.value = true
    try {
        await examApi.updateSchedule(examId, {
            startTime: scheduleForm.range[0],
            endTime: scheduleForm.range[1],
            duration: scheduleForm.duration
        })
        ElMessage.success('时间已调整')
        scheduleVisible.value = false
        await loadDetail()
    } catch {
        // 拦截器已提示
    } finally {
        saving.value = false
    }
}

// ---------- 手动选题 ----------
const pickerVisible = ref(false)
const pickerRows = ref([])
const pickerTotal = ref(0)
const pickerQuery = reactive({ page: 1, size: 10, type: null, difficulty: null, knowledgePointId: null, keyword: '' })
const pendingPicks = ref([])

function isPicked(id) {
    return questions.value.some(q => q.questionId === id)
}

function openQuestionPicker() {
    pickerVisible.value = true
    pendingPicks.value = []
    loadPicker()
}

async function loadPicker() {
    try {
        const data = await questionApi.page({
            page: pickerQuery.page,
            size: pickerQuery.size,
            courseId: form.courseId || undefined,
            type: pickerQuery.type || undefined,
            difficulty: pickerQuery.difficulty || undefined,
            knowledgePointId: pickerQuery.knowledgePointId ?? undefined,
            keyword: pickerQuery.keyword || undefined,
            enabled: true
        })
        pickerRows.value = data.list
        pickerTotal.value = data.total
    } catch {
        pickerRows.value = []
        pickerTotal.value = 0
    }
}

function onPickChange(rows) {
    pendingPicks.value = rows
}

function applyPicks() {
    if (!pendingPicks.value.length) {
        ElMessage.warning('请先勾选题目')
        return
    }
    let added = 0
    for (const row of pendingPicks.value) {
        if (isPicked(row.id)) continue
        questions.value.push({
            questionId: row.id,
            type: row.type,
            typeLabel: row.typeLabel,
            stem: row.stem,
            // 默认沿用题库中的建议分值，教师可在列表中逐题调整
            score: row.score,
            knowledgePointNames: (row.knowledgePoints || []).map(k => k.name)
        })
        added++
    }
    pickerVisible.value = false
    ElMessage.success(`已加入 ${added} 题，记得点「保存题目」`)
}

// ---------- 随机组卷 ----------
const randomVisible = ref(false)
const randomForm = reactive({
    courseId: null,
    rules: [{ type: 'SINGLE', difficulty: null, count: 5, score: 5 }]
})

const randomSum = computed(() =>
    randomForm.rules.reduce((s, r) => s + (r.count || 0) * (r.score || 0), 0))

function addRule() {
    randomForm.rules.push({ type: 'SINGLE', difficulty: null, count: 5, score: 5 })
}

async function onRandom() {
    randomizing.value = true
    try {
        const d = await examApi.random(examId, {
            courseId: randomForm.courseId,
            rules: randomForm.rules.map(r => ({
                type: r.type,
                difficulty: r.difficulty || null,
                count: r.count,
                score: r.score
            }))
        })
        detail.value = d
        questions.value = (d.questions || []).map(q => ({ ...q }))
        randomVisible.value = false
        ElMessage.success(`已抽取 ${questions.value.length} 道题`)
    } catch {
        // 题目不足等错误由拦截器提示
    } finally {
        randomizing.value = false
    }
}

// ---------- 绑定班级 ----------
const classVisible = ref(false)
const classSelection = ref([])

function openClassDialog() {
    classSelection.value = (detail.value.classes || []).map(c => c.id)
    classVisible.value = true
}

async function onSaveClasses() {
    if (!classSelection.value.length) {
        ElMessage.warning('请至少选择一个班级')
        return
    }
    savingClasses.value = true
    try {
        const d = await examApi.bindClasses(examId, classSelection.value)
        detail.value = d
        classVisible.value = false
        ElMessage.success('已绑定班级')
    } catch {
        // 拦截器已提示
    } finally {
        savingClasses.value = false
    }
}

// ---------- 调整时间 ----------
const scheduleVisible = ref(false)
const scheduleForm = reactive({ range: [], duration: 60 })

onMounted(async () => {
    await loadDetail()
    form.range = [detail.value.startTime, detail.value.endTime]
    scheduleForm.range = [...form.range]
    scheduleForm.duration = form.duration
    randomForm.courseId = form.courseId
    await loadOptions()
})
</script>

<style scoped>
.mb-12 { margin-bottom: 12px; }
.ml-8 { margin-left: 8px; }
.head { display: flex; align-items: center; justify-content: space-between; }
.head-left { display: flex; align-items: center; gap: 10px; }
.title { font-size: 16px; font-weight: 600; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
.pager { margin-top: 12px; justify-content: flex-end; }
.tag-gap { margin-right: 4px; margin-bottom: 2px; }
.unit { margin-left: 6px; color: #909399; font-size: 12px; }
.hint { font-size: 12px; color: #909399; }
.rules { width: 100%; }
.rule-row { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.class-item { display: block; margin-bottom: 6px; }
</style>
