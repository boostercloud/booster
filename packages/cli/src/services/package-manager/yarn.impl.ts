import { PackageManagerService } from '.'
import { Layer, orDie } from '@boostercloud/framework-types/src/effect'
import { makePackageManager } from './common'

export const makeYarnPackageManager = makePackageManager('yarn run')

export const YarnPackageManager = Layer.fromEffect(PackageManagerService)(orDie(makeYarnPackageManager))
