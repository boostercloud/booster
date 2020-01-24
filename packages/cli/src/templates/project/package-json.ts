export const template = `{
  "name": "{{{name}}}",
  "description": "{{{description}}}",
  "version": "{{{version}}}",
  "author": "{{{author}}}",
  "dependencies": {
    "@boostercloud/framework-core": "^0.0",
    "tslib": "^1.10"
  },
  "devDependencies": {
    "@boostercloud/cli": "^0.0",
    "@types/chai": "^4",
    "@types/mocha": "^5",
    "@types/node": "^10",
    "@typescript-eslint/eslint-plugin": "^1.12.0",
    "@typescript-eslint/parser": "^1.12.0",
    "eslint": "^6.0.1",
    "eslint-config-prettier": "^6.0.0",
    "mocha": "^5",
    "nyc": "^13",
    "typescript": "^3.7"
  },
  "engines": {
    "node": ">=8.0.0"
  },
  "homepage": "{{{homepage}}}",
  "license": "{{{license}}}",
  "main": "dist/index.js",
  "repository": "{{{repository}}}",
  "scripts": {
    "lint": "eslint --ext '.js,.ts' .",
    "compile": "tsc -b tsconfig.json",
    "deploy": "boost deploy",
    "clean": "rm -rf ./dist && rm -rf tsconfig.tsbuildinfo",
    "test": "nyc --extension .ts mocha --forbid-only \\"test/**/*.test.ts\\""
  },
  "types": "lib/index.d.ts"
}`
