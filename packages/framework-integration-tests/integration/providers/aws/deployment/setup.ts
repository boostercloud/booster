import { deploy } from '../../../../src/deploy'
import { sleep } from '../../../helper/sleep'
import { setEnv, checkConfigAnd } from '../utils'

before(async () => {
  await setEnv()
  await checkConfigAnd(deploy)
  console.log('Waiting 30 seconds after deployment to let the stack finish its initialization...')
  await sleep(30000)
  console.log('...sleep finished. Let the tests begin 🔥!')
})
