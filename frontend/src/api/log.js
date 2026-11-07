import request from '@/utils/request'

/** 操作日志接口（教师端，只读审计）。 */
export const logApi = {
    /** 分页查询：{ page, size, action, keyword, from, to } */
    page(params) {
        return request.get('/logs', { params })
    },

    /** 已出现过的动作类型，供下拉筛选 */
    actions() {
        return request.get('/logs/actions')
    }
}
