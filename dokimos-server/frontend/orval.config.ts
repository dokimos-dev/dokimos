import { defineConfig } from 'orval';

export default defineConfig({
  dokimos: {
    input: 'http://localhost:8080/v3/api-docs',
    output: {
      target: './src/lib/api/generated.ts',
      client: 'swr',
      mode: 'tags-split',
      prettier: true,
    },
  },
});
