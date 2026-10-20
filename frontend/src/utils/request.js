import axios from 'axios'
import { ElMessage } from 'element-plus'
import { clearAuth, getToken } from '@/utils/auth'
import router from '@/router'

/**
 * Axios 封装。
 * <p>
 * 只做三件事，写一次全项目受益：
 * 1. 请求拦截：自动带上 Authorization: Bearer <token>
 * 2. 响应拦截：后端统一返回 { code, message, data }，成功时直接把 data 交给业务层
 * 3. 错误拦截：401 清理登录态并跳登录页；403 提示无权限
 */
const request = axios.create({
    baseURL: '/api',
    timeout: 15000
})

// ===== 请求拦截器 =====
request.interceptors.request.use(
    config => {
        const token = getToken()
        if (token) {
            config.headers.Authorization = `Bearer ${token}`
        }
        return config
    },
    error => Promise.reject(error)
)

// ===== 响应拦截器 =====
request.interceptors.response.use(
    response => {
        const res = response.data
        // 后端约定 code === 0 为成功
        if (res && typeof res.code !== 'undefined') {
            if (res.code === 0) {
                return res.data
            }
            ElMessage.error(res.message || '请求失败')
            return Promise.reject(new Error(res.message || '请求失败'))
        }
        return res
    },
    error => {
        const status = error.response?.status
        const message = error.response?.data?.message

        if (status === 401) {
            // 未登录或 token 失效：清理本地凭证并回登录页
            clearAuth()
            ElMessage.error(message || '登录已过期，请重新登录')
            if (router.currentRoute.value.path !== '/login') {
                router.push('/login')
            }
        } else if (status === 403) {
            ElMessage.error(message || '无权限访问该资源')
        } else if (status === 404) {
            ElMessage.error(message || '请求的接口不存在')
        } else {
            ElMessage.error(message || '网络异常，请稍后重试')
        }
        return Promise.reject(error)
    }
)

export default request
