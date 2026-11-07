import request from '@/utils/request'

/** 学情分析接口。 */
export const analyticsApi = {
    /** 教师端：一场考试的学情看板（成绩统计 + 难度贴合 + 知识点掌握 + 成绩表） */
    exam(examId, classId) {
        return request.get(`/analytics/exams/${examId}`, {
            params: classId == null ? {} : { classId }
        })
    },

    /** 学生端：我的知识点掌握度与薄弱知识点 */
    myAnalysis(recordId) {
        return request.get(`/my/records/${recordId}/analysis`)
    },

    /** 学生端：逐题作答详情（含正确答案与解析） */
    myDetail(recordId) {
        return request.get(`/my/records/${recordId}/detail`)
    }
}
