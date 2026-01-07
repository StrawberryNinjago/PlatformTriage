import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      // Deployment Doctor API (platformtriage)
      '/api/deployment': {
        target: 'http://localhost:8082',
        changeOrigin: true
      },
      // DB Doctor APIs (dbtriage) - all other /api calls
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true
      }
    }
  }
})
