// New projects use the same Booster version as the installed CLI
const VERSION = require('../../../package.json').version

export const template = `{
  "name": "{{{projectName}}}",
  "description": "{{{description}}}",
  "version": "{{{version}}}",
  "author": "{{{author}}}",
  "dependencies": {
    "@boostercloud/framework-core": "^${VERSION}",
    "@boostercloud/framework-types": "^${VERSION}",
    "{{{providerPackageName}}}": "*",
    "tslib": "^2.0.3"
  },
  "devDependencies": {
    "{{{providerPackageName}}}-infrastructure": "*",
    "rimraf": "^3.0.1",
    "@typescript-eslint/eslint-plugin": "^2.18.0",
    "@typescript-eslint/parser": "^2.18.0",
    "eslint": "^6.8.0",
    "eslint-config-prettier": "^6.10.0",
    "eslint-plugin-prettier": "^3.1.2",
    "mocha": "^7.0.1",
    "nyc": "^15.0.0",
    "prettier": "^1.19.1",
    "typescript": "4.1.5",
    "ts-node": "9.1.1",
    "@types/node": "^13.5.1",
    "ttypescript": "1.5.12",
    "metadata-booster": "0.3.1"
  },
  "engines": {
    "node": ">=8.0.0"
  },
  "homepage": "{{{homepage}}}",
  "license": "{{{license}}}",
  "main": "dist/index.js",
  "repository": "{{{repository}}}",
  "scripts": {
    "lint:check": "eslint --ext '.js,.ts' **/*.ts",
    "lint:fix": "eslint --quiet --fix --ext '.js,.ts' **/*.ts",
    "compile": "npx ttypescript -b tsconfig.json",
    "deploy": "boost deploy",
    "clean": "npx rimraf ./dist tsconfig.tsbuildinfo",
    "test": "AWS_SDK_LOAD_CONFIG=true BOOSTER_ENV=test npx nyc --extension .ts mocha --forbid-only \\"test/**/*.test.ts\\""
  },
  "types": "lib/index.d.ts"
}`
