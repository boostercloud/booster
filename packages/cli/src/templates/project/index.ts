import * as ConfigTs from './config-ts'
import * as EslintIgnore from './eslintignore'
import * as GitIgnore from './gitignore'
import * as IndexTs from './index-ts'
import * as PackageJson from './package-json'
import * as TsconfigJson from './tsconfig-json'

export const templates = {
  configTs: ConfigTs.template,
  eslintIgnore: EslintIgnore.template,
  gitIgnore: GitIgnore.template,
  indexTs: IndexTs.template,
  packageJson: PackageJson.template,
  tsconfigJson: TsconfigJson.template,
}
