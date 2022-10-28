import { PackageManagerService } from '.'
import { Layer, orDie } from '@boostercloud/framework-types/dist/effect'
import { makePackageManager } from './common'

export const makeYarnPackageManager = makePackageManager('yarn')

export const YarnPackageManager = Layer.fromEffect(PackageManagerService)(orDie(makeYarnPackageManager))
