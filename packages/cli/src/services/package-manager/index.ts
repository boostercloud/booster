import { Effect, tag } from '@boostercloud/framework-types/dist/effect'

export type PackageManagerError = InstallDependenciesError | RunScriptError

export class InstallDependenciesError {
  readonly _tag = 'InstallDependenciesError'
  constructor(readonly error: Error) {}
}

export class RunScriptError {
  readonly _tag = 'RunScriptError'
  constructor(readonly error: Error) {}
}

export interface PackageManagerService {
  readonly setProjectRoot: (projectRoot: string) => Effect<unknown, never, void>
  readonly installProductionDependencies: () => Effect<unknown, InstallDependenciesError, void>
  readonly installAllDependencies: () => Effect<unknown, InstallDependenciesError, void>
  readonly runScript: (scriptName: string, args: ReadonlyArray<string>) => Effect<unknown, RunScriptError, string>
  readonly build: (args: ReadonlyArray<string>) => Effect<unknown, RunScriptError, string>
}

export const PackageManagerService = tag<PackageManagerService>()
