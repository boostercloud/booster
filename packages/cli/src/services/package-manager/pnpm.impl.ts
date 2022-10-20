import { PackageManagerService } from '.'
import { Layer, orDie } from '@boostercloud/framework-types/src/effect'
import { makePackageManager } from './common'

export const makePnpmPackageManager = makePackageManager('pnpm run')

export const PnpmPackageManager = Layer.fromEffect(PackageManagerService)(orDie(makePnpmPackageManager))
