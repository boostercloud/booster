import { PackageManagerService } from '.'
import { dieMessage, gen, Layer, orDie, Ref } from '@boostercloud/framework-types/src/effect'
import { makeScopedRun } from './common'
import { guardError } from '../../common/errors'

// TODO: Look recursively up for a rush.json file and run ./common/scripts/install-run-rushx.js
export const makeRushPackageManager = gen(function* ($) {
  // Create a reference to store the current project directory
  const projectDirRef = yield* $(Ref.makeRef(''))

  // Create a function to run a script in the project directory
  const runRush = yield* $(makeScopedRun('rush', projectDirRef))
  const runRushX = yield* $(makeScopedRun('rushx', projectDirRef))

  const service: PackageManagerService = {
    setProjectRoot: (projectDir: string) => Ref.set(projectDir)(projectDirRef),
    runScript: (scriptName: string, args: ReadonlyArray<string>) => runRushX(scriptName, args),
    installProductionDependencies: () =>
      guardError('Could not install production dependencies')(
        dieMessage('Rush is a monorepo manager, so it does not support installing production dependencies')
      ),
    installAllDependencies: () => guardError('Could not install dependencies')(runRush('update', [])),
  }
  return service
})

export const RushPackageManager = Layer.fromEffect(PackageManagerService)(orDie(makeRushPackageManager))
