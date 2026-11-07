<template>
    <div v-loading="loading">
        <el-card v-if="result" shadow="never">
            <el-result
                :icon="result.passed ? 'success' : 'error'"
                :title="result.passed ? '考试通过' : '未达到及格线'"
                :sub-title="result.examTitle"
            >
                <template #extra>
                    <div class="score-box">
                        <div class="score">{{ result.objectiveScore }}</div>
                        <div class="score-sub">满分 {{ result.totalScore }} · 及格线 {{ result.passScore }}</div>
                    </div>
                </template>
            </el-result>

            <el-tabs v-model="tab" class="mt-8">
                <!-- 概览 -->
                <el-tab-pane label="成绩概览" name="summary">
                    <el-descriptions :column="2" border>
                        <el-descriptions-item label="答对题数">
                            {{ result.correctCount }} / {{ result.questionCount }}
                        </el-descriptions-item>
                        <el-descriptions-item label="交卷方式">
                            <el-tag size="small" :type="result.autoSubmitted ? 'warning' : 'success'">
                                {{ result.autoSubmitted ? '超时自动交卷' : '主动交卷' }}
                            </el-tag>
                            <el-tag v-if="result.attemptNo > 1" size="small" type="info" class="ml-4">
                                第 {{ result.attemptNo }} 次作答
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
                </el-tab-pane>

                <!-- 学情分析 -->
                <el-tab-pane name="analysis">
                    <template #label>
                        薄弱知识点
                        <el-badge v-if="analysis?.weakPoints?.length" :value="analysis.weakPoints.length"
                                  type="danger" class="ml-4"/>
                    </template>

                    <template v-if="analysis && analysis.hasData">
                        <el-alert v-if="analysis.weakPoints.length" type="warning" :closable="false" show-icon
                                  class="mb-12"
                                  :title="`本次考试有 ${analysis.weakPoints.length} 个知识点掌握度低于 60%，建议优先复习`"
                                  description="掌握度按分值加权计算：该知识点下答对题的分值 ÷ 全部题的分值。"/>
                        <el-alert v-else type="success" :closable="false" show-icon class="mb-12"
                                  title="本次考试涉及的知识点都已达到 60% 以上，暂无薄弱项"/>

                        <h4 v-if="analysis.weakPoints.length">需要加强的知识点</h4>
                        <div v-for="k in analysis.weakPoints" :key="k.knowledgePointId" class="kp-card weak">
                            <div class="kp-head">
                                <span class="kp-name">{{ k.name }}</span>
                                <el-tag size="small" type="danger">掌握度 {{ Math.round(k.mastery * 100) }}%</el-tag>
                            </div>
                            <el-progress :percentage="Math.round(k.mastery * 100)" status="exception"
                                         :stroke-width="10"/>
                            <div class="kp-meta">
                                该知识点共 {{ k.questionCount }} 题，答对 {{ k.correctCount }} 题，答错 {{ k.wrongCount }} 题
                            </div>
                        </div>

                        <h4 class="mt-16">全部知识点掌握情况</h4>
                        <div v-for="k in analysis.allPoints" :key="k.knowledgePointId" class="kp-card">
                            <div class="kp-head">
                                <span class="kp-name">{{ k.name }}</span>
                                <span class="kp-meta">{{ k.correctCount }}/{{ k.questionCount }} 题正确</span>
                                <el-tag size="small" :type="k.mastery >= 0.8 ? 'success' : k.mastery >= 0.6 ? 'warning' : 'danger'">
                                    {{ Math.round(k.mastery * 100) }}%
                                </el-tag>
                            </div>
                            <el-progress :percentage="Math.round(k.mastery * 100)"
                                         :color="masteryColor(k.mastery)" :stroke-width="8"/>
                        </div>
                    </template>
                    <el-empty v-else description="题目尚未标注知识点，无法生成学情分析"/>
                </el-tab-pane>

                <!-- 逐题详情 -->
                <el-tab-pane label="逐题详情" name="detail">
                    <div v-for="q in detail?.questions || []" :key="q.questionId" class="q-card">
                        <div class="q-head">
                            <span class="q-index">{{ q.order }}</span>
                            <el-tag size="small" type="info">{{ q.typeLabel }}</el-tag>
                            <el-tag size="small" :type="q.correct ? 'success' : 'danger'">
                                {{ q.correct ? '正确' : '错误' }}
                            </el-tag>
                            <span class="q-score">{{ q.score }} / {{ q.maxScore }} 分</span>
                            <span v-if="q.timeSpentSec != null" class="sub">耗时 {{ q.timeSpentSec }} 秒</span>
                            <span v-if="q.submitCount" class="sub">修改 {{ q.submitCount }} 次</span>
                        </div>
                        <div class="q-stem">{{ q.stem }}</div>
                        <el-image v-if="q.imageUrl" :src="q.imageUrl" fit="contain" class="q-image"
                                  :preview-src-list="[q.imageUrl]" preview-teleported/>
                        <el-descriptions :column="1" size="small" border>
                            <el-descriptions-item label="你的答案">
                                <span :class="q.correct ? 'ok' : 'bad'">{{ formatAnswer(q.studentAnswer) }}</span>
                            </el-descriptions-item>
                            <el-descriptions-item label="正确答案">
                                <span class="ok">{{ formatAnswer(q.correctAnswer) }}</span>
                            </el-descriptions-item>
                            <el-descriptions-item v-if="q.analysis" label="解析">{{ q.analysis }}</el-descriptions-item>
                        </el-descriptions>
                    </div>
                    <el-empty v-if="!detail?.questions?.length" description="暂无详情"/>
                </el-tab-pane>
            </el-tabs>

            <div class="actions">
                <el-button @click="$router.push('/my-exams')">返回我的考试</el-button>
                <el-button type="primary" @click="$router.push('/my-scores')">查看我的成绩</el-button>
            </div>
        </el-card>
    </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { takingApi } from '@/api/answer'
