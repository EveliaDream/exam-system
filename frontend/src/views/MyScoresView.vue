<template>
    <div>
        <el-card shadow="never">
            <template #header>
                <div class="card-header">
                    <span>我的成绩（共 {{ rows.length }} 次）</span>
                    <el-button link type="primary" @click="$router.push('/my-exams')">去考试</el-button>
                </div>
            </template>

            <el-table v-loading="loading" :data="rows" border stripe>
                <el-table-column prop="examTitle" label="考试" min-width="220" show-overflow-tooltip/>
                <el-table-column prop="courseName" label="课程" width="150">
                    <template #default="{ row }">{{ row.courseName || '—' }}</template>
                </el-table-column>
                <el-table-column label="得分" width="140" align="center">
                    <template #default="{ row }">
                        <span class="score" :class="{ pass: row.passed, fail: !row.passed }">
                            {{ row.objectiveScore }}
                        </span>
                        <span class="sub"> / {{ row.totalScore }}</span>
                    </template>
                </el-table-column>
                <el-table-column label="结果" width="90" align="center">
                    <template #default="{ row }">
                        <el-tag size="small" :type="row.passed ? 'success' : 'danger'">
                            {{ row.passed ? '通过' : '未通过' }}
                        </el-tag>
                    </template>
                </el-table-column>
                <el-table-column label="交卷方式" width="120" align="center">
                    <template #default="{ row }">
                        <el-tag size="small" :type="row.status === 'AUTO_SUBMITTED' ? 'warning' : 'info'">
                            {{ row.statusLabel }}
                        </el-tag>
                    </template>
                </el-table-column>
                <el-table-column prop="submitTime" label="交卷时间" width="180"/>
                <el-table-column label="操作" width="100" align="center">
                    <template #default="{ row }">
                        <el-button link type="primary" size="small" @click="$router.push(`/my-records/${row.recordId}`)">
                            查看结果
                        </el-button>
                    </template>
                </el-table-column>
                <template #empty>
                    <el-empty description="还没有交卷记录"/>
                </template>
            </el-table>
        </el-card>
    </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { takingApi } from '@/api/answer'

const loading = ref(false)
const rows = ref([])

async function load() {
    loading.value = true
    try {
        rows.value = await takingApi.myRecords()
    } catch {
        rows.value = []
    } finally {
        loading.value = false
    }
}

onMounted(load)
</script>

<style scoped>
.card-header { display: flex; align-items: center; justify-content: space-between; }
.score { font-size: 16px; font-weight: 700; }
.score.pass { color: #67c23a; }
.score.fail { color: #f56c6c; }
.sub { color: #909399; font-size: 12px; }
</style>
