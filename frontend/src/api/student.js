import request from '@/utils/request'

/**
 * 学生管理接口。
 * 对应后端 /api/students（教师专属，学生访问会被 403 拦截）。
 */
export const studentApi = {
    /** 分页查询：{ page, size, classId, keyword, enabled } */
    page(params) {
        return request.get('/students', { params })
    },

    detail(id) {
        return request.get(`/students/${id}`)
    },

    create(data) {
        return request.post('/students', data)
    },

    update(id, data) {
        return request.put(`/students/${id}`, data)
    },

    remove(id) {
        return request.delete(`/students/${id}`)
    },

    /** 重置密码为系统初始密码 */
    resetPassword(id) {
        return request.put(`/students/${id}/password/reset`)
    },

    /**
     * 批量导入。请求体是纯文本，每行一条：用户名,姓名,年级,班级名称
     * 后端逐个处理并在结果里返回失败原因，不做全有或全无。
     */
    import(content) {
        return request.post('/students/import', content, {
            headers: { 'Content-Type': 'text/plain;charset=UTF-8' }
        })
    }
}
