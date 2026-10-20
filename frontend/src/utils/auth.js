/**
 * localStorage 中的 token 读写。
 * <p>
 * 单独抽出成一个模块，是为了避免 store 与 request 之间循环依赖：
 * request.js 需要读 token 和清理登录态，但不应反向依赖 Pinia store。
 */
const TOKEN_KEY = 'exam_token'
const USER_KEY = 'exam_user'

export function getToken() {
    return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token) {
    localStorage.setItem(TOKEN_KEY, token)
}

export function getStoredUser() {
    const raw = localStorage.getItem(USER_KEY)
    if (!raw) return null
    try {
        return JSON.parse(raw)
    } catch {
        return null
    }
}

export function setStoredUser(user) {
    localStorage.setItem(USER_KEY, JSON.stringify(user))
}

/**
 * 清空本地登录态。
 * 注意：无状态 JWT 下服务端无法立即吊销 token，这里只是丢弃本地凭证；
 * 真正让旧 token 失效的是后端的 tokenVersion 机制（改密码时 +1）。
 */
export function clearAuth() {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
}
