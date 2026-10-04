// Karma configuration.
//
// This file did not exist, so `ng test` used the CLI defaults: Chrome, not
// headless, and no sandbox flags. On a CI runner there is no display, so the
// launcher either fails to start or hangs forever in watch mode.
//
// ChromeHeadlessNoSandbox is required because containers and CI runners usually
// run as root or under a user namespace where the Chromium sandbox cannot be
// initialised. --no-sandbox is only acceptable here because the browser loads
// nothing but the local bundle.
//
// CHROME_BIN is only filled in when it is missing and a known install path
// exists. The previous version resolved it through require('which'), which is
// not a declared dependency, and fell back to a hardcoded Windows path that
// cannot exist on a Linux runner. On GitHub Actions the runner image installs
// Chrome and sets CHROME_BIN itself, so on Linux nothing is set here and
// karma-chrome-launcher finds the browser the way it normally does.
if (!process.env.CHROME_BIN) {
  const { existsSync } = require('fs');

  const windowsCandidates = [
    'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
    'C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe',
  ];

  const found = windowsCandidates.find((candidate) => existsSync(candidate));
  if (found) {
    process.env.CHROME_BIN = found;
  }
}

module.exports = function (config) {
  config.set({
    basePath: '',
    frameworks: ['jasmine', '@angular-devkit/build-angular'],

    plugins: [
      require('karma-jasmine'),
      require('karma-chrome-launcher'),
      require('karma-jasmine-html-reporter'),
      require('karma-coverage'),
      require('@angular-devkit/build-angular/plugins/karma'),
    ],

    client: {
      jasmine: {
        // Reports every spec failure instead of stopping at the first one, so a
        // single run lists everything that needs fixing.
        random: false,
      },
      clearContext: false,
    },

    jasmineHtmlReporter: {
      suppressAll: true,
    },

    coverageReporter: {
      dir: require('path').join(__dirname, './coverage/crm'),
      subdir: '.',
      reporters: [
        { type: 'html' },
        { type: 'text-summary' },
        { type: 'lcovonly' },
        // Read by scripts/check-coverage.mjs. The text and lcov reporters are
        // for humans; this one is machine readable and gives the totals the
        // coverage gate compares, without the pipeline having to parse lcov.
        { type: 'json-summary' },
      ],
    },

    reporters: ['progress', 'kjhtml'],

    port: 9876,
    colors: true,
    logLevel: config.LOG_INFO,
    autoWatch: false,
    singleRun: true,
    restartOnFileChange: false,

    browsers: ['ChromeHeadlessNoSandbox'],

    customLaunchers: {
      ChromeHeadlessNoSandbox: {
        base: 'ChromeHeadless',
        flags: [
          '--no-sandbox',
          '--disable-gpu',
          '--disable-dev-shm-usage',
          '--headless=new',
        ],
      },
    },
  });
};