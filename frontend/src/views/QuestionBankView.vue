<template>
    <div>
        <!-- 筛选 -->
        <el-card shadow="never" class="mb-12">
            <el-form :inline="true" @submit.prevent>
                <el-form-item label="课程">
                    <el-select v-model="query.courseId" placeholder="全部" clearable style="width: 160px">
                        <el-option v-for="c in courseOptions" :key="c.id" :label="c.name" :value="c.id"/>
                    </el-select>
                </el-form-item>
                <el-form-item label="题型">
                    <el-select v-model="query.type" placeholder="全部" clearable style="width: 120px">
                        <el-option label="单选题" value="SINGLE"/>
                        <el-option label="多选题" value="MULTIPLE"/>
                        <el-option label="判断题" value="JUDGE"/>
                        <el-option label="填空题" value="BLANK"/>
                    </el-select>
                </el-form-item>
                <el-form-item label="难度">
                    <el-select v-model="query.difficulty" placeholder="全部" clearable style="width: 110px">
                        <el-option label="简单" value="EASY"/>
                        <el-option label="中等" value="MEDIUM"/>
                        <el-option label="困难" value="HARD"/>
                    </el-select>
                </el-form-item>
                <el-form-item label="知识点">
                    <el-select v-model="query.knowledgePointId" placeholder="全部" clearable style="width: 180px">
                        <el-option v-for="k in knowledgeOptions" :key="k.id" :label="k.name" :value="k.id"/>
                    </el-select>
                </el-form-item>
                <el-form-item label="状态">
                    <el-select v-model="query.enabled" placeholder="全部" clearable style="width: 100px">
                        <el-option label="启用" :value="true"/>
                        <el-option label="停用" :value="false"/>
                    </el-select>
                </el-form-item>
                <el-form-item label="关键词">
                    <el-input v-model="query.keyword" placeholder="题干关键词" clearable style="width: 160px"
                              @keyup.enter="reload"/>
                </el-form-item>
                <el-form-item>
                    <el-button type="primary" :icon="Search" @click="reload">查询</el-button>
                    <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
                </el-form-item>
            </el-form>
        </el-card>

        <!-- 列表 -->
        <el-card shadow="never">
            <template #header>
                <div class="card-header">
                    <span>题库（共 {{ total }} 道）</span>
                    <el-button type="primary" :icon="Plus" @click="openCreate">新增题目</el-button>
                </div>
            </template>

            <el-table v-loading="loading" :data="rows" border stripe>
                <el-table-column type="index" label="#" width="55" align="center"/>
                <el-table-column label="题型" width="90">
                    <template #default="{ row }">
                        <el-tag size="small" :type="typeTagType(row.type)">{{ row.typeLabel }}</el-tag>
                    </template>
                </el-table-column>
                <el-table-column prop="stem" label="题干" min-width="300" show-overflow-tooltip/>
                <el-table-column label="知识点" min-width="200">
                    <template #default="{ row }">
                        <template v-if="row.knowledgePoints?.length">
                            <el-tag v-for="k in row.knowledgePoints" :key="k.id" size="small" class="kp-tag">
                                {{ k.name }}
                            </el-tag>
                        </template>
                        <el-text v-else type="warning" size="small">未标注</el-text>
                    </template>
                </el-table-column>
                <el-table-column label="难度" width="80" align="center">
                    <template #default="{ row }">{{ row.difficultyLabel }}</template>
                </el-table-column>
                <el-table-column prop="score" label="分值" width="70" align="center"/>
                <el-table-column label="状态" width="80" align="center">
                    <template #default="{ row }">
                        <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
                            {{ row.enabled ? '启用' : '停用' }}
                        </el-tag>
                    </template>
                </el-table-column>
                <el-table-column label="操作" width="200" align="center" fixed="right">
                    <template #default="{ row }">
                        <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
                        <el-button link :type="row.enabled ? 'warning' : 'success'" size="small"
                                   @click="onToggle(row)">
                            {{ row.enabled ? '停用' : '启用' }}
                        </el-button>
                        <el-button link type="danger" size="small" @click="onDelete(row)">删除</el-button>
                    </template>
                </el-table-column>
                <template #empty>
                    <el-empty description="暂无题目"/>
                </template>
            </el-table>

            <el-pagination
                class="pager"
                background
                layout="total, sizes, prev, pager, next"
                :total="total"
                :current-page="query.page"
                :page-size="query.size"
                :page-sizes="[10, 20, 50]"
                @current-change="onPageChange"
                @size-change="onSizeChange"
            />
        </el-card>

        <!-- 新增 / 编辑 -->
        <el-dialog v-model="formVisible" :title="form.id ? '编辑题目' : '新增题目'" width="640px"
                   :close-on-click-modal="false">
            <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
                <el-form-item label="题型" prop="type">
                    <el-radio-group v-model="form.type" :disabled="!!form.id" @change="onTypeChange">
                        <el-radio-button value="SINGLE">单选题</el-radio-button>
                        <el-radio-button value="MULTIPLE">多选题</el-radio-button>
                        <el-radio-button value="JUDGE">判断题</el-radio-button>
                        <el-radio-button value="BLANK">填空题</el-radio-button>
                    </el-radio-group>
                </el-form-item>

                <el-form-item label="题干" prop="stem">
                    <el-input v-model="form.stem" type="textarea" :rows="3"
                              placeholder="填空题的空位用 ______ 表示"/>
                </el-form-item>

                <!-- 选择题选项 -->
                <el-form-item v-if="isChoice" label="选项">
                    <div class="options">
                        <div v-for="(opt, idx) in form.options" :key="idx" class="option-row">
                            <span class="option-label">{{ optionLabel(idx) }}</span>
                            <el-checkbox
                                v-if="form.type === 'MULTIPLE'"
                                :model-value="form.answer.includes(optionLabel(idx))"
                                @change="v => toggleAnswer(optionLabel(idx), v)"
                            />
                            <el-radio
                                v-else
                                :model-value="form.answer[0]"
                                :value="optionLabel(idx)"
                                @change="form.answer = [optionLabel(idx)]"
                            />
                            <el-input v-model="form.options[idx]" placeholder="选项内容"/>
                            <el-button link type="danger" size="small"
                                       :disabled="form.options.length <= 2"
                                       @click="removeOption(idx)">删除</el-button>
                        </div>
                        <el-button link type="primary" :icon="Plus"
                                   :disabled="form.options.length >= 8"
                                   @click="addOption">添加选项</el-button>
                    </div>
                </el-form-item>

                <!-- 判断题答案 -->
                <el-form-item v-if="form.type === 'JUDGE'" label="正确答案" prop="answer">
                    <el-radio-group v-model="form.answer[0]">
                        <el-radio value="T">正确</el-radio>
                        <el-radio value="F">错误</el-radio>
                    </el-radio-group>
                </el-form-item>

                <!-- 填空题答案 -->
                <el-form-item v-if="form.type === 'BLANK'" label="可接受答案">
                    <el-input
                        :model-value="form.answer.join('；')"
                        placeholder="多个可接受答案用中文分号「；」分隔"
                        @update:model-value="v => form.answer = splitAnswers(v)"
                    />
                    <div class="hint">判分时会做去空格、忽略大小写、全角转半角处理，命中任一答案即得分</div>
                </el-form-item>

                <el-form-item label="知识点" prop="knowledgePointIds">
                    <el-select v-model="form.knowledgePointIds" multiple filterable
                               placeholder="选择该题考查的知识点（可多选）" style="width: 100%">
                        <el-option v-for="k in knowledgeOptions" :key="k.id" :label="k.name" :value="k.id"/>
                    </el-select>
                    <div class="hint">知识点决定学情统计的口径：不标注则该题的作答不会计入任何知识点</div>
                </el-form-item>

                <el-form-item label="课程">
                    <el-select v-model="form.courseId" placeholder="可不选" clearable style="width: 100%">
                        <el-option v-for="c in courseOptions" :key="c.id" :label="c.name" :value="c.id"/>
                    </el-select>
                </el-form-item>

                <el-row>
                    <el-col :span="12">
                        <el-form-item label="分值" prop="score">
                            <el-input-number v-model="form.score" :min="1" :max="100"/>
                        </el-form-item>
                    </el-col>
                    <el-col :span="12">
                        <el-form-item label="难度" prop="difficulty">
                            <el-select v-model="form.difficulty" style="width: 120px">
                                <el-option label="简单" value="EASY"/>
                                <el-option label="中等" value="MEDIUM"/>
                                <el-option label="困难" value="HARD"/>
                            </el-select>
                        </el-form-item>
                    </el-col>
                </el-row>

                <el-form-item label="解析">
                    <el-input v-model="form.analysis" type="textarea" :rows="2"
                              placeholder="学生查看答卷时展示"/>
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="formVisible = false">取消</el-button>
                <el-button type="primary" :loading="saving" @click="onSubmit">保存</el-button>
            </template>
        </el-dialog>
    </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { questionApi, knowledgePointApi, courseApi } from '@/api/question'

