import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  // Pinned away from Vite's 5173 default, which collides with another local project on this
  // machine. strictPort so a future collision fails loudly instead of silently moving elsewhere.
  server: {
    port: 5180,
    strictPort: true,
  },
})
