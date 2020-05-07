import { buildDependenciesLocally } from '../../src/deploy'

before(async () => {
  await buildDependenciesLocally()
})
