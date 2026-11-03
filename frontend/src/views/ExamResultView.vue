<template>
    <div v-loading="loading">
        <el-card v-if="result" shadow="never">
            <el-result
                :icon="result.passed ? 'success' : 'error'"
                :title="result.passed ? '考试通过' : '未达到及格线'"
                :sub-title="`${result.examTitle}`"
            >
                <template #extra>
                    <div class="score-box">
                        <div class="score">{{ result.objectiveScore }}</div>
                        <div class="score-sub">满分 {{ result.totalScore }} · 及格线 {{ result.passScore }}</div>
                    </div>
                </template>
            </el-result>

            <el-descriptions :column="2" border class="mt-16">
                <el-descriptions-item label="答对题数">
                    {{ result.correctCount }} / {{ result.questionCount }}
                </el-descriptions-item>
                <el-descriptions-item label="交卷方式">
                    <el-tag size="small" :type="result.autoSubmitted ? 'warning' : 'success'">
                        {{ result.autoSubmitted ? '超时自动交卷' : '主动交卷' }}
                    </el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="开始作答">{{ result.startTime }}</el-descriptions-item>
                <el-descriptions-item label="交卷时间">{{ result.submitTime }}</el-descriptions-item>
                <el-descriptions-item label="切屏次数">
                    {{ result.switchScreenCount }}
                    <span class="hint">（仅作参考，不作为作弊判定依据）</span>
                </el-descriptions-item>
                <el-descriptions-item label="记录编号">#{{ result.recordId }}</el-descriptions-item>
            </el-descriptions>

            <div class="actions">
                <el-button @click="$router.push('/my-exams')">返回我的考试</el-button>
                <el-button type="primary" @click="$router.push('/my-scores')">查看我的成绩</el-button>
            </div>

            <el-alert type="info" :closable="false" class="mt-16" show-icon
                      title="逐题作答详情与解析、薄弱知识点分析将在学情分析模块中提供"/>
        </el-card>
    </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { takingApi } from '@/api/answer'

const route = useRoute()
const router = useRouter()
const recordId = Number(route.params.recordId)

const loading = ref(false)
const result = ref(null)

async function load() {
    loading.value = true
    try {
        result.value = await takingApi.result(recordId)
    } catch {
        router.replace('/my-exams')
    } finally {
        loading.value = false
    }
}

onMounted(load)
</script>

<style scoped>
.score-box { text-align: center; margin-bottom: 8px; }
.score { font-size: 56px; font-weight: 700; color: #409eff; line-height: 1.1; }
.score-sub { color: #909399; font-size: 13px; margin-top: 6px; }
.mt-16 { margin-top: 16px; }
.actions { display: flex; justify-content: center; gap: 12px; margin-top: 20px; }
.hint { color: #909399; font-size: 12px; margin-left: 6px; }
</style>