const OPTION_LABELS = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H']

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const total = ref(0)
const courseOptions = ref([])
const knowledgeOptions = ref([])

const query = reactive({
    page: 1, size: 10,
    courseId: null, type: null, difficulty: null,
    knowledgePointId: null, keyword: '', enabled: null
})

const formVisible = ref(false)
const formRef = ref(null)
const form = reactive({
    id: null,
    type: 'SINGLE',
    stem: '',
    options: ['', ''],
    answer: [],
    score: 5,
    difficulty: 'MEDIUM',
    analysis: '',
    courseId: null,
    knowledgePointIds: [],
    enabled: true
})

const isChoice = computed(() => form.type === 'SINGLE' || form.type === 'MULTIPLE')

const rules = {
    type: [{ required: true, message: '请选择题型', trigger: 'change' }],
    stem: [{ required: true, message: '请输入题干', trigger: 'blur' }],
    score: [{ required: true, message: '请输入分值', trigger: 'change' }],
    difficulty: [{ required: true, message: '请选择难度', trigger: 'change' }],
    knowledgePointIds: [{
        required: true, type: 'array', min: 1,
        message: '至少选择一个知识点（否则该题无法计入学情统计）',
        trigger: 'change'
    }]
}

function optionLabel(idx) {
    return OPTION_LABELS[idx] || ''
}

