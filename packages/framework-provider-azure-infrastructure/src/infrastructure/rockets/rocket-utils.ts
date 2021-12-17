import * as path from 'path'
import { toTerraformName } from '../helper/utils'

export interface RocketUtils {
  scriptFilePath: (functionDefinitionScriptFile: string) => string
  toTerraformName: (name: string, suffix: string) => string
}

export const buildRocketUtils = (packageName: string): RocketUtils => ({
  scriptFilePath: (functionDefinitionScriptFile: string) =>
    path.join('..', 'node_modules', packageName, functionDefinitionScriptFile),
  toTerraformName: (name: string, suffix: string) => toTerraformName(name, suffix),
})
