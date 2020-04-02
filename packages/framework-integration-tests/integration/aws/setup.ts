import { deploy, nuke } from '../../src/deploy'

before(async () => {
  await deploy()
})

after(async () => {
  await nuke()
})
