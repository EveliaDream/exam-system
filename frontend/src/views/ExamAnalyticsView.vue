<template>
    <div v-loading="loading">
        <el-card shadow="never" class="mb-12">
            <div class="head">
                <div class="head-left">
                    <el-button link :icon="ArrowLeft" @click="$router.push('/exams')">返回考试列表</el-button>
                    <span class="title">{{ data.title || '学情分析' }}</span>
                    <el-tag size="small" type="info">总分 {{ data.totalScore }} · 及格线 {{ data.passScore }}</el-tag>
                </div>
                <el-button :icon="Refresh" @click="load">刷新</el-button>
            </div>
        </el-card>

        <el-alert v-if="data.submittedCount !== undefined && !data.sufficient" type="warning" :closable="false"
                  show-icon class="mb-12"
                  :title="`已交卷 ${data.submittedCount} 人，少于 ${MIN_SAMPLE} 人`"
                  description="样本量偏小，统计量波动较大：下方的数字可看，但「偏难/偏易」等判断仅供参考，不建议据此调整题库。"/>

        <!-- 概览 -->
        <el-row :gutter="12" class="mb-12">
            <el-col :span="4">
                <el-card shadow="never" class="stat">
                    <div class="stat-label">参与人数</div>
                    <div class="stat-value">{{ data.participantCount ?? 0 }}</div>
                    <div class="stat-sub">已交卷 {{ data.submittedCount ?? 0 }}</div>
                </el-card>
            </el-col>
            <el-col :span="4">
                <el-card shadow="never" class="stat">
                    <div class="stat-label">平均分</div>
                    <div class="stat-value">{{ data.avgScore ?? 0 }}</div>
                    <div class="stat-sub">得分率 {{ pct(data.avgRate) }}</div>
                </el-card>
            </el-col>
            <el-col :span="4">
                <el-card shadow="never" class="stat">
                    <div class="stat-label">及格率</div>
                    <div class="stat-value" :class="rateClass(data.passRate)">{{ pct(data.passRate) }}</div>
                    <div class="stat-sub">及格线 {{ data.passScore }}</div>
                </el-card>
            </el-col>
            <el-col :span="4">
                <el-card shadow="never" class="stat">
                    <div class="stat-label">最高分</div>
                    <div class="stat-value ok">{{ data.maxScore ?? 0 }}</div>
                </el-card>
            </el-col>
            <el-col :span="4">
                <el-card shadow="never" class="stat">
                    <div class="stat-label">最低分</div>
                    <div class="stat-value bad">{{ data.minScore ?? 0 }}</div>
                </el-card>
            </el-col>
            <el-col :span="4">
                <el-card shadow="never" class="stat">
                    <div class="stat-label">题目数</div>
                    <div class="stat-value">{{ data.questionStats?.length ?? 0 }}</div>
                    <div class="stat-sub">知识点 {{ data.knowledgeStats?.length ?? 0 }}</div>
                </el-card>
            </el-col>
        </el-row>

        <el-row :gutter="12">
            <!-- 分数段分布 -->
            <el-col :span="10">
                <el-card shadow="never" class="mb-12">
                    <template #header>分数段分布（按得分率）</template>
                    <div v-for="b in data.scoreDistribution || []" :key="b.label" class="dist-row">
                        <span class="dist-label">{{ b.label }}</span>
                        <el-progress :percentage="Math.round(b.percent * 100)" :stroke-width="14"
                                     :show-text="false" class="dist-bar"/>
                        <span class="dist-count">{{ b.count }} 人</span>
                    </div>
                    <el-empty v-if="!data.scoreDistribution?.length" description="暂无数据" :image-size="60"/>
                </el-card>

                <!-- 知识点掌握度 -->
                <el-card shadow="never">
                    <template #header>
                        <div class="card-header">
                            <span>知识点掌握度（全班）</span>
                            <span class="sub">按掌握度升序，低于 60% 标红</span>
                        </div>
                    </template>
                    <div v-for="k in data.knowledgeStats || []" :key="k.knowledgePointId" class="kp-row">
                        <div class="kp-head">
                            <el-tag v-if="k.weak" size="small" type="danger">薄弱</el-tag>
                            <span class="kp-name">{{ k.name }}</span>
                            <span class="kp-meta">{{ k.correctCount }}/{{ k.answerCount }} 题正确 · {{ k.earnedScore }}/{{ k.possibleScore }} 分</span>
                        </div>
                        <el-progress :percentage="Math.round((k.mastery || 0) * 100)"
                                     :color="masteryColor(k.mastery)" :stroke-width="10"/>
                    </div>
                    <el-empty v-if="!data.knowledgeStats?.length" description="题目尚未标注知识点" :image-size="60"/>
                </el-card>
            </el-col>

            <!-- 题目难度贴合度 -->
            <el-col :span="14">
                <el-card shadow="never" class="mb-12">
                    <template #header>
                        <div class="card-header">
                            <span>题目难度 × 班级水平贴合度</span>
                            <span class="sub">得分率与班级平均得分率比较</span>
                        </div>
                    </template>
                    <el-table :data="data.questionStats || []" border stripe size="small" max-height="420">
                        <el-table-column prop="order" label="#" width="45" align="center"/>
                        <el-table-column prop="stem" label="题干" min-width="220" show-overflow-tooltip/>
                        <el-table-column label="得分率" width="90" align="center">
                            <template #default="{ row }">{{ pct(row.correctRate) }}</template>
                        </el-table-column>
                        <el-table-column label="平均耗时" width="90" align="center">
                            <template #default="{ row }">{{ row.avgTimeSpentSec }} 秒</template>
                        </el-table-column>
                        <el-table-column label="判断" min-width="200">
                            <template #default="{ row }">
                                <el-tag size="small" :type="judgeType(row.judgementCode)">{{ row.judgement }}</el-tag>
                            </template>
                        </el-table-column>
                        <template #empty>
                            <el-empty description="暂无作答数据" :image-size="60"/>
                        </template>
                    </el-table>
                </el-card>

                <!-- 学生成绩 -->
                <el-card shadow="never">
                    <template #header>
                        <div class="card-header">
                            <span>学生成绩（{{ data.students?.length ?? 0 }} 人）</span>
                            <span class="sub">按分数降序</span>
                        </div>
                    </template>
                    <el-table :data="data.students || []" border stripe size="small" max-height="320">
                        <el-table-column type="index" label="#" width="45" align="center"/>
                        <el-table-column prop="studentName" label="姓名" width="100"/>
                        <el-table-column prop="username" label="账号" width="110"/>
                        <el-table-column prop="className" label="班级" min-width="140"/>
                        <el-table-column label="得分" width="100" align="center">
                            <template #default="{ row }">
                                <span :class="row.passed ? 'ok' : 'bad'">{{ row.score }}</span>
                                <span class="sub"> / {{ data.totalScore }}</span>
                            </template>
                        </el-table-column>
                        <el-table-column label="结果" width="80" align="center">
                            <template #default="{ row }">
                                <el-tag size="small" :type="row.passed ? 'success' : 'danger'">
                                    {{ row.passed ? '通过' : '未通过' }}
                                </el-tag>
                            </template>
                        </el-table-column>
                        <el-table-column label="交卷" width="110" align="center">
                            <template #default="{ row }">
                                <el-tag size="small" :type="row.autoSubmitted ? 'warning' : 'info'">
                                    {{ row.autoSubmitted ? '自动' : '主动' }}
                                </el-tag>
                            </template>
                        </el-table-column>
                        <el-table-column label="切屏" width="70" align="center">
                            <template #default="{ row }">{{ row.switchScreenCount }}</template>
                        </el-table-column>
                        <el-table-column prop="submitTime" label="交卷时间" width="170"/>
                        <template #empty>
                            <el-empty description="还没有学生交卷" :image-size="60"/>
                        </template>
                    </el-table>
                </el-card>
            </el-col>
        </el-row>

        <el-alert type="info" :closable="false" class="mt-12" show-icon
                  title="统计口径说明">
            掌握度 = Σ(该知识点下答对题的分值) / Σ(该知识点下全部题的分值)，按分值加权而非题数平均；
            「贴合度」为经验性判据（题目得分率与班级平均得分率相差超过 25% 视为偏难/偏易），
            不是心理测量学意义上的区分度，样本不足时仅供参考。
        </el-alert>
    </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowLeft, Refresh } from '@element-plus/icons-vue'
