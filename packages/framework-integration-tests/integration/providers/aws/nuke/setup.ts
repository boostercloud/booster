import { nuke } from '../../../../src/deploy'
import { setEnv, checkConfigAnd } from '../utils'

before(async () => {
  await setEnv()
  await checkConfigAnd(nuke)
})
