import { PackageManagerService } from '.'
import { Layer, orDie } from '@boostercloud/framework-types/dist/effect'
import { makePackageManager } from './common'

export const makeNpmPackageManager = makePackageManager('npm')

export const NpmPackageManager = Layer.fromEffect(PackageManagerService)(orDie(makeNpmPackageManager))
