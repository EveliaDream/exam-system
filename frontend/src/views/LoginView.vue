<template>
    <div class="login-page">
        <el-card class="login-card">
            <template #header>
                <div class="login-header">
                    <h2>学生在线考试系统</h2>
                    <p class="text-muted">阶段 0 · 登录联调</p>
                </div>
            </template>

            <el-form
                ref="formRef"
                :model="form"
                :rules="rules"
                label-position="top"
                @keyup.enter="onSubmit"
            >
                <el-form-item label="用户名" prop="username">
                    <el-input v-model="form.username" placeholder="请输入用户名" clearable/>
                </el-form-item>

                <el-form-item label="密码" prop="password">
                    <el-input
                        v-model="form.password"
                        type="password"
                        placeholder="请输入密码"
                        show-password
                    />
                </el-form-item>

                <el-button
                    type="primary"
                    class="submit-btn"
                    :loading="loading"
                    @click="onSubmit"
                >
                    登录
                </el-button>
            </el-form>

            <el-divider>测试账号（密码均为 123456）</el-divider>
            <div class="accounts">
                <el-tag
                    v-for="account in accounts"
                    :key="account.username"
                    class="account-tag"
                    :type="account.type"
                    effect="plain"
                    @click="fillAccount(account)"
                >
                    {{ account.label }}
                </el-tag>
            </div>
            <p class="text-muted tip">点击标签自动填充，然后点登录。</p>
        </el-card>
    </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const formRef = ref(null)
const loading = ref(false)

const form = reactive({
    username: '',
    password: ''
})

const rules = {
    username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
    password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const accounts = [
    { label: 'teacher / 教师', username: 'teacher', password: '123456', type: 'primary' },
    { label: 'student1 / 学生', username: 'student1', password: '123456', type: 'success' },
    { label: 'student2 / 学生', username: 'student2', password: '123456', type: 'success' }
]

function fillAccount(account) {
    form.username = account.username
    form.password = account.password
}

async function onSubmit() {
    const valid = await formRef.value?.validate().catch(() => false)
    if (!valid) return

    loading.value = true
    try {
        const user = await authStore.login({ username: form.username, password: form.password })
        ElMessage.success(`欢迎回来，${user.realName}`)
        // 登录前被拦截的页面存在 redirect 参数，登录后回到那里
        router.push(route.query.redirect || '/')
    } catch {
        // 错误提示已由 Axios 响应拦截器统一处理
    } finally {
        loading.value = false
    }
}
</script>

<style scoped>
.login-page {
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
    background: linear-gradient(135deg, #e8f0fe 0%, #f5f7fa 100%);
}

.login-card {
    width: 420px;
}

.login-header h2 {
    margin: 0 0 6px;
    font-size: 20px;
}

.login-header p {
    margin: 0;
    font-size: 13px;
}

.submit-btn {
    width: 100%;
}

.accounts {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
}

.account-tag {
    cursor: pointer;
}

.tip {
    margin-top: 10px;
    font-size: 12px;
}
</style>