function typeTagType(type) {
    return { SINGLE: 'primary', MULTIPLE: 'success', JUDGE: 'warning', BLANK: 'info' }[type] || ''
}

function toggleAnswer(label, checked) {
    if (checked) {
        if (!form.answer.includes(label)) form.answer = [...form.answer, label].sort()
    } else {
        form.answer = form.answer.filter(a => a !== label)
    }
}

function addOption() {
    if (form.options.length < 8) form.options.push('')
}

function removeOption(idx) {
    if (form.options.length <= 2) return
    const removedLabel = optionLabel(idx)
    form.options.splice(idx, 1)
    // 选项删除后，后面的标识会前移，答案里被删的标识要一并移除
    form.answer = form.answer.filter(a => a !== removedLabel)
}

function onTypeChange(type) {
    form.answer = []
    if (type === 'SINGLE' || type === 'MULTIPLE') {
        if (form.options.length < 2) form.options = ['', '']
    } else if (type === 'JUDGE') {
        form.options = ['正确', '错误']
        form.answer = ['T']
    } else if (type === 'BLANK') {
        form.options = []
        form.answer = []
    }
}

function splitAnswers(text) {
    return (text || '').split(/[；;]/).map(s => s.trim()).filter(Boolean)
}

async function load() {
    loading.value = true
    try {
        const data = await questionApi.page({
            page: query.page,
            size: query.size,
            courseId: query.courseId ?? undefined,
            type: query.type || undefined,
            difficulty: query.difficulty || undefined,
            knowledgePointId: query.knowledgePointId ?? undefined,
            keyword: query.keyword || undefined,
            enabled: query.enabled ?? undefined
        })
        rows.value = data.list
        total.value = data.total
    } catch {
        rows.value = []
        total.value = 0
    } finally {
        loading.value = false
    }
}

