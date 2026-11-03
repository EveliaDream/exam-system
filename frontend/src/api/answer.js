import request from '@/utils/request'

/** 学生作答接口。对应后端 /api/my/*（学生专属）。 */
export const takingApi = {
    /** 开始或继续考试（幂等） */
    start(examId) {
        return request.post(`/my/exams/${examId}/start`)
    },

    /** 刷新后恢复试卷 */
    paper(examId) {
        return request.get(`/my/exams/${examId}/paper`)
    },

    /**
     * 自动保存。answers: [{ questionId, answer, timeSpentSec, submitCount }]
     * 返回 { savedAt, remainingSeconds, submitted, recordId }；
     * submitted=true 表示已超时被服务端自动交卷。
     */
    save(recordId, answers) {
        return request.put(`/my/records/${recordId}/answers`, { answers })
    },

    submit(recordId) {
        return request.post(`/my/records/${recordId}/submit`)
    },

    result(recordId) {
        return request.get(`/my/records/${recordId}/result`)
    },

    /** 切屏上报（只留痕，不判作弊） */
    reportSwitch(recordId) {
        return request.post(`/my/records/${recordId}/switch-screen`)
    },

    /** 我的成绩列表 */
    myRecords() {
        return request.get('/my/records')
    }
}
