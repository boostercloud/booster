import { setEnv, checkConfigAnd, hopeTheBest } from '../utils'

before(async () => {
  await setEnv()
  await checkConfigAnd(hopeTheBest)
})
