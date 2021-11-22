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
    "{{{providerPackageName}}}": "*"
  },
  "devDependencies": {
    "@boostercloud/framework-provider-local": "^${VERSION}",
    "@boostercloud/framework-provider-local-infrastructure": "^${VERSION}",
    "{{{providerPackageName}}}-infrastructure": "*",
    "rimraf": "^3.0.1",
    "@typescript-eslint/eslint-plugin": "4.22.1",
    "@typescript-eslint/parser": "4.22.1",
    "eslint": "7.26.0",
    "eslint-config-prettier": "8.3.0",
    "eslint-plugin-prettier": "3.4.0",
    "mocha": "8.4.0",
    "@types/mocha": "8.2.2",
    "nyc": "15.1.0",
    "prettier":  "2.3.0",
    "typescript": "4.2.4",
    "ts-node": "9.1.1",
    "@types/node": "15.0.2",
    "ttypescript": "1.5.12",
    "metadata-booster": "0.3.1",
    "cdktf": "0.7.0",
    "cdktf-cli": "^0.7.0"
  },
  "engines": {
    "node": ">=14.0.0"
  },
  "homepage": "{{{homepage}}}",
  "license": "{{{license}}}",
  "main": "dist/index.js",
  "repository": "{{{repository}}}",
  "scripts": {
    "lint:check": "eslint --ext '.js,.ts' **/*.ts",
    "lint:fix": "eslint --quiet --fix --ext '.js,.ts' **/*.ts",
    "compile": "ttsc -b tsconfig.json",
    "clean": "rimraf ./dist tsconfig.tsbuildinfo",
    "test": "AWS_SDK_LOAD_CONFIG=true BOOSTER_ENV=test nyc --extension .ts mocha --forbid-only \\"test/**/*.test.ts\\""
  },
  "types": "lib/index.d.ts"
}`
