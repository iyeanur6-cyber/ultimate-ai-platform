import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  base: './', // গিটহাব পেজেস রিলেটিভ এসেট লিঙ্কিং ফিক্স
  server: {
    port: 3000,
    host: true
  }
});
