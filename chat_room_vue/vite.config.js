import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue()],
    // vite 相关配置
    server: {
    port: 80,
    host: true,
    open: true,
    proxy: {
      // https://cn.vitejs.dev/config/#server-proxy
      '/stage-api': {
        target: 'http://119.91.74.48:8080', //localhost
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/stage-api/, '')
      }
    }
  }
})
