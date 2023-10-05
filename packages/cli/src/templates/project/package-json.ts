// New projects use the same Booster version as the installed CLI
const VERSION = require('../../../package.json').version

export const template = `{
  "name": "{{{projectName}}}",
  "description": "{{{description}}}",
  "version": "{{{version}}}",
  "author": "{{{author}}}",
  "dependencies": {
    "tslib": "^2.4.0",
    "@boostercloud/framework-core": "^${VERSION}",
    "@boostercloud/framework-types": "^${VERSION}",
    "@boostercloud/framework-common-helpers": "^${VERSION}",
    "{{{providerPackageName}}}": "^${VERSION}"
  },
  "devDependencies": {
    "@boostercloud/framework-provider-local": "^${VERSION}",
    "@boostercloud/framework-provider-local-infrastructure": "^${VERSION}",
    "@boostercloud/metadata-booster": "^${VERSION}",
    "{{{providerPackageName}}}-infrastructure": "^${VERSION}",
    "rimraf": "^5.0.0",
    "@typescript-eslint/eslint-plugin": "4.22.1",
    "@typescript-eslint/parser": "4.22.1",
    "eslint": "7.26.0",
    "eslint-config-prettier": "8.3.0",
    "eslint-plugin-prettier": "3.4.0",
    "mocha": "10.2.0",
    "@types/mocha": "10.0.1",
    "@types/jsonwebtoken": "9.0.1",
    "nyc": "15.1.0",
    "prettier":  "2.3.0",
    "typescript": "4.7.4",
    "ts-node": "9.1.1",
    "@types/node": "^18.15.3",
    "ts-patch": "2.0.2",
    "graphql": "^16.6.0"
  },
  "engines": {
    "node": ">=18.0.0 <19.0.0"
  },
  "homepage": "{{{homepage}}}",
  "license": "{{{license}}}",
  "main": "dist/index.js",
  "repository": "{{{repository}}}",
  "scripts": {
    "prepare": "ts-patch install -s",
    "lint:check": "eslint --ext '.js,.ts' **/*.ts",
    "lint:fix": "eslint --quiet --fix --ext '.js,.ts' **/*.ts",
    "build": "tsc -b tsconfig.json",
    "clean": "rimraf ./dist tsconfig.tsbuildinfo"
  },
  "types": "lib/index.d.ts"
}`
