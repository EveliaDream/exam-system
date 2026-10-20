<template>
    <div>
        <el-alert
            title="阶段 2 已完成：用户与班级管理（学生 CRUD、分班、批量导入、教师数据隔离）"
            type="success"
            :closable="false"
            show-icon
            class="mb-12"
        />

        <el-row :gutter="12">
            <!-- 当前登录用户 -->
            <el-col :span="8">
                <el-card shadow="never" class="mb-12">
                    <template #header>当前登录用户</template>
                    <el-descriptions :column="1" border size="small">
                        <el-descriptions-item label="用户名">{{ authStore.user?.username }}</el-descriptions-item>
                        <el-descriptions-item label="姓名">{{ authStore.user?.realName }}</el-descriptions-item>
                        <el-descriptions-item label="角色">
                            {{ authStore.user?.role }} / {{ authStore.user?.roleLabel }}
                        </el-descriptions-item>
                        <el-descriptions-item label="所属班级">
                            {{ authStore.user?.className || '—（教师无班级）' }}
                        </el-descriptions-item>
                    </el-descriptions>
                    <p class="text-muted small mt-8">
                        数据来自 <code>/api/auth/me</code>，不信任 localStorage 缓存。
                    </p>
                </el-card>

                <el-card shadow="never">
                    <template #header>后端连通性</template>
                    <div v-if="health">
                        <el-tag type="success" size="small">UP</el-tag>
                        <span class="ml-8">{{ health.application }}</span>
                        <p class="text-muted small mt-8">服务端时间：{{ health.time }}</p>
                        <p class="text-muted small">
                            经 Vite 代理访问 <code>/api/health</code>：前端 5173 → 后端 8080，无需 CORS 配置。
                        </p>
                    </div>
                    <el-skeleton v-else :rows="2" animated/>
                </el-card>
            </el-col>

            <!-- 阶段进度 -->
            <el-col :span="8">
                <el-card shadow="never" class="mb-12">
                    <template #header>开发进度</template>
                    <el-timeline>
                        <el-timeline-item
                            v-for="item in phases"
                            :key="item.title"
                            :type="item.done ? 'success' : 'info'"
                            :hollow="!item.done"
                            :timestamp="item.phase"
                            placement="top"
                        >
                            <strong>{{ item.title }}</strong>
                            <div class="text-muted small">{{ item.detail }}</div>
                        </el-timeline-item>
                    </el-timeline>
                </el-card>
            </el-col>

            <!-- 权限验证 -->
            <el-col :span="8">
                <el-card shadow="never">
                    <template #header>权限与数据隔离验证</template>
                    <p class="text-muted small">
                        后端用 <code>@PreAuthorize("hasRole('TEACHER')")</code> 保护整个学生管理模块。
                        当前角色：
                        <el-tag size="small" :type="authStore.isTeacher ? 'primary' : 'success'">
                            {{ authStore.user?.roleLabel }}
                        </el-tag>
                    </p>
                    <el-button :loading="checking" type="primary" plain size="small" @click="checkPermission">
                        尝试调用学生管理接口
                    </el-button>
                    <el-alert
                        v-if="permissionResult"
                        class="mt-12"
                        :type="permissionResult.type"
                        :title="permissionResult.title"
                        :description="permissionResult.description"
                        :closable="false"
                        show-icon
                    />
                    <p class="text-muted small mt-12">
                        另一个验证点：教师只能看到<strong>自己名下班级</strong>的学生（未分班学生除外）。
                        可用 teacher 与 teacher2 两个账号分别登录对比列表内容。
                    </p>
                </el-card>
            </el-col>
        </el-row>
    </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { systemApi } from '@/api/auth'
import request from '@/utils/request'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const health = ref(null)
const checking = ref(false)
const permissionResult = ref(null)

const phases = [
    { phase: '阶段 0', title: '工程骨架', detail: '统一响应、异常处理、11 张表实体、JWT 认证、前端工程', done: true },
    { phase: '阶段 1', title: '认证与权限', detail: '登录/登出/改密码、角色鉴权、401 与 403 统一响应', done: true },
    { phase: '阶段 2', title: '用户与班级管理', detail: '学生 CRUD、分班、批量导入、重置密码、数据隔离', done: true },
    { phase: '阶段 3', title: '题库管理', detail: '四种题型增删改查、筛选、录入校验', done: false },
    { phase: '阶段 4', title: '组卷与发布', detail: '手动/随机抽题、分值校验、绑定班级', done: false },
    { phase: '阶段 5', title: '在线作答与判分', detail: '自动保存、断线恢复、服务端权威倒计时、客观题判分', done: false },
    { phase: '阶段 6', title: '成绩与统计', detail: '成绩单、Excel 导出、分数段统计', done: false }
]

onMounted(async () => {
    try {
        health.value = await systemApi.health()
    } catch {
        // 拦截器已提示
    }
})

async function checkPermission() {
    checking.value = true
    permissionResult.value = null
    try {
        const data = await request.get('/students', { params: { page: 1, size: 5 } })
        permissionResult.value = {
            type: 'success',
            title: `HTTP 200 · 可见 ${data.total} 名学生`,
            description: '教师身份访问学生管理接口正常，且返回范围已限定在当前教师的数据权限内。'
        }
    } catch (error) {
        const status = error.response?.status
        permissionResult.value = {
            type: status === 403 ? 'warning' : 'error',
            title: status === 403 ? 'HTTP 403 · 无权限访问该资源' : `请求失败（HTTP ${status ?? '未知'}）`,
            description: status === 403
                ? '学生身份访问教师专属接口被后端正确拦截，权限边界生效。'
                : '请检查后端服务是否正常运行。'
        }
    } finally {
        checking.value = false
    }
}
</script>

<style scoped>
.mb-12 {
    margin-bottom: 12px;
}

.mt-8 {
    margin-top: 8px;
}

.mt-12 {
    margin-top: 12px;
}

.ml-8 {
    margin-left: 8px;
}

.small {
    font-size: 12px;
    line-height: 1.7;
}
</style>
