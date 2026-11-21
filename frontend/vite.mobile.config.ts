import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
export default defineConfig({ root: 'mobile', plugins: [react()], publicDir: false, build: { outDir: '../dist-mobile', emptyOutDir: true } });
