import request from '@/utils/request'

/** 班级管理接口。对应后端 /api/classes（教师专属）。 */
export const classApi = {
    page(params) {
        return request.get('/classes', { params })
    },

    /** 当前教师名下全部班级，供下拉框使用 */
    options() {
        return request.get('/classes/options')
    },

    detail(id) {
        return request.get(`/classes/${id}`)
    },

    create(data) {
        return request.post('/classes', data)
    },

    update(id, data) {
        return request.put(`/classes/${id}`, data)
    },

    remove(id) {
        return request.delete(`/classes/${id}`)
    }
}
