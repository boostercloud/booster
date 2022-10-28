import { PackageManagerService } from '.'
import { Layer, orDie } from '@boostercloud/framework-types/dist/effect'
import { makePackageManager } from './common'

export const makePnpmPackageManager = makePackageManager('pnpm')

export const PnpmPackageManager = Layer.fromEffect(PackageManagerService)(orDie(makePnpmPackageManager))
