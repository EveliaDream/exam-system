<template>
    <el-container class="layout">
        <el-aside width="210px" class="aside">
            <div class="logo">在线考试系统</div>
            <el-menu :default-active="$route.path" router class="menu">
                <el-menu-item index="/">
                    <el-icon><HomeFilled/></el-icon>
                    <span>工作台</span>
                </el-menu-item>

                <!-- 教师菜单 -->
                <template v-if="authStore.isTeacher">
                    <el-menu-item index="/students">
                        <el-icon><User/></el-icon>
                        <span>学生管理</span>
                    </el-menu-item>
                    <el-menu-item index="/classes">
                        <el-icon><School/></el-icon>
                        <span>班级管理</span>
                    </el-menu-item>
                    <el-menu-item index="/questions">
                        <el-icon><Document/></el-icon>
                        <span>题库管理</span>
                    </el-menu-item>
                    <el-menu-item index="/knowledge-points">
                        <el-icon><CollectionTag/></el-icon>
                        <span>知识点管理</span>
                    </el-menu-item>
                    <el-menu-item index="/exams">
                        <el-icon><Tickets/></el-icon>
                        <span>考试管理</span>
                    </el-menu-item>
                </template>

                <!-- 学生菜单 -->
                <template v-if="authStore.isStudent">
                    <el-menu-item index="/my-exams">
                        <el-icon><Tickets/></el-icon>
                        <span>我的考试</span>
                    </el-menu-item>
                    <el-menu-item index="/my-scores">
                        <el-icon><TrendCharts/></el-icon>
                        <span>我的成绩</span>
                    </el-menu-item>
                </template>
            </el-menu>
        </el-aside>

        <el-container>
            <el-header class="header">
                <div class="crumb">{{ $route.meta.title }}</div>
                <div class="user-area">
                    <el-tag :type="authStore.isTeacher ? 'primary' : 'success'" effect="dark" size="small">
                        {{ authStore.user?.roleLabel }}
                    </el-tag>
                    <span class="username">{{ authStore.displayName }}</span>
                    <el-dropdown @command="onCommand">
                        <el-icon class="more"><MoreFilled/></el-icon>
                        <template #dropdown>
                            <el-dropdown-menu>
                                <el-dropdown-item command="password">修改密码</el-dropdown-item>
                                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
                            </el-dropdown-menu>
                        </template>
                    </el-dropdown>
                </div>
            </el-header>

            <el-main class="main">
                <router-view/>
            </el-main>
        </el-container>

        <!-- 修改密码：教师与学生共用 -->
        <el-dialog v-model="pwdVisible" title="修改密码" width="460px" :close-on-click-modal="false"
                   @closed="resetPwdForm">
            <el-alert type="info" :closable="false" show-icon class="mb-12"
                      title="修改成功后当前登录状态会失效，需要用新密码重新登录"/>
            <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="90px">
                <el-form-item label="原密码" prop="oldPassword">
                    <el-input v-model="pwdForm.oldPassword" type="password" show-password
                              autocomplete="current-password" placeholder="请输入当前密码"/>
                </el-form-item>
                <el-form-item label="新密码" prop="newPassword">
                    <el-input v-model="pwdForm.newPassword" type="password" show-password
                              autocomplete="new-password" placeholder="6~64 位"/>
                </el-form-item>
                <el-form-item label="确认新密码" prop="confirmPassword">
                    <el-input v-model="pwdForm.confirmPassword" type="password" show-password
                              autocomplete="new-password" placeholder="再次输入新密码"
                              @keyup.enter="submitPassword"/>
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="pwdVisible = false">取消</el-button>
                <el-button type="primary" :loading="pwdSubmitting" @click="submitPassword">确定</el-button>
            </template>
        </el-dialog>
    </el-container>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { CollectionTag, Document, HomeFilled, MoreFilled, School, Tickets, TrendCharts, User } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { authApi } from '@/api/auth'

const router = useRouter()
const authStore = useAuthStore()

// ===== 修改密码 =====
const pwdVisible = ref(false)
const pwdSubmitting = ref(false)
const pwdFormRef = ref(null)
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const pwdRules = {
    oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
    newPassword: [
        { required: true, message: '请输入新密码', trigger: 'blur' },
        { min: 6, max: 64, message: '新密码长度需为 6~64 位', trigger: 'blur' }
    ],
    confirmPassword: [
        { required: true, message: '请再次输入新密码', trigger: 'blur' },
        {
            validator: (_, value, cb) => {
                if (value !== pwdForm.newPassword) {
                    cb(new Error('两次输入的新密码不一致'))
                } else {
                    cb()
                }
            },
            trigger: 'blur'
        }
    ]
}

function resetPwdForm() {
    pwdForm.oldPassword = ''
    pwdForm.newPassword = ''
    pwdForm.confirmPassword = ''
    pwdFormRef.value?.clearValidate()
}

async function submitPassword() {
    const valid = await pwdFormRef.value?.validate().catch(() => false)
    if (!valid) return
    pwdSubmitting.value = true
    try {
        await authApi.changePassword({
            oldPassword: pwdForm.oldPassword,
            newPassword: pwdForm.newPassword
        })
        pwdVisible.value = false
        // 后端改密后 tokenVersion +1，当前 token 已失效，必须重新登录
        ElMessage.success('密码已修改，请用新密码重新登录')
        authStore.token = ''
        authStore.user = null
        localStorage.removeItem('token')
        localStorage.removeItem('user')
        router.push('/login')
    } catch {
        // 原密码错误等由拦截器提示
    } finally {
        pwdSubmitting.value = false
    }
}

onMounted(async () => {
    // 每次进入后台都用服务端数据校正本地缓存，避免用户手改 localStorage 就变成教师
    try {
        await authStore.fetchCurrentUser()
    } catch {
        // 401 由拦截器统一处理
    }
})

async function onCommand(command) {
    if (command === 'password') {
        pwdVisible.value = true
        return
    }
    if (command === 'logout') {
        await authStore.logout()
        ElMessage.success('已退出登录')
        router.push('/login')
    }
}
</script>

<style scoped>
.layout {
    height: 100%;
}

.aside {
    background-color: #fff;
    border-right: 1px solid #e4e7ed;
    display: flex;
    flex-direction: column;
}

.logo {
    height: 60px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 16px;
    font-weight: 600;
    color: #409eff;
    border-bottom: 1px solid #e4e7ed;
}

.menu {
    border-right: none;
}

.header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    background-color: #fff;
    border-bottom: 1px solid #e4e7ed;
}

.crumb {
    font-size: 15px;
    font-weight: 600;
}

.user-area {
    display: flex;
    align-items: center;
    gap: 10px;
}

.username {
    font-size: 14px;
}

.more {
    cursor: pointer;
    color: #606266;
}

.main {
    background-color: #f5f7fa;
}
</style>
