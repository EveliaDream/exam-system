<template>
    <div>
        <el-alert
            type="info"
            :closable="false"
            show-icon
            class="mb-12"
            title="知识点是学情分析的主干"
        >
            系统通过统计学生「答对/答错题目所覆盖的知识点」来判断其掌握程度，
            因此知识点必须集中维护、录题时从下拉框选择，不能自由输入。
        </el-alert>

        <el-card shadow="never" class="mb-12">
            <el-form :inline="true" @submit.prevent>
                <el-form-item label="课程">
                    <el-select v-model="query.courseId" placeholder="全部（含通用）" clearable style="width: 200px">
                        <el-option
                            v-for="c in courseOptions"
                            :key="c.id"
                            :label="c.name"
                            :value="c.id"
                        />
                    </el-select>
                </el-form-item>
                <el-form-item label="名称">
                    <el-input
                        v-model="query.keyword"
                        placeholder="模糊查询"
                        clearable
                        style="width: 200px"
                        @keyup.enter="reload"
                    />
                </el-form-item>
                <el-form-item>
                    <el-button type="primary" :icon="Search" @click="reload">查询</el-button>
                    <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
                </el-form-item>
            </el-form>
        </el-card>

        <el-card shadow="never">
            <template #header>
                <div class="card-header">
                    <span>知识点（共 {{ total }} 个）</span>
                    <el-button type="primary" :icon="Plus" @click="openCreate">新增知识点</el-button>
                </div>
            </template>

            <el-table v-loading="loading" :data="rows" border stripe>
                <el-table-column type="index" label="#" width="55" align="center"/>
                <el-table-column prop="name" label="知识点名称" min-width="180"/>
                <el-table-column label="所属课程" width="160">
                    <template #default="{ row }">
                        <el-tag v-if="row.courseId" size="small">{{ courseName(row.courseId) }}</el-tag>
                        <el-tag v-else size="small" type="info">通用</el-tag>
                    </template>
                </el-table-column>
                <el-table-column prop="description" label="说明" min-width="220" show-overflow-tooltip>
                    <template #default="{ row }">{{ row.description || '—' }}</template>
                </el-table-column>
                <el-table-column label="引用题数" width="100" align="center">
                    <template #default="{ row }">
                        <el-tag size="small" :type="row.questionCount > 0 ? 'primary' : 'info'">
                            {{ row.questionCount }} 题
                        </el-tag>
                    </template>
                </el-table-column>
                <el-table-column label="排序" width="80" align="center">
                    <template #default="{ row }">{{ row.sortOrder }}</template>
                </el-table-column>
                <el-table-column label="操作" width="140" align="center">
                    <template #default="{ row }">
                        <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
                        <el-button link type="danger" size="small" @click="onDelete(row)">删除</el-button>
                    </template>
                </el-table-column>
                <template #empty>
                    <el-empty description="暂无知识点，请先新增"/>
                </template>
            </el-table>

            <el-pagination
                class="pager"
                background
                layout="total, prev, pager, next"
                :total="total"
                :current-page="query.page"
                :page-size="query.size"
                @current-change="onPageChange"
            />
        </el-card>

        <el-dialog v-model="formVisible" :title="form.id ? '编辑知识点' : '新增知识点'" width="480px">
            <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
                <el-form-item label="名称" prop="name">
                    <el-input v-model="form.name" placeholder="如：面向对象·多态实现"/>
                </el-form-item>
                <el-form-item label="所属课程">
                    <el-select v-model="form.courseId" placeholder="不选表示通用知识点" clearable style="width: 100%">
                        <el-option
                            v-for="c in courseOptions"
                            :key="c.id"
                            :label="c.name"
                            :value="c.id"
                        />
                    </el-select>
                </el-form-item>
                <el-form-item label="说明">
                    <el-input v-model="form.description" type="textarea" :rows="3"
                              placeholder="写清该知识点的考查范围，便于后续讲评时对照"/>
                </el-form-item>
                <el-form-item label="排序">
                    <el-input-number v-model="form.sortOrder" :min="0" :max="999"/>
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
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { knowledgePointApi, courseApi } from '@/api/question'

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const total = ref(0)
const courseOptions = ref([])

const query = reactive({ page: 1, size: 50, courseId: null, keyword: '' })

const formVisible = ref(false)
const formRef = ref(null)
const form = reactive({ id: null, name: '', courseId: null, description: '', sortOrder: 0 })

const rules = {
    name: [
        { required: true, message: '请输入知识点名称', trigger: 'blur' },
        { max: 100, message: '名称不能超过 100 个字符', trigger: 'blur' }
    ]
}

function courseName(id) {
    return courseOptions.value.find(c => c.id === id)?.name || '—'
}

async function load() {
    loading.value = true
    try {
        const data = await knowledgePointApi.page({
            page: query.page,
            size: query.size,
            courseId: query.courseId ?? undefined,
            keyword: query.keyword || undefined
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

async function loadCourseOptions() {
    try {
        courseOptions.value = await courseApi.options()
    } catch {
        courseOptions.value = []
    }
}

function reload() {
    query.page = 1
    load()
}

function resetQuery() {
    query.courseId = null
    query.keyword = ''
    reload()
}

function onPageChange(p) {
    query.page = p
    load()
}

function openCreate() {
    Object.assign(form, { id: null, name: '', courseId: null, description: '', sortOrder: 0 })
    formVisible.value = true
}

function openEdit(row) {
    Object.assign(form, {
        id: row.id,
        name: row.name,
        courseId: row.courseId,
        description: row.description || '',
        sortOrder: row.sortOrder ?? 0
    })
    formVisible.value = true
}

async function onSubmit() {
    const valid = await formRef.value?.validate().catch(() => false)
    if (!valid) return
    saving.value = true
    try {
        const payload = {
            name: form.name,
            courseId: form.courseId,
            description: form.description || null,
            sortOrder: form.sortOrder
        }
        if (form.id) {
            await knowledgePointApi.update(form.id, payload)
            ElMessage.success('已保存')
        } else {
            await knowledgePointApi.create(payload)
            ElMessage.success('新增成功')
        }
        formVisible.value = false
        await load()
    } catch {
        // 重名等错误由拦截器统一提示
    } finally {
        saving.value = false
    }
}

async function onDelete(row) {
    if (row.questionCount > 0) {
        await ElMessageBox.alert(
            `「${row.name}」已被 ${row.questionCount} 道题引用，删除会导致学情统计出现空洞，系统会拒绝删除。请先解除题目关联。`,
            '无法删除',
            { type: 'warning' }
        )
        return
    }
    try {
        await ElMessageBox.confirm(`确定删除知识点「${row.name}」吗？`, '删除知识点', { type: 'warning' })
    } catch {
        return
    }
    try {
        await knowledgePointApi.remove(row.id)
        ElMessage.success('已删除')
        await load()
    } catch {
        // 拦截器已提示
    }
}

onMounted(() => {
    loadCourseOptions()
    load()
})
</script>

<style scoped>
.mb-12 { margin-bottom: 12px; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
