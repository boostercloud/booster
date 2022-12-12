import { PackageManagerError, PackageManagerService } from '.'
import { dieMessage, gen, Layer, mapError, orDie, pipe, Ref } from '@boostercloud/framework-types/dist/effect'
import { makePackageManager, makeScopedRun } from './common'

// TODO: Look recursively up for a rush.json file and run ./common/scripts/install-run-rushx.js
export const makeRushPackageManager = gen(function* ($) {
  // Create a reference to store the current project directory
  const projectDirRef = yield* $(Ref.makeRef(''))

  // Create a function to run a script in the project directory
  const runRush = yield* $(makeScopedRun('rush', projectDirRef))
  const runRushX = yield* $(makeScopedRun('rushx', projectDirRef))

  const commonService = yield* $(makePackageManager('rush'))

  const service: PackageManagerService = {
    ...commonService,
    runScript: (scriptName: string, args: ReadonlyArray<string>) =>
      pipe(
        runRushX(scriptName, args),
        mapError((error) => new PackageManagerError('ScriptExecutionError', error))
      ),
    installProductionDependencies: () =>
      dieMessage('Rush is a monorepo manager, so it does not support installing production dependencies'),
    installAllDependencies: () =>
      pipe(
        runRush('update', []),
        mapError((error) => new PackageManagerError('DependencyInstallationFailed', error))
      ),
  }
  return service
})

export const RushPackageManager = Layer.fromEffect(PackageManagerService)(orDie(makeRushPackageManager))
