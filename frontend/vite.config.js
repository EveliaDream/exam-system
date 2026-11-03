import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// 开发期：前端 5173，后端 8080。
// 通过代理转发 /api，浏览器视角全部同源 —— 从根上避免 CORS 配置问题，
// 后端不需要写任何 @CrossOrigin 或 WebMvcConfigurer.addCorsMappings。
export default defineConfig({
    plugins: [vue()],
    resolve: {
        alias: {
            '@': fileURLToPath(new URL('./src', import.meta.url))
        }
    },
    server: {
        port: 5173,
        proxy: {
            '/api': {
                target: 'http://localhost:8080',
                changeOrigin: true
            }
        },
        watch: {
            // 编辑器的原子写入会在 src 下临时生成 .xxx.tmpdir 目录，
            // 被文件监听器抓到后会抛 EBUSY 直接把 dev server 弄崩，这里统一忽略。
            ignored: ['**/.*.tmpdir/**', '**/*.tmp']
        }
    },
    build: {
        // 交付时打包进 Spring Boot 的静态资源目录，最终只产出一个可执行 jar
        outDir: '../src/main/resources/static',
        emptyOutDir: true
    }
})