import { analyticsApi } from '@/api/analytics'

const route = useRoute()
const examId = Number(route.params.examId)
const MIN_SAMPLE = 30

const loading = ref(false)
const data = ref({})

function pct(v) {
    return `${Math.round((v || 0) * 100)}%`
}

function rateClass(v) {
    const x = v || 0
    return x >= 0.8 ? 'ok' : x >= 0.6 ? '' : 'bad'
}

function masteryColor(v) {
    const x = v || 0
    if (x < 0.6) return '#f56c6c'
    if (x < 0.75) return '#e6a23c'
    return '#67c23a'
}

function judgeType(code) {
    return {
        GOOD: 'success',
        TOO_HARD: 'warning',
        HARD_UNCLEAR: 'danger',
        TOO_EASY: 'info',
        INSUFFICIENT: 'info'
    }[code] || 'info'
}

async function load() {
    loading.value = true
    try {
        data.value = await analyticsApi.exam(examId)
    } catch {
        data.value = {}
    } finally {
        loading.value = false
    }
}

onMounted(load)
</script>

<style scoped>
.mb-12 { margin-bottom: 12px; }
.mt-12 { margin-top: 12px; }
.head { display: flex; align-items: center; justify-content: space-between; }
.head-left { display: flex; align-items: center; gap: 10px; }
.title { font-size: 16px; font-weight: 600; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
.sub { font-size: 12px; color: #909399; }
.stat { text-align: center; }
.stat-label { color: #909399; font-size: 12px; }
.stat-value { font-size: 26px; font-weight: 700; line-height: 1.4; }
.stat-sub { color: #909399; font-size: 12px; }
.ok { color: #67c23a; }
.bad { color: #f56c6c; }
.dist-row { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.dist-label { width: 90px; font-size: 12px; color: #606266; }
.dist-bar { flex: 1; }
.dist-count { width: 60px; text-align: right; font-size: 12px; color: #909399; }
.kp-row { margin-bottom: 14px; }
.kp-head { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
.kp-name { font-size: 13px; }
.kp-meta { margin-left: auto; font-size: 12px; color: #909399; }
</style>
