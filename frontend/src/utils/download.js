import request from '@/utils/request'

/**
 * 触发浏览器保存二进制文件（Excel 等）。
 *
 * 为什么单独抽一个工具：
 * 1. 下载接口返回的是 blob，不走 { code, message, data } 约定，需要单独处理；
 * 2. 后端用 RFC 5987 的 `filename*=UTF-8''...` 传中文文件名，
 *    直接拿 `filename=` 会得到乱码，这里两种都解析；
 * 3. 错误时后端仍会返回 JSON（例如 403 无权限），但它被包在 blob 里，
 *    必须读出来才能给出有意义的提示，否则只会显示"网络异常"。
 */
export async function downloadFile(url, fallbackName = 'download.xlsx') {
    const res = await request.get(url, { responseType: 'blob' })
    const blob = res.data

    // 后端返回 JSON 说明是错误响应（Content-Type 会是 application/json）
    if (blob.type && blob.type.includes('application/json')) {
        const text = await blob.text()
        let message = '下载失败'
        try {
            message = JSON.parse(text).message || message
        } catch {
            // 保持默认提示
        }
        throw new Error(message)
    }

    const name = resolveFilename(res.headers?.['content-disposition'], fallbackName)

    const href = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = href
    link.download = name
    document.body.appendChild(link)
    link.click()
    link.remove()
    // 交给浏览器异步释放，立即释放可能导致下载被取消
    setTimeout(() => URL.revokeObjectURL(href), 1000)
    return name
}

/** 从 Content-Disposition 解析文件名，优先 filename*，其次 filename */
function resolveFilename(disposition, fallback) {
    if (!disposition) {
        return fallback
    }
    const star = /filename\*=UTF-8''([^;]+)/i.exec(disposition)
    if (star && star[1]) {
        try {
            return decodeURIComponent(star[1].trim())
        } catch {
            // 解析失败则退回 fallback
        }
    }
    const plain = /filename="?([^";]+)"?/i.exec(disposition)
    if (plain && plain[1]) {
        return plain[1].trim()
    }
    return fallback
}
