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
    { pkg: 'form-data',           major: '4',  minSafe: '>=4.0.4 <5.0.0' },    // GHSA-fjxv-7rqg-78g4 (critical)
    { pkg: 'axios',               major: '1',  minSafe: '>=1.12.0 <2.0.0' },   // GHSA-4hjh-wcwx-xvwj, GHSA-jr5f-v2jv-69x6 (high)
    { pkg: 'tar-fs',              major: '2',  minSafe: '>=2.1.4 <3.0.0' },    // GHSA-vj76-c3g6-qr5v, GHSA-8cj5-5rvv-wf4v (high)
    { pkg: 'glob',                major: '10', minSafe: '>=10.5.0 <11.0.0' },  // GHSA-5j98-mcp5-4vw2 (high)
    { pkg: 'qs',                  major: '6',  minSafe: '>=6.15.2 <7.0.0' },   // GHSA-q8mj-m7cp-5q26 (moderate)
    { pkg: 'jws',                 major: '3',  minSafe: '>=3.2.3 <4.0.0' },    // GHSA-869p-cjfg-cm3x (high)
    { pkg: 'jws',                 major: '4',  minSafe: '>=4.0.1 <5.0.0' },    // GHSA-869p-cjfg-cm3x (high)
    { pkg: 'undici',              major: '7',  minSafe: '>=7.24.0 <8.0.0' },   // GHSA-f269, GHSA-vrm6, GHSA-v9p9 (high)
    { pkg: 'shell-quote',         major: '1',  minSafe: '>=1.8.4 <2.0.0' },    // GHSA-58qx-3vcg-4xpx (critical)
    { pkg: 'tar',                 major: '6',  minSafe: '>=6.2.1 <7.0.0' },    // CVE-2024-28863 etc.
    { pkg: 'tar',                 major: '7',  minSafe: '>=7.5.11 <8.0.0' },   // GHSA series on tar 7.x (high)
    { pkg: 'serialize-javascript',major: '6',  minSafe: '>=6.0.2 <7.0.0' },    // GHSA-76p7-773f-r4q5 (moderate)
    { pkg: 'serialize-javascript',major: '7',  minSafe: '>=7.0.5 <8.0.0' },    // GHSA on 7.x (high)
    { pkg: 'flatted',             major: '3',  minSafe: '>=3.4.2 <4.0.0' },    // GHSA on flatted (high)
    { pkg: 'minimatch',           major: '5',  minSafe: '>=5.1.8 <6.0.0' },    // GHSA series on minimatch 5 (high)
    { pkg: 'brace-expansion',     major: '5',  minSafe: '>=5.0.6 <6.0.0' },    // GHSA-jxxr-4gwj-5jf2 (moderate)
    { pkg: 'ws',                  major: '8',  minSafe: '>=8.20.1 <9.0.0' },   // GHSA on ws 8.x (moderate)
    { pkg: 'nanoid',              major: '3',  minSafe: '>=3.3.8 <4.0.0' },    // GHSA on nanoid (moderate); replaces prior >=3.1.31 pin
    { pkg: 'js-yaml',             major: '4',  minSafe: '>=4.1.1 <5.0.0' },    // GHSA on js-yaml (moderate)
    { pkg: 'yaml',                major: '1',  minSafe: '>=1.10.3 <2.0.0' },   // GHSA on yaml 1.x (moderate)
    { pkg: 'follow-redirects',    major: '1',  minSafe: '>=1.16.0 <2.0.0' },   // GHSA on follow-redirects (moderate)
  ];

  for (const { pkg, major, minSafe } of securityOverrides) {
    const spec = packageJson.dependencies && packageJson.dependencies[pkg];
    if (spec && new RegExp('^[\\^~]?' + major + '\\.').test(spec)) {
      packageJson.dependencies[pkg] = minSafe;
    }
  }

  return packageJson;
}
