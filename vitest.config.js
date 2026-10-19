import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    environment: 'jsdom',
    include: ['src/test/frontend/**/*.test.js'],
    passWithNoTests: true,
    environmentOptions: {
      jsdom: {
        url: 'http://localhost/'
      }
    }
  }
});
