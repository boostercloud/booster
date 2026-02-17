'use strict';

/**
 * When using the PNPM package manager, you can use pnpmfile.js to workaround
 * dependencies that have mistakes in their package.json file.  (This feature is
 * functionally similar to Yarn's "resolutions".)
 *
 * For details, see the PNPM documentation:
 * https://pnpm.js.org/docs/en/hooks.html
 *
 * IMPORTANT: SINCE THIS FILE CONTAINS EXECUTABLE CODE, MODIFYING IT IS LIKELY TO INVALIDATE
 * ANY CACHED DEPENDENCY ANALYSIS.  After any modification to pnpmfile.js, it's recommended to run
 * "rush update --full" so that PNPM will recalculate all version selections.
 */
module.exports = {
  hooks: {
    readPackage
  }
};

/**
 * This hook is invoked during installation before a package's dependencies
 * are selected.
 * The `packageJson` parameter is the deserialized package.json
 * contents for the package that is about to be installed.
 * The `context` parameter provides a log() function.
 * The return value is the updated object.
 */
function readPackage(packageJson, context) {

  // // The karma types have a missing dependency on typings from the log4js package.
  // if (packageJson.name === '@types/karma') {
  //  context.log('Fixed up dependencies for @types/karma');
  //  packageJson.dependencies['log4js'] = '0.6.38';
  // }

  // Security overrides for transitive dependencies (high/critical vulnerabilities).
  // Each entry targets a specific major version line to avoid breaking cross-major deps.
  const securityOverrides = [
    { pkg: 'form-data', major: '4', minSafe: '>=4.0.4 <5.0.0' },    // GHSA-fjxv-7rqg-78g4 (critical)
    { pkg: 'axios',     major: '1', minSafe: '>=1.12.0 <2.0.0' },    // GHSA-4hjh-wcwx-xvwj, GHSA-jr5f-v2jv-69x6 (high)
    { pkg: 'tar-fs',    major: '2', minSafe: '>=2.1.4 <3.0.0' },     // GHSA-vj76-c3g6-qr5v, GHSA-8cj5-5rvv-wf4v (high)
    { pkg: 'glob',      major: '10', minSafe: '>=10.5.0 <11.0.0' },   // GHSA-5j98-mcp5-4vw2 (high)
    { pkg: 'qs',        major: '6', minSafe: '>=6.14.1 <7.0.0' },    // GHSA-6rw7-vpxm-498p (high)
    { pkg: 'jws',       major: '3', minSafe: '>=3.2.3 <4.0.0' },     // GHSA-869p-cjfg-cm3x (high)
    { pkg: 'jws',       major: '4', minSafe: '>=4.0.1 <5.0.0' },     // GHSA-869p-cjfg-cm3x (high)
  ];

  for (const { pkg, major, minSafe } of securityOverrides) {
    const spec = packageJson.dependencies && packageJson.dependencies[pkg];
    if (spec && new RegExp('^[\\^~]?' + major + '\\.').test(spec)) {
      packageJson.dependencies[pkg] = minSafe;
    }
  }

  return packageJson;
}
