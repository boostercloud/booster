import * as chai from 'chai'

// TODO: Make this compatible with ES Modules
// More info: https://github.com/sindresorhus/eslint-plugin-unicorn/blob/v43.0.2/docs/rules/prefer-module.md
// eslint-disable-next-line unicorn/prefer-module
chai.use(require('sinon-chai'))

// TODO: Make this compatible with ES Modules
// More info: https://github.com/sindresorhus/eslint-plugin-unicorn/blob/v43.0.2/docs/rules/prefer-module.md
// eslint-disable-next-line unicorn/prefer-module
chai.use(require('chai-as-promised'))

export const expect = chai.expect