async function loadOptions() {
    try {
        courseOptions.value = await courseApi.options()
        knowledgeOptions.value = await knowledgePointApi.options()
    } catch {
        courseOptions.value = []
        knowledgeOptions.value = []
    }
}

function reload() {
    query.page = 1
    load()
}

function resetQuery() {
    Object.assign(query, {
        page: 1, courseId: null, type: null, difficulty: null,
        knowledgePointId: null, keyword: '', enabled: null
    })
    load()
}

function onPageChange(p) { query.page = p; load() }
function onSizeChange(s) { query.size = s; query.page = 1; load() }

function openCreate() {
    Object.assign(form, {
        id: null, type: 'SINGLE', stem: '', options: ['', ''],
        answer: [], score: 5, difficulty: 'MEDIUM', analysis: '',
        courseId: null, knowledgePointIds: [], enabled: true
    })
    formVisible.value = true
}

function openEdit(row) {
    Object.assign(form, {
        id: row.id,
        type: row.type,
        stem: row.stem,
        options: row.type === 'BLANK' ? [] : [...(row.options || [])],
        answer: [...(row.answer || [])],
        score: row.score,
        difficulty: row.difficulty,
        analysis: row.analysis || '',
        courseId: row.courseId,
        knowledgePointIds: (row.knowledgePoints || []).map(k => k.id),
        enabled: row.enabled
    })
    formVisible.value = true
}

async function onSubmit() {
    const valid = await formRef.value?.validate().catch(() => false)
    if (!valid) return

    if (isChoice.value) {
        if (form.options.some(o => !o || !o.trim())) {
            ElMessage.warning('选项内容不能为空')
            return
        }
        if (form.type === 'SINGLE' && form.answer.length !== 1) {
            ElMessage.warning('单选题必须且只能选一个正确答案')
            return
        }
        if (form.type === 'MULTIPLE' && form.answer.length < 2) {
            ElMessage.warning('多选题至少需要两个正确答案')
            return
        }
    }
    if (form.type === 'BLANK' && form.answer.length === 0) {
        ElMessage.warning('填空题至少填写一个可接受答案')
        return
    }

    saving.value = true
    try {
        const payload = {
            type: form.type,
            stem: form.stem,
            options: form.type === 'BLANK' ? [] : form.options.map(o => o.trim()),
            answer: form.type === 'BLANK' ? form.answer : form.answer,
            score: form.score,
            difficulty: form.difficulty,
            analysis: form.analysis || null,
            courseId: form.courseId,
            knowledgePointIds: form.knowledgePointIds,
            enabled: form.enabled
        }
        if (form.id) {
            await questionApi.update(form.id, payload)
            ElMessage.success('已保存')
        } else {
            await questionApi.create(payload)
            ElMessage.success('新增成功')
        }
        formVisible.value = false
        await load()
    } catch {
        // 校验类错误由后端统一返回，拦截器已提示
    } finally {
        saving.value = false
    }
}

async function onToggle(row) {
    try {
        await questionApi.toggleEnabled(row.id, !row.enabled)
        ElMessage.success(row.enabled ? '已停用' : '已启用')
        await load()
    } catch {
        // 拦截器已提示
    }
}

async function onDelete(row) {
    try {
        await ElMessageBox.confirm(`确定删除题目「${(row.stem || '').slice(0, 20)}…」吗？`, '删除题目',
            { type: 'warning' })
    } catch {
        return
    }
    try {
        await questionApi.remove(row.id)
        ElMessage.success('已删除')
        await load()
    } catch {
        // 拦截器已提示
    }
}

onMounted(() => {
    loadOptions()
    load()
})
</script>

<style scoped>
.mb-12 { margin-bottom: 12px; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
.pager { margin-top: 16px; justify-content: flex-end; }
.kp-tag { margin-right: 4px; margin-bottom: 2px; }
.options { width: 100%; }
.option-row { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.option-label { width: 20px; font-weight: 600; color: #909399; }
.hint { font-size: 12px; color: #909399; line-height: 1.6; margin-top: 2px; }
</style>
