export const template = `{
  "name": "{{{name}}}",
  "description": "{{{description}}}",
  "version": "{{{version}}}",
  "author": "{{{author}}}",
  "dependencies": {
    "@boostercloud/framework-core": "^{{{boosterVersion}}}",
    "@boostercloud/framework-types": "^{{{boosterVersion}}}"
  },
  "devDependencies": {
    "@boostercloud/cli": "^{{{boosterVersion}}}",
    "rimraf": "^3.0.1",
    "@typescript-eslint/eslint-plugin": "^2.18.0",
    "@typescript-eslint/parser": "^2.18.0",
    "eslint": "^6.8.0",
    "eslint-config-prettier": "^6.10.0",
    "mocha": "^7.0.1",
    "nyc": "^15.0.0",
    "typescript": "^3.7.5",
    "ts-node": "^8.6.2",
    "@types/node": "^13.5.1"
  },
  "engines": {
    "node": ">=8.0.0"
  },
  "homepage": "{{{homepage}}}",
  "license": "{{{license}}}",
  "main": "dist/index.js",
  "repository": "{{{repository}}}",
  "scripts": {
    "lint": "eslint --ext '.js,.ts' **/*.ts",
    "compile": "tsc -b tsconfig.json",
    "deploy": "boost deploy",
    "clean": "rimraf ./dist tsconfig.tsbuildinfo",
    "test": "nyc --extension .ts mocha --forbid-only \\"test/**/*.test.ts\\""
  },
  "types": "lib/index.d.ts"
}`
