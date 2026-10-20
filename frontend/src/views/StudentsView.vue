<template>
    <div>
        <!-- 筛选工具栏 -->
        <el-card shadow="never" class="mb-12">
            <el-form :inline="true" @submit.prevent>
                <el-form-item label="班级">
                    <el-select v-model="query.classId" placeholder="全部班级" clearable style="width: 200px">
                        <el-option
                            v-for="item in classOptions"
                            :key="item.id"
                            :label="`${item.name}（${item.studentCount} 人）`"
                            :value="item.id"
                        />
                    </el-select>
                </el-form-item>

                <el-form-item label="状态">
                    <el-select v-model="query.enabled" placeholder="全部" clearable style="width: 120px">
                        <el-option label="启用" :value="true"/>
                        <el-option label="停用" :value="false"/>
                    </el-select>
                </el-form-item>

                <el-form-item label="关键词">
                    <el-input
                        v-model="query.keyword"
                        placeholder="用户名或姓名"
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

        <!-- 列表 -->
        <el-card shadow="never">
            <template #header>
                <div class="card-header">
                    <span>学生名单（共 {{ total }} 人）</span>
                    <div>
                        <el-button type="primary" :icon="Plus" @click="openCreate">新增学生</el-button>
                        <el-button :icon="Upload" @click="importVisible = true">批量导入</el-button>
                    </div>
                </div>
            </template>

            <el-table v-loading="loading" :data="rows" border stripe>
                <el-table-column type="index" label="#" width="55" align="center"/>
                <el-table-column prop="username" label="用户名" min-width="130"/>
                <el-table-column prop="realName" label="姓名" min-width="100"/>
                <el-table-column label="班级" min-width="160">
                    <template #default="{ row }">
                        <el-tag v-if="row.className" size="small">{{ row.className }}</el-tag>
                        <el-tag v-else size="small" type="warning">未分班</el-tag>
                    </template>
                </el-table-column>
                <el-table-column prop="grade" label="年级" width="90" align="center">
                    <template #default="{ row }">{{ row.grade || '—' }}</template>
                </el-table-column>
                <el-table-column label="状态" width="90" align="center">
                    <template #default="{ row }">
                        <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
                            {{ row.enabled ? '启用' : '停用' }}
                        </el-tag>
                    </template>
                </el-table-column>
                <el-table-column label="操作" width="250" align="center" fixed="right">
                    <template #default="{ row }">
                        <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
                        <el-button link type="warning" size="small" @click="onResetPassword(row)">重置密码</el-button>
                        <el-button link type="danger" size="small" @click="onDelete(row)">删除</el-button>
                    </template>
                </el-table-column>
                <template #empty>
                    <el-empty description="暂无学生数据"/>
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
        <el-dialog v-model="formVisible" :title="form.id ? '编辑学生' : '新增学生'" width="460px">
            <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
                <el-form-item label="用户名" prop="username">
                    <el-input v-model="form.username" placeholder="字母、数字、下划线"/>
                </el-form-item>
                <el-form-item label="姓名" prop="realName">
                    <el-input v-model="form.realName"/>
                </el-form-item>
                <el-form-item v-if="!form.id" label="初始密码" prop="password">
                    <el-input v-model="form.password" placeholder="留空则使用 123456"/>
                </el-form-item>
                <el-form-item label="班级" prop="classId">
                    <el-select v-model="form.classId" placeholder="可不分班" clearable style="width: 100%">
                        <el-option
                            v-for="item in classOptions"
                            :key="item.id"
                            :label="item.name"
                            :value="item.id"
                        />
                    </el-select>
                </el-form-item>
                <el-form-item v-if="form.id" label="状态">
                    <el-switch v-model="form.enabled" active-text="启用" inactive-text="停用"/>
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="formVisible = false">取消</el-button>
                <el-button type="primary" :loading="saving" @click="onSubmit">保存</el-button>
            </template>
        </el-dialog>

        <!-- 批量导入 -->
        <el-dialog v-model="importVisible" title="批量导入学生" width="620px">
            <el-alert type="info" :closable="false" class="mb-12">
                每行一条，逗号分隔：<b>用户名,姓名,年级,班级名称</b><br/>
                后两列可省略。班级不存在会自动创建并归属当前教师；单行出错只跳过该行。
            </el-alert>
            <el-input
                v-model="importText"
                type="textarea"
                :rows="8"
                placeholder="student101,张三,2023,软件工程 2303 班&#10;student102,李四"
            />
            <el-alert
                v-if="importResult"
                class="mt-12"
                :type="importResult.failed > 0 ? 'warning' : 'success'"
                :closable="false"
                show-icon
            >
                <template #title">
                    共 {{ importResult.total }} 行，成功 {{ importResult.success }} 行，失败 {{ importResult.failed }} 行
                </template>
                <ul v-if="importResult.errors?.length" class="err-list">
                    <li v-for="(err, i) in importResult.errors" :key="i">{{ err }}</li>
                </ul>
            </el-alert>
            <template #footer>
                <el-button @click="closeImport">关闭</el-button>
                <el-button type="primary" :loading="importing" @click="onImport">开始导入</el-button>
            </template>
        </el-dialog>
    </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search, Upload } from '@element-plus/icons-vue'
