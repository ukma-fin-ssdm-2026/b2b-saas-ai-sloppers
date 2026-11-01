import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import security from 'eslint-plugin-security'
import tseslint from 'typescript-eslint'
import prettier from 'eslint-config-prettier'

export default tseslint.config(
  { ignores: ['dist', 'node_modules', 'coverage'] },
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      js.configs.recommended,
      ...tseslint.configs.recommended,
      reactHooks.configs.flat.recommended,
      reactRefresh.configs.vite,
      security.configs.recommended,
      prettier,
    ],
    languageOptions: {
      ecmaVersion: 2022,
      globals: globals.browser,
    },
    rules: {
      // Data-fetch pages legitimately reset state when route/filter inputs change.
      'react-hooks/set-state-in-effect': 'off',
      // AuthContext exports Provider + hook from one module (common pattern).
      'react-refresh/only-export-components': [
        'error',
        { allowConstantExport: true, allowExportNames: ['useAuth'] },
      ],
    },
  },
  {
    // Third-party CircularCarousel syncs refs during render for the rAF loop.
    files: ['**/CircularCarousel.tsx'],
    rules: {
      'react-hooks/refs': 'off',
      'security/detect-object-injection': 'off',
    },
  },
)
