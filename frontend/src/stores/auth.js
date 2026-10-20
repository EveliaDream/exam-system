import { defineStore } from 'pinia'
import { authApi } from '@/api/auth'
import { clearAuth, getStoredUser, getToken, setStoredUser, setToken } from '@/utils/auth'

/**
 * 登录态。
 * <p>
 * 约定：token 与用户信息同时写入 localStorage，刷新页面后先用本地信息渲染，
 * 再调用 /auth/me 向服务端确认（避免用户手改 localStorage 就变成教师）。
 */
export const useAuthStore = defineStore('auth', {
    state: () => ({
        token: getToken() || '',
        user: getStoredUser()
    }),

    getters: {
        isLoggedIn: state => !!state.token,
        role: state => state.user?.role || '',
        isTeacher: state => state.user?.role === 'TEACHER',
        isStudent: state => state.user?.role === 'STUDENT',
        displayName: state => state.user?.realName || state.user?.username || ''
    },

    actions: {
        async login(form) {
            const data = await authApi.login(form)
            this.token = data.token
            this.user = data
            setToken(data.token)
            setStoredUser(data)
            return data
        },

        /** 用服务端返回的用户信息覆盖本地缓存 */
        async fetchCurrentUser() {
            const user = await authApi.me()
            this.user = { ...this.user, ...user }
            setStoredUser(this.user)
            return user
        },

        async logout() {
            try {
                await authApi.logout()
            } catch {
                // 登出接口失败不影响本地清理
            }
            this.token = ''
            this.user = null
            clearAuth()
        }
    }
})
