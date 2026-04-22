import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import tseslint from 'typescript-eslint'
import { defineConfig, globalIgnores } from 'eslint/config'
import { fileURLToPath } from 'node:url'

const tsconfigRootDir = fileURLToPath(new URL('.', import.meta.url))

const fsdImportRule = (disallowedGroups, message) => ({
  'no-restricted-imports': [
    'error',
    {
      patterns: disallowedGroups.map((group) => ({ group: [group], message })),
    },
  ],
})

export default defineConfig([
  globalIgnores(['dist', 'coverage']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      js.configs.recommended,
      tseslint.configs.recommended,
      reactHooks.configs.flat.recommended,
      reactRefresh.configs.vite,
    ],
    languageOptions: {
      ecmaVersion: 2020,
      globals: globals.browser,
      parserOptions: {
        projectService: true,
        tsconfigRootDir,
      },
    },
    rules: {
      '@typescript-eslint/no-floating-promises': 'error',
      '@typescript-eslint/no-misused-promises': 'error',
    },
  },
  {
    files: ['src/shared/**/*.{ts,tsx}'],
    rules: fsdImportRule(
      ['@/app/**', '@/pages/**', '@/features/**'],
      'shared 레이어는 app/pages/features를 import하면 안 됩니다.'
    ),
  },
  {
    files: ['src/features/**/*.{ts,tsx}'],
    rules: fsdImportRule(
      ['@/app/**', '@/pages/**'],
      'features 레이어는 app/pages를 import하면 안 됩니다.'
    ),
  },
  {
    files: ['src/pages/**/*.{ts,tsx}'],
    rules: fsdImportRule(
      ['@/app/**', '@/features/*/*'],
      'pages 레이어는 app 또는 feature 내부 경로를 import하면 안 됩니다. feature 공개 API를 사용하세요.'
    ),
  },
  {
    files: ['src/app/**/*.{ts,tsx}'],
    rules: fsdImportRule(
      ['@/features/*/*'],
      'app 레이어는 feature 공개 API 경로로만 import해야 합니다.'
    ),
  },
])
