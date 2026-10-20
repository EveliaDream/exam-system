<template>
    <div>
        <el-card shadow="never" class="mb-12">
            <el-form :inline="true" @submit.prevent>
                <el-form-item label="班级名称">
                    <el-input
                        v-model="query.keyword"
                        placeholder="支持模糊查询"
                        clearable
                        style="width: 220px"
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
                    <span>我的班级（共 {{ total }} 个）</span>
                    <el-button type="primary" :icon="Plus" @click="openCreate">新增班级</el-button>
                </div>
            </template>

            <el-table v-loading="loading" :data="rows" border stripe>
                <el-table-column type="index" label="#" width="55" align="center"/>
                <el-table-column prop="name" label="班级名称" min-width="200"/>
                <el-table-column prop="grade" label="年级" width="100" align="center">
                    <template #default="{ row }">{{ row.grade || '—' }}</template>
                </el-table-column>
                <el-table-column prop="teacherName" label="负责教师" width="120"/>
                <el-table-column label="学生人数" width="110" align="center">
                    <template #default="{ row }">
                        <el-tag size="small" :type="row.studentCount > 0 ? 'primary' : 'info'">
                            {{ row.studentCount }} 人
                        </el-tag>
                    </template>
                </el-table-column>
                <el-table-column label="操作" width="160" align="center">
                    <template #default="{ row }">
                        <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
                        <el-button link type="danger" size="small" @click="onDelete(row)">删除</el-button>
                    </template>
                </el-table-column>
                <template #empty>
                    <el-empty description="暂无班级，请先新增"/>
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

        <el-dialog v-model="formVisible" :title="form.id ? '编辑班级' : '新增班级'" width="420px">
            <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
                <el-form-item label="班级名称" prop="name">
                    <el-input v-model="form.name" placeholder="如：软件工程 2303 班"/>
                </el-form-item>
                <el-form-item label="年级" prop="grade">
                    <el-input v-model="form.grade" placeholder="如：2023"/>
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
import { classApi } from '@/api/class'

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const total = ref(0)

const query = reactive({ page: 1, size: 10, keyword: '' })

const formVisible = ref(false)
const formRef = ref(null)
const form = reactive({ id: null, name: '', grade: '' })

const rules = {
    name: [
        { required: true, message: '请输入班级名称', trigger: 'blur' },
        { max: 50, message: '班级名称不能超过 50 个字符', trigger: 'blur' }
    ]
}

async function load() {
    loading.value = true
    try {
        const data = await classApi.page({
            page: query.page,
            size: query.size,
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

function reload() {
    query.page = 1
    load()
}

function resetQuery() {
    query.keyword = ''
    reload()
}

function onPageChange(page) {
    query.page = page
    load()
}

function openCreate() {
    Object.assign(form, { id: null, name: '', grade: '' })
    formVisible.value = true
}

function openEdit(row) {
    Object.assign(form, { id: row.id, name: row.name, grade: row.grade || '' })
    formVisible.value = true
}

async function onSubmit() {
    const valid = await formRef.value?.validate().catch(() => false)
    if (!valid) return

    saving.value = true
    try {
        const payload = { name: form.name, grade: form.grade || null }
        if (form.id) {
            await classApi.update(form.id, payload)
            ElMessage.success('已保存')
        } else {
            await classApi.create(payload)
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
    const warning = row.studentCount > 0
        ? `「${row.name}」下还有 ${row.studentCount} 名学生，系统会拒绝删除。请先把学生转到其他班级。`
        : `确定删除班级「${row.name}」吗？`
    try {
        await ElMessageBox.confirm(warning, '删除班级', {
            type: warning.startsWith('确定') ? 'warning' : 'info'
        })
    } catch {
        return
    }
    try {
        await classApi.remove(row.id)
        ElMessage.success('已删除')
        await load()
    } catch {
        // 班级下有学生时后端返回 30202，拦截器已提示
    }
}

onMounted(load)
</script>

<style scoped>
.mb-12 {
    margin-bottom: 12px;
}

.card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
}

.pager {
    margin-top: 16px;
    justify-content: flex-end;
}
</style>
