// ESLint flat configuration for the CRM frontend.
//
// Three decisions here, all deliberate.
//
// Formatting is not linted. Prettier owns it, and eslint-config-prettier is
// spread across the end to switch the stylistic rules off that would otherwise
// fight the formatter about the same lines.
//
// Nothing from the recommended sets blocks the build on the day it is switched
// on. Every one of those rules is demoted to a warning by demoting(), and only
// the short list below is promoted back to an error. The reason is not
// leniency: the service code predates any static analysis, so the recommended
// sets produce hundreds of findings on the first run, and a build that is red
// before anyone has changed anything is a build everyone learns to ignore. The
// findings are still printed, which is what the backlog is counted from.
//
// The promoted rules are the ones that cannot be wrong. A debugger left in
// committed code, a var in a codebase that is otherwise const and let, a
// condition that is always true, an empty catch.

const eslint = require('@eslint/js');
const tseslint = require('typescript-eslint');
const angular = require('angular-eslint');
const prettier = require('eslint-config-prettier');

/** Rewrites every rule of a config to a warning, keeping its options. */
function demote(config) {
  const rules = config.rules ?? {};
  return {
    ...config,
    rules: Object.fromEntries(
      Object.entries(rules).map(([name, value]) => [
        name,
        Array.isArray(value) ? ['warn', ...value.slice(1)] : 'warn',
      ])
    ),
  };
}

module.exports = tseslint.config(
  {
    // Nothing here is linted: build output, dependencies, and the fixtures that
    // are not source.
    ignores: ['dist/**', 'coverage/**', '.angular/**', 'node_modules/**', '**/*.d.ts'],
  },

  // TypeScript sources: the components, services, guards and interceptors.
  {
    files: ['**/*.ts'],
    extends: [
      demote(eslint.configs.recommended),
      ...tseslint.configs.recommended.map(demote),
      ...angular.configs.tsRecommended.map(demote),
    ],
    processor: angular.processInlineTemplates,
    rules: {
      // Blocking. Each of these is code that compiles and then does something
      // other than what it reads like.
      'no-debugger': 'error',
      'no-var': 'error',
      'no-constant-condition': 'error',
      'no-dupe-keys': 'error',
      'no-duplicate-case': 'error',
      'no-unreachable': 'error',
      'no-self-assign': 'error',
      'no-unsafe-finally': 'error',
      'use-isnan': 'error',
      'valid-typeof': 'error',

      // An empty catch is how the old logout() swallowed the 401 the server
      // returned. That bug is the reason this one is an error and not a note.
      'no-empty': ['error', { allowEmptyCatch: false }],

      // The token is read and written directly by the auth service, and it is
      // the one thing every debugging session needs to be able to see.
      'no-console': 'off',
    },
  },

  // Inline templates. Angular 18 compiles them into the component, so without
  // the processor above these rules would never see the markup.
  {
    files: ['**/*.html'],
    extends: [
      ...angular.configs.templateRecommended.map(demote),
      ...angular.configs.templateAccessibility.map(demote),
    ],
  },

  // Specs. Relaxed on purpose: a test is allowed to be blunt, and the point
  // here is that the tests themselves stay type-checked.
  {
    files: ['**/*.spec.ts'],
    languageOptions: {
      // Jasmine's globals. Without this every spec reports describe, it and
      // expect as undefined, which is a gap in this file rather than a defect
      // in the tests. It also drowned the real findings: the first pipeline run
      // printed 699 warnings of which a large share were these three names.
      globals: {
        afterAll: 'readonly',
        afterEach: 'readonly',
        beforeAll: 'readonly',
        beforeEach: 'readonly',
        describe: 'readonly',
        expect: 'readonly',
        fail: 'readonly',
        fdescribe: 'readonly',
        fit: 'readonly',
        it: 'readonly',
        jasmine: 'readonly',
        pending: 'readonly',
        spyOn: 'readonly',
        spyOnProperty: 'readonly',
        xdescribe: 'readonly',
        xit: 'readonly',
      },
    },
    rules: {
      '@typescript-eslint/no-unused-expressions': 'off',
    },
  },

  // Must stay last: it turns off every stylistic rule that conflicts with
  // Prettier, and would otherwise undo the rules above.
  prettier
);
