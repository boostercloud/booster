import { setEnv, checkConfigAnd, hopeTheBest } from '../providers/aws/utils'

before(async () => {
  // Make sure that the AWS environment is ready to run the tests
  await setEnv()
  await checkConfigAnd(hopeTheBest)
})
