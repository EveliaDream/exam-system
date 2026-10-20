import request from '@/utils/request'

/** 考试与组卷接口（教师端）。对应后端 /api/exams。 */
export const examApi = {
    page(params) {
        return request.get('/exams', { params })
    },

    detail(id) {
        return request.get(`/exams/${id}`)
    },

    create(data) {
        return request.post('/exams', data)
    },

    update(id, data) {
        return request.put(`/exams/${id}`, data)
    },

    /** 只调整时间窗（已发布考试也可改，题目与总分冻结） */
    updateSchedule(id, data) {
        return request.put(`/exams/${id}/schedule`, data)
    },

    remove(id) {
        return request.delete(`/exams/${id}`)
    },

    /** 手动组卷：整体替换试卷题目 */
    setQuestions(id, items) {
        return request.put(`/exams/${id}/questions`, { items })
    },

    /** 随机组卷：按题型/难度/数量抽题，替换当前试卷 */
    random(id, data) {
        return request.post(`/exams/${id}/questions/random`, data)
    },

    bindClasses(id, classIds) {
        return request.put(`/exams/${id}/classes`, { classIds })
    },

    publish(id) {
        return request.put(`/exams/${id}/publish`)
    },

    finish(id) {
        return request.put(`/exams/${id}/finish`)
    }
}

/** 学生端「我的考试」 */
export const myExamApi = {
    list() {
        return request.get('/my/exams')
    }
}
