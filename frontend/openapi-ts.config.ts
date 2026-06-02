import { defineConfig } from '@hey-api/openapi-ts'

export default defineConfig({
  input: '../api/campalert-api.yaml',
  output: 'src/api/generated',
  plugins: ['@hey-api/client-axios']
})
