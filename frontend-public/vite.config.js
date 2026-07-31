import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  // Pinned (see the admin frontend's vite.config.js for why): this machine runs many other
  // projects' dev servers, so a fixed, verified-free port beats Vite's default + auto-increment.
  server: {
    port: 5181,
    strictPort: true,
  },
})
