<template>
    <div>
        <el-alert
            type="info" :closable="false" show-icon class="mb-12"
            title="考试状态由服务端按时间窗计算"
        >
            「未开始 / 进行中 / 已结束」以服务器时间为准，修改本机时间不会改变状态。
            在线作答功能将在下一阶段开放。
        </el-alert>

        <el-card shadow="never">
            <template #header>
                <span>我的考试（共 {{ rows.length }} 场）</span>
            </template>

            <el-table v-loading="loading" :data="rows" border stripe>
                <el-table-column prop="title" label="考试标题" min-width="220" show-overflow-tooltip/>
                <el-table-column prop="courseName" label="课程" width="150">
                    <template #default="{ row }">{{ row.courseName || '—' }}</template>
                </el-table-column>
                <el-table-column label="状态" width="100" align="center">
                    <template #default="{ row }">
                        <el-tag size="small" :type="phaseType(row.phase)">{{ row.phaseLabel }}</el-tag>
                    </template>
                </el-table-column>
                <el-table-column label="题目 / 总分" width="120" align="center">
                    <template #default="{ row }">{{ row.questionCount }} 题 / {{ row.totalScore }} 分</template>
                </el-table-column>
                <el-table-column label="作答时长" width="100" align="center">
                    <template #default="{ row }">{{ row.duration }} 分钟</template>
                </el-table-column>
                <el-table-column label="考试时间" min-width="230">
                    <template #default="{ row }">
                        <div class="sub">{{ row.startTime }}</div>
                        <div class="sub">至 {{ row.endTime }}</div>
                    </template>
                </el-table-column>
                <el-table-column label="操作" width="120" align="center">
                    <template #default="{ row }">
                        <el-button v-if="row.canEnter" type="primary" size="small" disabled>
                            进入考试（下一阶段）
                        </el-button>
                        <span v-else class="sub">不可进入</span>
                    </template>
                </el-table-column>
                <template #empty>
                    <el-empty description="暂无面向你所在班级的考试"/>
                </template>
            </el-table>
        </el-card>
    </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { myExamApi } from '@/api/exam'

const loading = ref(false)
const rows = ref([])

function phaseType(p) {
    return { ONGOING: 'success', NOT_STARTED: 'info', ENDED: 'warning' }[p] || ''
}

async function load() {
    loading.value = true
    try {
        rows.value = await myExamApi.list()
    } catch {
        rows.value = []
    } finally {
        loading.value = false
    }
}

onMounted(load)
</script>

<style scoped>
.mb-12 { margin-bottom: 12px; }
.sub { font-size: 12px; color: #909399; line-height: 1.6; }
</style>
