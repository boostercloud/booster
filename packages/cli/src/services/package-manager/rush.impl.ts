/* eslint-disable @typescript-eslint/explicit-function-return-type */
import { makePackageManager } from './common'

// TODO: Look recursively up for a rush.json file and run ./common/scripts/install-run-rushx.js
export const RushPackageManager = makePackageManager('rushx')
