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
    { pkg: 'form-data',            major: '4',  minSafe: '>=4.0.6 <5.0.0' },    // CVE-2026-12143 (high)
    { pkg: 'axios',                major: '1',  minSafe: '>=1.20.0 <2.0.0' },   // GHSA-c29m-xwm3-cm6r, GHSA-3pq3-5fj3-cg6v and others (high)
    { pkg: 'tar-fs',               major: '2',  minSafe: '>=2.1.4 <3.0.0' },    // GHSA-vj76-c3g6-qr5v, GHSA-8cj5-5rvv-wf4v (high)
    { pkg: 'glob',                 major: '10', minSafe: '>=10.5.0 <11.0.0' },  // GHSA-5j98-mcp5-4vw2 (high)
    { pkg: 'qs',                   major: '6',  minSafe: '>=6.16.1 <7.0.0' },   // GHSA-6pq8-jhm2-2vcw and others (moderate)
    { pkg: 'jws',                  major: '3',  minSafe: '>=3.2.3 <4.0.0' },    // GHSA-869p-cjfg-cm3x (high)
    { pkg: 'jws',                  major: '4',  minSafe: '>=4.0.1 <5.0.0' },    // GHSA-869p-cjfg-cm3x (high)
    { pkg: 'undici',               major: '7',  minSafe: '>=7.30.1 <8.0.0' },   // 7 high advisories, fixed in 7.30.1
    { pkg: 'shell-quote',          major: '1',  minSafe: '>=1.11.0 <2.0.0' },   // GHSA-pqg4-j6r4-53mv (critical), GHSA-395f-4hp3-45gv (high)
    { pkg: 'tar',                  major: '6',  minSafe: '>=7.5.21 <8.0.0' },   // 9 high/critical advisories; no 6.x fix exists
    { pkg: 'tar',                  major: '7',  minSafe: '>=7.5.21 <8.0.0' },   // 9 high/critical advisories
    { pkg: 'serialize-javascript', major: '6',  minSafe: '>=7.0.5 <8.0.0' },   // GHSA-5c6j-r48x-rmvq (high), GHSA-qj8w-gfj5-8c6v (moderate)
    { pkg: 'serialize-javascript', major: '7',  minSafe: '>=7.1.3 <8.0.0' },   // GHSA-m5mm-p8h7-m29v (low), newest 7.x fix
    { pkg: 'flatted',              major: '2',  minSafe: '>=3.4.2 <4.0.0' },    // CVE-2026-32141, CVE-2026-33228 (high)
    { pkg: 'flatted',              major: '3',  minSafe: '>=3.4.2 <4.0.0' },    // CVE-2026-33228 (high)
    { pkg: 'minimatch',            major: '5',  minSafe: '>=5.1.8 <6.0.0' },    // GHSA series on minimatch 5 (high)
    { pkg: 'brace-expansion',      major: '1',  minSafe: '>=1.1.20 <2.0.0' },   // CVE-2026-102278 and others (high)
    { pkg: 'brace-expansion',      major: '2',  minSafe: '>=2.1.6 <3.0.0' },    // CVE-2026-102278 and others (high)
    { pkg: 'brace-expansion',      major: '5',  minSafe: '>=5.0.11 <6.0.0' },   // CVE-2026-102278 and others (high)
    { pkg: 'ws',                   major: '8',  minSafe: '>=8.20.1 <9.0.0' },   // GHSA on ws 8.x (moderate)
    { pkg: 'nanoid',               major: '3',  minSafe: '>=3.3.18 <4.0.0' },   // CVE-2026-67213, CVE-2026-67214 (high)
    { pkg: 'js-yaml',              major: '3',  minSafe: '>=3.15.2 <4.0.0' },   // CVE-2026-84375 and others (high)
    { pkg: 'js-yaml',              major: '4',  minSafe: '>=4.3.2 <5.0.0' },    // CVE-2026-84375 and others (high)
    { pkg: 'yaml',                 major: '1',  minSafe: '>=1.10.3 <2.0.0' },   // GHSA on yaml 1.x (moderate)
    { pkg: 'follow-redirects',     major: '1',  minSafe: '>=1.16.1 <2.0.0' },   // CVE-2026-111756, CVE-2026-111755 (high)
    { pkg: 'fast-uri',             major: '3',  minSafe: '>=3.1.7 <4.0.0' },    // 7 high advisories, fixed in 3.1.7
    { pkg: 'proxy-addr',           major: '2',  minSafe: '>=2.0.8 <3.0.0' },    // CVE-2026-90711 (critical)
    { pkg: 'body-parser',          major: '1',  minSafe: '>=1.20.9 <2.0.0' },   // CVE-2026-111181 (high)
    { pkg: 'browserslist',         major: '4',  minSafe: '>=4.28.7 <5.0.0' },   // CVE-2026-73089, CVE-2026-73088 (high)
    { pkg: '@xmldom/xmldom',       major: '0',  minSafe: '>=0.9.12 <0.10.0' },  // 11 high advisories, fixed in 0.9.12
    { pkg: 'velocityjs',           major: '2',  minSafe: '>=2.1.7 <3.0.0' },    // CVE-2026-73649 (critical)
    { pkg: 'tmp',                  major: '0',  minSafe: '>=0.2.6 <0.3.0' },    // CVE-2026-44705 (high)
    { pkg: 'adm-zip',              major: '0',  minSafe: '>=0.6.1 <0.7.0' },    // CVE-2026-39244, CVE-2026-77301, CVE-2026-102282 (high)
    { pkg: 'toml',                 major: '3',  minSafe: '>=5.0.1 <6.0.0' },    // GHSA-82x6-q7mm-w9cf (high), GHSA-4qx8-w7c3-pj3p and others; reached via @effect/cli
    { pkg: 'zod',                  major: '3',  minSafe: '>=4.6.6 <5.0.0' },    // GHSA-mrhx-c7xc-m3w5 (high); reached via @cdktn/hcl2cdk
    { pkg: '@azure/msal-node',     major: '5',  minSafe: '>=7.1.0 <8.0.0' },    // GHSA-xcx6-jx8p-x3h5 (high); reached via @azure/identity
    { pkg: 'simple-git',           major: '3',  minSafe: '>=4.0.1 <5.0.0' },    // CVE-2026-102828 (critical), CVE-2026-102826 (high)
  ];

  for (const { pkg, major, minSafe } of securityOverrides) {
    const spec = packageJson.dependencies && packageJson.dependencies[pkg];
    if (spec && new RegExp('^[\\^~]?' + major + '\\.').test(spec)) {
      packageJson.dependencies[pkg] = minSafe;
    }
  }

  return packageJson;
}
