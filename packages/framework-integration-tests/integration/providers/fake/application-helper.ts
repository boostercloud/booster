import * as path from 'path'
import { sandboxPath } from './constants'
import { BoosterApp } from '@boostercloud/framework-types'

export function loadBoosterApp(): BoosterApp {
  return require(path.join(sandboxPath, 'dist', 'index.js')).Booster
}
