import * as path from 'path'
import * as process from 'process'
import { getLogger } from '@boostercloud/framework-common-helpers'
import { BoosterConfig } from '@boostercloud/framework-types'

export function boosterVersion(config: BoosterConfig) {
  const projectAbsolutePath = path.resolve(process.cwd())
  const logger = getLogger(config, 'boosterVersion')
  try {
    const packageJsonContents = require(path.join(projectAbsolutePath, 'package.json'))
    const version = packageJsonContents.dependencies['@boostercloud/framework-core']
    if (!version) {
      logger.warn('Could not get Booster Version')
      return ''
    }
    const versionParts = version.replace('workspace:', '').replace('^', '').replace('.tgz', '').split('-')
    return versionParts[versionParts.length - 1]
  } catch (e) {
    logger.warn('There was an error when calculating the booster version the application', e)
    return ''
  }
}
