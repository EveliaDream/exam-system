import request from '@/utils/request'

/** 文件接口：题干配图上传。读取用相对地址直接放进 <img src>。 */
export const fileApi = {
    /**
     * 上传图片，返回 { url, filename, contentType, size }。
     * 把返回的 url 填进题目的 imageUrl 字段即可。
     */
    uploadImage(file) {
        const form = new FormData()
        form.append('file', file)
        return request.post('/files/image', form, {
            headers: { 'Content-Type': 'multipart/form-data' },
            timeout: 60000
        })
    }
}