import { studentApi } from '@/api/student'
import { classApi } from '@/api/class'

const loading = ref(false)
const saving = ref(false)
const importing = ref(false)
const rows = ref([])
const total = ref(0)
const classOptions = ref([])

const query = reactive({ page: 1, size: 10, classId: null, keyword: '', enabled: null })

const formVisible = ref(false)
const formRef = ref(null)
const form = reactive({ id: null, username: '', realName: '', password: '', classId: null, enabled: true })

const importVisible = ref(false)
const importText = ref('')
const importResult = ref(null)

const rules = {
    username: [
        { required: true, message: '请输入用户名', trigger: 'blur' },
        { pattern: /^[a-zA-Z0-9_]{3,50}$/, message: '3~50 位字母、数字或下划线', trigger: 'blur' }
    ],
    realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
    password: [{ min: 6, max: 64, message: '密码长度需为 6~64 位', trigger: 'blur' }]
}

async function load() {
    loading.value = true
    try {
        // 空字符串要转成 undefined，否则后端会按 keyword='' 处理
        const params = {
            page: query.page,
            size: query.size,
            classId: query.classId ?? undefined,
            keyword: query.keyword || undefined,
            enabled: query.enabled ?? undefined
        }
        const data = await studentApi.page(params)
        rows.value = data.list
        total.value = data.total
    } catch {
        rows.value = []
        total.value = 0
    } finally {
        loading.value = false
    }
}

async function loadClassOptions() {
    try {
        classOptions.value = await classApi.options()
    } catch {
        classOptions.value = []
    }
}

function reload() {
    query.page = 1
    load()
}

function resetQuery() {
    query.classId = null
    query.keyword = ''
    query.enabled = null
    reload()
}

function onPageChange(page) {
    query.page = page
    load()
}

function onSizeChange(size) {
    query.size = size
    query.page = 1
    load()
}

function openCreate() {
    Object.assign(form, { id: null, username: '', realName: '', password: '', classId: null, enabled: true })
    formVisible.value = true
}

function openEdit(row) {
    Object.assign(form, {
        id: row.id,
        username: row.username,
        realName: row.realName,
        password: '',
        classId: row.classId,
        enabled: row.enabled
    })
    formVisible.value = true
}

async function onSubmit() {
    const valid = await formRef.value?.validate().catch(() => false)
    if (!valid) return

    saving.value = true
    try {
        if (form.id) {
            await studentApi.update(form.id, {
                username: form.username,
                realName: form.realName,
                classId: form.classId,
                enabled: form.enabled
            })
            ElMessage.success('已保存')
        } else {
            await studentApi.create({
                username: form.username,
                realName: form.realName,
                password: form.password || null,
                classId: form.classId
            })
            ElMessage.success('新增成功')
        }
        formVisible.value = false
        await Promise.all([load(), loadClassOptions()])
    } catch {
        // 错误提示由拦截器统一处理（如用户名重复 409）
    } finally {
        saving.value = false
    }
}

async function onResetPassword(row) {
    try {
        await ElMessageBox.confirm(
            `确定将「${row.realName}」的密码重置为初始密码 123456 吗？重置后该学生的登录状态会立即失效。`,
            '重置密码',
            { type: 'warning' }
        )
    } catch {
        return
    }
    try {
        await studentApi.resetPassword(row.id)
        ElMessage.success('密码已重置为 123456')
    } catch {
        // 拦截器已提示
    }
}

async function onDelete(row) {
    try {
        await ElMessageBox.confirm(
            `确定删除学生「${row.realName}」吗？若该学生已有考试作答记录，系统会拒绝删除并提示改为停用。`,
            '删除学生',
            { type: 'warning' }
        )
    } catch {
        return
    }
    try {
        await studentApi.remove(row.id)
        ElMessage.success('已删除')
        await Promise.all([load(), loadClassOptions()])
    } catch {
        // 拦截器已提示
    }
}

async function onImport() {
    if (!importText.value.trim()) {
        ElMessage.warning('请先粘贴要导入的内容')
        return
    }
    importing.value = true
    try {
        importResult.value = await studentApi.import(importText.value)
        await Promise.all([load(), loadClassOptions()])
    } catch {
        // 拦截器已提示
    } finally {
        importing.value = false
    }
}

function closeImport() {
    importVisible.value = false
    importText.value = ''
    importResult.value = null
}

onMounted(() => {
    loadClassOptions()
    load()
})
</script>

<style scoped>
.mb-12 {
    margin-bottom: 12px;
}

.mt-12 {
    margin-top: 12px;
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

.err-list {
    margin: 6px 0 0;
    padding-left: 18px;
    max-height: 160px;
    overflow-y: auto;
}
</style>
