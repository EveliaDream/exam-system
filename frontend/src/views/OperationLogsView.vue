<template>
    <div>
        <el-card shadow="never" class="mb-12">
            <el-form :inline="true" @submit.prevent>
                <el-form-item label="动作">
                    <el-select v-model="query.action" placeholder="全部" clearable filterable style="width: 240px">
                        <el-option v-for="a in actionOptions" :key="a" :label="a" :value="a"/>
                    </el-select>
                </el-form-item>
                <el-form-item label="关键字">
                    <el-input v-model="query.keyword" placeholder="操作人或对象" clearable style="width: 180px"
                              @keyup.enter="reload"/>
                </el-form-item>
                <el-form-item label="时间范围">
                    <el-date-picker
                        v-model="range"
                        type="datetimerange"
                        range-separator="至"
                        start-placeholder="开始"
                        end-placeholder="结束"
                        value-format="YYYY-MM-DD HH:mm:ss"
                        style="width: 340px"
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
                    <span>操作日志（共 {{ total }} 条）</span>
                    <span class="sub">只记录成功的写操作；日志不可修改或删除</span>
                </div>
            </template>

            <el-table v-loading="loading" :data="rows" border stripe size="small">
                <el-table-column prop="createdAt" label="时间" width="180"/>
                <el-table-column prop="username" label="操作人" width="120">
                    <template #default="{ row }">{{ row.username || '—' }}</template>
                </el-table-column>
                <el-table-column label="动作" min-width="240">
                    <template #default="{ row }">
                        <el-tag size="small" :type="actionType(row.action)">{{ actionLabel(row.action) }}</el-tag>
                        <span class="route">{{ row.action }}</span>
                    </template>
                </el-table-column>
                <el-table-column prop="target" label="操作对象" min-width="220" show-overflow-tooltip/>
                <el-table-column prop="ip" label="IP" width="140">
                    <template #default="{ row }">{{ row.ip || '—' }}</template>
                </el-table-column>
                <template #empty>
                    <el-empty description="暂无日志"/>
                </template>
            </el-table>

            <el-pagination
                class="pager"
                background
                layout="total, prev, pager, next"
                :total="total"
                :current-page="query.page"
                :page-size="query.size"
                @current-change="p => { query.page = p; load() }"
            />
        </el-card>
    </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'
import { logApi } from '@/api/log'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const actionOptions = ref([])
const range = ref([])
const query = reactive({ page: 1, size: 20, action: null, keyword: '' })

const METHOD_LABELS = {
    POST: '新增',
    PUT: '修改',
    DELETE: '删除',
    PATCH: '修改'
}

/** 把 "PUT /api/exams/{id}/publish" 变成中文短标签，原始路由仍显示在后面 */
function actionLabel(action) {
    if (!action) return '—'
    const [method, path] = action.split(' ')
    const prefix = METHOD_LABELS[method] || method
    if (path?.includes('/publish')) return '发布考试'
    if (path?.includes('/finish')) return '结束考试'
    if (path?.includes('/questions/random')) return '随机组卷'
    if (path?.includes('/questions')) return '组卷'
    if (path?.includes('/exams')) return prefix + '考试'
    if (path?.includes('/questions/import')) return '导入题库'
    if (path?.includes('/login')) return '登录'
    if (path?.includes('/auth/password')) return '修改密码'
    if (path?.includes('/answers')) return '保存答案'
    if (path?.includes('/submit')) return '交卷'
    if (path?.includes('/files/image')) return '上传图片'
    if (path?.includes('/students')) return prefix + '学生'
    if (path?.includes('/classes')) return prefix + '班级'
    if (path?.includes('/knowledge-points')) return prefix + '知识点'
    return prefix
}

function actionType(action) {
    if (!action) return 'info'
    if (action.startsWith('DELETE')) return 'danger'
    if (action.startsWith('POST')) return 'success'
    if (action.startsWith('PUT')) return 'warning'
    return 'info'
}

async function load() {
    loading.value = true
    try {
        const data = await logApi.page({
            page: query.page,
            size: query.size,
            action: query.action || undefined,
            keyword: query.keyword || undefined,
            from: range.value?.[0] || undefined,
            to: range.value?.[1] || undefined
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
    query.action = null
    query.keyword = ''
    range.value = []
    reload()
}

onMounted(async () => {
    load()
    try {
        actionOptions.value = await logApi.actions()
    } catch {
        actionOptions.value = []
    }
})
</script>

<style scoped>
.mb-12 { margin-bottom: 12px; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
.pager { margin-top: 16px; justify-content: flex-end; }
.sub { font-size: 12px; color: #909399; }
.route { margin-left: 8px; font-size: 12px; color: #909399; }
</style>
