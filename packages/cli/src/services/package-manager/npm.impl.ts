import { PackageManagerService } from '.'
import { Layer, orDie } from '@boostercloud/framework-types/src/effect'
import { makePackageManager } from './common'

export const makeNpmPackageManager = makePackageManager('npm run')

export const NpmPackageManager = Layer.fromEffect(PackageManagerService)(orDie(makeNpmPackageManager))
