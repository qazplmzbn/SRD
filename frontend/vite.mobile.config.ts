import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
const root = 'mobile';

export default defineConfig(({ command, mode }) => {
  if (command === 'build') {
    const raw = loadEnv(mode, root, 'VITE_').VITE_API_BASE;
    let url: URL | undefined;
    try { url = new URL(raw); } catch { /* Report the configuration error below. */ }
    if (!url || url.protocol !== 'https:' || raw !== url.origin ||
        /^(localhost\.?|127\..*|\[::1\])$/i.test(url.hostname)) {
      throw new Error(`Set VITE_API_BASE to the deployed HTTPS origin (no path or trailing slash) in mobile/.env.${mode}.local or the build environment.`);
    }
  }
  return { root, plugins: [react()], publicDir: false, build: { outDir: '../dist-mobile', emptyOutDir: true } };
});
