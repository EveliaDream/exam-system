<template>
    <div>
        <el-card shadow="never" class="mb-12">
            <el-form :inline="true" @submit.prevent>
                <el-form-item label="状态">
                    <el-select v-model="query.status" placeholder="全部" clearable style="width: 130px">
                        <el-option label="草稿" value="DRAFT"/>
                        <el-option label="已发布" value="PUBLISHED"/>
                        <el-option label="已结束" value="FINISHED"/>
                    </el-select>
                </el-form-item>
                <el-form-item label="标题">
                    <el-input v-model="query.keyword" placeholder="模糊查询" clearable style="width: 200px"
                              @keyup.enter="reload"/>
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
                    <span>我的考试（共 {{ total }} 场）</span>
                    <el-button type="primary" :icon="Plus" @click="onCreate">新建考试</el-button>
                </div>
            </template>

            <el-table v-loading="loading" :data="rows" border stripe>
                <el-table-column prop="title" label="考试标题" min-width="200" show-overflow-tooltip/>
                <el-table-column label="状态" width="90" align="center">
                    <template #default="{ row }">
                        <el-tag size="small" :type="statusType(row.status)">{{ row.statusLabel }}</el-tag>
                    </template>
                </el-table-column>
                <el-table-column label="题目 / 总分" width="140" align="center">
                    <template #default="{ row }">
                        <span :class="{ 'score-bad': row.questionScoreSum !== row.totalScore }">
                            {{ row.questionCount }} 题 / {{ row.questionScoreSum }} 分
                        </span>
                        <div class="sub">设定总分 {{ row.totalScore }}</div>
                    </template>
                </el-table-column>
                <el-table-column label="参考班级" min-width="180">
                    <template #default="{ row }">
                        <template v-if="row.classNames.length">
                            <el-tag v-for="n in row.classNames" :key="n" size="small" class="tag-gap">{{ n }}</el-tag>
                        </template>
                        <el-text v-else type="warning" size="small">未绑定</el-text>
                    </template>
                </el-table-column>
                <el-table-column label="考试时间" min-width="220">
                    <template #default="{ row }">
                        <div class="sub">{{ row.startTime }}</div>
                        <div class="sub">至 {{ row.endTime }}</div>
                    </template>
                </el-table-column>
                <el-table-column label="时长" width="80" align="center">
                    <template #default="{ row }">{{ row.duration }} 分</template>
                </el-table-column>
                <el-table-column label="操作" width="260" align="center" fixed="right">
                    <template #default="{ row }">
                        <el-button link type="primary" size="small" @click="goEdit(row)">编辑</el-button>
                        <el-button v-if="row.status === 'DRAFT'" link type="success" size="small"
                                   @click="onPublish(row)">发布</el-button>
                        <el-button v-if="row.status === 'PUBLISHED'" link type="warning" size="small"
                                   @click="onFinish(row)">结束</el-button>
                        <el-button v-if="row.status === 'DRAFT'" link type="danger" size="small"
                                   @click="onDelete(row)">删除</el-button>
                    </template>
                </el-table-column>
                <template #empty>
                    <el-empty description="还没有考试，点击右上角新建"/>
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
    </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { examApi } from '@/api/exam'

const router = useRouter()
const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, status: null, keyword: '' })

function statusType(s) {
    return { DRAFT: 'info', PUBLISHED: 'success', FINISHED: 'warning' }[s] || ''
}

async function load() {
    loading.value = true
    try {
        const data = await examApi.page({
            page: query.page,
            size: query.size,
            status: query.status || undefined,
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

function reload() { query.page = 1; load() }
function resetQuery() { query.status = null; query.keyword = ''; reload() }
function onPageChange(p) { query.page = p; load() }

/** 新建：先建一个草稿，再跳转到编辑页组卷 */
async function onCreate() {
    try {
        const exam = await examApi.create({
            title: '未命名考试',
            totalScore: 100,
            passScore: 60,
            startTime: fmt(addHours(new Date(), 1)),
            endTime: fmt(addHours(new Date(), 25)),
            duration: 60,
            shuffleQuestion: false,
            shuffleOption: false,
            maxAttempt: 1
        })
        ElMessage.success('已创建草稿，请完善设置并组卷')
        router.push(`/exams/${exam.id}`)
    } catch {
        // 拦截器已提示
    }
}

function addHours(d, h) {
    const x = new Date(d)
    x.setHours(x.getHours() + h)
    return x
}

function fmt(d) {
    const p = n => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:00`
}

function goEdit(row) {
    router.push(`/exams/${row.id}`)
}

async function onPublish(row) {
    try {
        await ElMessageBox.confirm(
            `发布后题目与总分将被冻结，学生可在时间窗内作答。确定发布「${row.title}」吗？`,
            '发布考试', { type: 'warning' })
    } catch { return }
    try {
        await examApi.publish(row.id)
        ElMessage.success('已发布')
        await load()
    } catch {
        // 后端会返回具体原因（如分值不一致、未绑定班级）
    }
}

async function onFinish(row) {
    try {
        await ElMessageBox.confirm('结束后学生不能再进入该考试，确定结束吗？', '结束考试', { type: 'warning' })
    } catch { return }
    try {
        await examApi.finish(row.id)
        ElMessage.success('已结束')
        await load()
    } catch {
        // 拦截器已提示
    }
}

async function onDelete(row) {
    try {
        await ElMessageBox.confirm(`确定删除考试「${row.title}」吗？`, '删除考试', { type: 'warning' })
    } catch { return }
    try {
        await examApi.remove(row.id)
        ElMessage.success('已删除')
        await load()
    } catch {
        // 拦截器已提示
    }
}

onMounted(load)
</script>

<style scoped>
.mb-12 { margin-bottom: 12px; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
.pager { margin-top: 16px; justify-content: flex-end; }
.sub { font-size: 12px; color: #909399; line-height: 1.6; }
.tag-gap { margin-right: 4px; margin-bottom: 2px; }
.score-bad { color: #e6a23c; font-weight: 600; }
</style>