import { analyticsApi } from '@/api/analytics'

const route = useRoute()
const router = useRouter()
const recordId = Number(route.params.recordId)

const loading = ref(false)
const result = ref(null)
const analysis = ref(null)
const detail = ref(null)
const tab = ref('summary')

function masteryColor(v) {
    if (v < 0.6) return '#f56c6c'
    if (v < 0.75) return '#e6a23c'
    return '#67c23a'
}

/** 答案展示：选择题显示 A/B/C，判断题转成中文 */
function formatAnswer(list) {
    if (!list || !list.length) return '（未作答）'
    return list.map(a => (a === 'T' ? '正确' : a === 'F' ? '错误' : a)).join('、')
}

async function load() {
    loading.value = true
    try {
        result.value = await takingApi.result(recordId)
        // 学情分析与逐题详情互不依赖，并行取，失败不影响成绩展示
        const [a, d] = await Promise.allSettled([
            analyticsApi.myAnalysis(recordId),
            analyticsApi.myDetail(recordId)
        ])
        analysis.value = a.status === 'fulfilled' ? a.value : null
        detail.value = d.status === 'fulfilled' ? d.value : null
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
.mt-8 { margin-top: 8px; }
.mt-16 { margin-top: 16px; }
.ml-4 { margin-left: 4px; }
.actions { display: flex; justify-content: center; gap: 12px; margin-top: 20px; }
.hint { color: #909399; font-size: 12px; margin-left: 6px; }
.sub { color: #909399; font-size: 12px; }
.ok { color: #67c23a; }
.bad { color: #f56c6c; }
.mb-12 { margin-bottom: 12px; }
.kp-card { border: 1px solid #ebeef5; border-radius: 6px; padding: 10px 12px; margin-bottom: 10px; }
.kp-card.weak { border-color: #fbc4c4; background: #fef0f0; }
.kp-head { display: flex; align-items: center; gap: 10px; margin-bottom: 6px; }
.kp-name { font-weight: 600; }
.kp-meta { margin-left: auto; font-size: 12px; color: #909399; }
.q-card { border: 1px solid #ebeef5; border-radius: 6px; padding: 12px; margin-bottom: 12px; }
.q-head { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; flex-wrap: wrap; }
.q-index { font-weight: 700; color: #909399; }
.q-score { color: #909399; font-size: 12px; }
.q-stem { line-height: 1.8; margin-bottom: 10px; white-space: pre-wrap; }
.q-image { max-width: 100%; max-height: 280px; margin-bottom: 10px; border-radius: 4px; }
</style>
