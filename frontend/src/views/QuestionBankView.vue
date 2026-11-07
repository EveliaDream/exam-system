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
                    <div>
                        <el-button :icon="Upload" @click="importVisible = true">Excel 导入</el-button>
                        <el-button type="primary" :icon="Plus" @click="openCreate">新增题目</el-button>
                    </div>
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

                <el-form-item label="配图">
                    <div class="image-field">
                        <el-upload
                            v-if="!form.imageUrl"
                            :show-file-list="false"
                            :auto-upload="false"
                            accept=".png,.jpg,.jpeg,.gif,.webp"
                            :on-change="onImageChange"
                        >
                            <el-button :icon="Picture" :loading="uploading">上传图片</el-button>
                        </el-upload>
                        <template v-else>
                            <el-image :src="form.imageUrl" fit="contain" class="image-preview"
                                      :preview-src-list="[form.imageUrl]" preview-teleported/>
                            <el-button link type="danger" @click="form.imageUrl = ''">移除</el-button>
                        </template>
                        <div class="hint">可选。支持 png/jpg/gif/webp，不超过 2MB；组卷时会一并快照。</div>
                    </div>
                </el-form-item>

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

        <!-- Excel 批量导入 -->
        <el-dialog v-model="importVisible" title="题库批量导入" width="620px" :close-on-click-modal="false"
                   @closed="resetImport">
            <el-alert type="info" :closable="false" show-icon class="mb-12"
                      title="逐行导入，某一行出错不影响其他行">
                先下载模板按格式填写；知识点与课程列必须与系统中已有名称完全一致，
                否则该行会被跳过并提示原因。单次最多 500 行。
            </el-alert>

            <div class="import-actions">
                <el-button :icon="Download" @click="onDownloadTemplate">下载模板</el-button>
                <span class="hint">建议先下载模板，示例行可直接覆盖</span>
            </div>

            <el-upload
                drag
                :auto-upload="false"
                :limit="1"
                accept=".xlsx"
                :on-change="onFileChange"
                :on-remove="() => (importFile = null)"
                :file-list="fileList"
                class="mb-12"
            >
                <el-icon class="el-icon--upload"><UploadFilled/></el-icon>
                <div class="el-upload__text">把 .xlsx 文件拖到这里，或<em>点击选择</em></div>
                <template #tip>
                    <div class="hint">仅支持 .xlsx（.xls 请先在 Excel 中另存为 .xlsx）</div>
                </template>
            </el-upload>

            <template v-if="importResult">
                <el-divider/>
                <el-descriptions :column="3" border size="small">
                    <el-descriptions-item label="总行数">{{ importResult.total }}</el-descriptions-item>
                    <el-descriptions-item label="成功">
                        <span class="ok">{{ importResult.success }}</span>
                    </el-descriptions-item>
                    <el-descriptions-item label="失败">
                        <span :class="importResult.failed ? 'bad' : ''">{{ importResult.failed }}</span>
                    </el-descriptions-item>
                </el-descriptions>
                <div v-if="importResult.errors?.length" class="error-box">
                    <div class="error-title">失败明细</div>
                    <div v-for="(e, i) in importResult.errors" :key="i" class="error-line">{{ e }}</div>
                </div>
            </template>

            <template #footer>
                <el-button @click="importVisible = false">关闭</el-button>
                <el-button type="primary" :loading="importing" :disabled="!importFile" @click="onImport">
                    开始导入
                </el-button>
            </template>
        </el-dialog>
    </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download, Picture, Plus, Refresh, Search, Upload, UploadFilled } from '@element-plus/icons-vue'
import { questionApi, knowledgePointApi, courseApi } from '@/api/question'
import { fileApi } from '@/api/file'
import { downloadFile } from '@/utils/download'

const OPTION_LABELS = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H']

const loading = ref(false)
const saving = ref(false)
const uploading = ref(false)
const rows = ref([])
const total = ref(0)
const courseOptions = ref([])
const knowledgeOptions = ref([])

// ===== Excel 批量导入 =====
const importVisible = ref(false)
const importing = ref(false)
const importFile = ref(null)
const fileList = ref([])
const importResult = ref(null)

function onFileChange(file) {
    importFile.value = file.raw
    fileList.value = [file]
}

function resetImport() {
    importFile.value = null
    fileList.value = []
    importResult.value = null
}

async function onDownloadTemplate() {
    try {
        const name = await downloadFile('/questions/import/template', '题库导入模板.xlsx')
        ElMessage.success(`已下载 ${name}`)
    } catch (e) {
        ElMessage.error(e.message || '模板下载失败')
    }
}

async function onImport() {
    if (!importFile.value) {
        ElMessage.warning('请先选择 .xlsx 文件')
        return
    }
    importing.value = true
    importResult.value = null
    try {
        const res = await questionApi.importQuestions(importFile.value)
        importResult.value = res
        if (res.success > 0) {
            ElMessage.success(`成功导入 ${res.success} 道题`)
            await load()
        }
        if (res.failed > 0) {
            ElMessage.warning(`${res.failed} 行未导入，请查看下方失败明细`)
        }
    } catch {
        // 文件格式等错误由拦截器提示
    } finally {
        importing.value = false
    }
}

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
    imageUrl: '',
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

/** 上传配图：先传到 /api/files/image，拿到 url 再随题目一起保存 */
async function onImageChange(file) {
    const raw = file.raw
    if (!raw) return
    if (raw.size > 2 * 1024 * 1024) {
        ElMessage.warning('图片不能超过 2MB')
        return
    }
    uploading.value = true
    try {
        const res = await fileApi.uploadImage(raw)
        form.imageUrl = res.url
        ElMessage.success('图片已上传')
    } catch {
        // 类型/大小等错误由拦截器提示
    } finally {
        uploading.value = false
    }
}

function openCreate() {
    Object.assign(form, {
        id: null, type: 'SINGLE', stem: '', options: ['', ''],
        answer: [], score: 5, difficulty: 'MEDIUM', analysis: '', imageUrl: '',
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
        imageUrl: row.imageUrl || '',
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
            imageUrl: form.imageUrl || null,
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
.import-actions { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
.image-field { display: flex; align-items: flex-start; gap: 12px; flex-wrap: wrap; }
.image-preview { width: 120px; height: 80px; border: 1px solid #dcdfe6; border-radius: 4px; }
.ok { color: #67c23a; font-weight: 600; }
.bad { color: #f56c6c; font-weight: 600; }
.error-box {
  margin-top: 12px; max-height: 220px; overflow-y: auto;
  background: #fef0f0; border: 1px solid #fbc4c4; border-radius: 4px; padding: 8px 12px;
}
.error-title { font-weight: 600; color: #f56c6c; margin-bottom: 6px; }
.error-line { font-size: 12px; color: #c45656; line-height: 1.8; }
</style>
