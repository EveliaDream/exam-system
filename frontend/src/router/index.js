import { createRouter, createWebHistory } from 'vue-router'
import { getToken, getStoredUser } from '@/utils/auth'

/**
 * 路由表。
 * <p>
 * 结构：登录页与 404 独立；其余业务页面统一挂在 AdminLayout 之下，
 * 由布局组件提供侧边菜单与顶栏，页面本身只关心业务内容。
 * meta.roles 为空表示任何已登录用户都可访问。
 */
const routes = [
    {
        path: '/login',
        name: 'login',
        component: () => import('@/views/LoginView.vue'),
        meta: { public: true, title: '登录' }
    },
    {
        path: '/',
        component: () => import('@/layouts/AdminLayout.vue'),
        children: [
            {
                path: '',
                name: 'home',
                component: () => import('@/views/HomeView.vue'),
                meta: { title: '工作台' }
            },
            {
                path: 'students',
                name: 'students',
                component: () => import('@/views/StudentsView.vue'),
                meta: { title: '学生管理', roles: ['TEACHER'] }
            },
            {
                path: 'classes',
                name: 'classes',
                component: () => import('@/views/ClassesView.vue'),
                meta: { title: '班级管理', roles: ['TEACHER'] }
            },
            {
                path: 'questions',
                name: 'questions',
                component: () => import('@/views/QuestionBankView.vue'),
                meta: { title: '题库管理', roles: ['TEACHER'] }
            },
            {
                path: 'knowledge-points',
                name: 'knowledge-points',
                component: () => import('@/views/KnowledgePointsView.vue'),
                meta: { title: '知识点管理', roles: ['TEACHER'] }
            }
            // 阶段 4 起补充：组卷、考试发布
            // 阶段 5 起补充（学生）：我的考试、在线作答、我的成绩
        ]
    },
    {
        path: '/:pathMatch(.*)*',
        name: 'not-found',
        component: () => import('@/views/NotFoundView.vue'),
        meta: { public: true, title: '页面不存在' }
    }
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

/**
 * 全局前置守卫。
 * <p>
 * 注意：前端守卫只负责体验（避免未登录用户看到空白页、教师误点学生菜单），
 * 真正的权限边界必须在后端接口上——学生即使手动改 URL 进来，接口依然会返回 403。
 */
router.beforeEach((to) => {
    document.title = to.meta.title ? `${to.meta.title} - 学生在线考试系统` : '学生在线考试系统'

    const loggedIn = !!getToken()
    if (to.meta.public) {
        if (to.path === '/login' && loggedIn) {
            return { path: '/' }
        }
        return true
    }

    if (!loggedIn) {
        return { path: '/login', query: { redirect: to.fullPath } }
    }

    const role = getStoredUser()?.role
    if (to.meta.roles && !to.meta.roles.includes(role)) {
        // 角色不匹配：退回工作台而不是停留在空白页
        return { path: '/' }
    }
    return true
})

export default router
