import * as ConfigTs from './config-ts'
import * as EslintIgnore from './eslintignore'
import * as GitIgnore from './gitignore'
import * as IndexTs from './index-ts'
import * as PackageJson from './package-json'
import * as TsconfigJson from './tsconfig-json'
import * as TsConfigEsLint from './tsconfig.eslint-json'
import * as PrettierRc from './prettierrc-yaml'
import * as MochaRc from './mocharc-yml'
import * as EsLintRc from './eslintrc-js'

export const projectTemplates: Array<[string, string]> = [
  ['.eslintignore', EslintIgnore.template],
  ['.eslintrc.js', EsLintRc.template],
  ['.gitignore', GitIgnore.template],
  ['package.json', PackageJson.template],
  ['tsconfig.json', TsconfigJson.template],
  ['tsconfig.eslint.json', TsConfigEsLint.template],
  ['.prettierrc.yaml', PrettierRc.template],
  ['src/config/config.ts', ConfigTs.template],
  ['src/index.ts', IndexTs.template],
  ['.mocharc.yml', MochaRc.template],
]
