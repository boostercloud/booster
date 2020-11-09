import { forceLernaRebuild } from '../helper/depsHelper'

before(async () => {
  await forceLernaRebuild()
})
