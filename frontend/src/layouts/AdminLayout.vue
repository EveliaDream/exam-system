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
                                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
                            </el-dropdown-menu>
                        </template>
                    </el-dropdown>
                </div>
            </el-header>

            <el-main class="main">
                <router-view/>
            </el-main>
        </el-container>
    </el-container>
</template>

<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { CollectionTag, Document, HomeFilled, MoreFilled, School, Tickets, User } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

onMounted(async () => {
    // 每次进入后台都用服务端数据校正本地缓存，避免用户手改 localStorage 就变成教师
    try {
        await authStore.fetchCurrentUser()
    } catch {
        // 401 由拦截器统一处理
    }
})

async function onCommand(command) {
    if (command !== 'logout') return
    await authStore.logout()
    ElMessage.success('已退出登录')
    router.push('/login')
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
