import { nuke } from '../../../../src/deploy'
import { setEnv, checkConfigAnd } from '../utils'

before(async () => {
  await setEnv()
  await checkConfigAnd(nuke)
})

after(async () => {
  if (!process.env['FULL_INTEGRATION_TEST']) {
    await checkConfigAnd(nuke)
  }
})
