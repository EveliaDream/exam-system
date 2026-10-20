import request from '@/utils/request'

/** 题目管理接口。对应后端 /api/questions（教师专属）。 */
export const questionApi = {
    /** 分页查询：{ page, size, courseId, type, difficulty, knowledgePointId, keyword, enabled } */
    page(params) {
        return request.get('/questions', { params })
    },

    detail(id) {
        return request.get(`/questions/${id}`)
    },

    create(data) {
        return request.post('/questions', data)
    },

    update(id, data) {
        return request.put(`/questions/${id}`, data)
    },

    remove(id) {
        return request.delete(`/questions/${id}`)
    },

    /** 启用 / 停用 */
    toggleEnabled(id, enabled) {
        return request.put(`/questions/${id}/enabled`, null, { params: { enabled } })
    }
}

/** 知识点接口。知识点是学情分析的主干，录题时必须从下拉框选择，不允许自由输入。 */
export const knowledgePointApi = {
    page(params) {
        return request.get('/knowledge-points', { params })
    },

    /** 下拉选项，供录题时选择 */
    options(courseId) {
        return request.get('/knowledge-points/options', {
            params: courseId == null ? {} : { courseId }
        })
    },

    create(data) {
        return request.post('/knowledge-points', data)
    },

    update(id, data) {
        return request.put(`/knowledge-points/${id}`, data)
    },

    remove(id) {
        return request.delete(`/knowledge-points/${id}`)
    }
}

/** 课程下拉选项 */
export const courseApi = {
    options() {
        return request.get('/courses/options')
    }
}
