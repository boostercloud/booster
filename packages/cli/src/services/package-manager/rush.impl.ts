import { PackageManagerService } from '.'
import { Layer, orDie } from '@boostercloud/framework-types/src/effect'
import { makePackageManager } from './common'

// TODO: Look recursively up for a rush.json file and run ./common/scripts/install-run-rushx.js
export const makeRushPackageManager = makePackageManager('rushx')

export const RushPackageManager = Layer.fromEffect(PackageManagerService)(orDie(makeRushPackageManager))
