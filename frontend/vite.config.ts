import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          'element-plus': ['element-plus'],
          'vue-vendor': ['axios', 'pinia', 'vue', 'vue-router'],
        },
      },
    },
  },
  test: {
    environment: 'jsdom',
  },
})
