import request from '@/utils/request'

/** 认证相关接口，与后端 /api/auth 一一对应 */
export const authApi = {
    /** 登录：成功返回 { token, expireMillis, userId, username, realName, role, roleLabel, classId, className } */
    login(data) {
        return request.post('/auth/login', data)
    },

    /** 当前登录用户：刷新页面后用它恢复登录态 */
    me() {
        return request.get('/auth/me')
    },

    logout() {
        return request.post('/auth/logout')
    },

    changePassword(data) {
        return request.put('/auth/password', data)
    }
}

/** 健康检查：联调时用来确认 Vite 代理是否通 */
export const systemApi = {
    health() {
        return request.get('/health')
    }
}
