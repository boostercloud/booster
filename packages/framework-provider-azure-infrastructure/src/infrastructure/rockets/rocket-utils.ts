import { toTerraformName } from '../helper/utils'

export interface RocketUtils {
  toTerraformName: (name: string, suffix: string) => string
}

export const buildRocketUtils = (): RocketUtils => ({
  toTerraformName: (name: string, suffix: string) => toTerraformName(name, suffix),
})
