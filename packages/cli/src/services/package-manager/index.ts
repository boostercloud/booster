import { Effect, tag } from '@boostercloud/framework-types/dist/effect'

type Reason = 'ProjectRootNotSet' | 'DependencyInstallationFailed' | 'ScriptExecutionError'

export class PackageManagerError {
  readonly _tag = 'PackageManagerError'
  public constructor(readonly reason: Reason, readonly error: unknown) {}
}

export interface PackageManagerService {
  readonly setProjectRoot: (projectRoot: string) => Effect<unknown, PackageManagerError, void>
  readonly installProductionDependencies: () => Effect<unknown, PackageManagerError, void>
  readonly installAllDependencies: () => Effect<unknown, PackageManagerError, void>
  readonly runScript: (scriptName: string, args: ReadonlyArray<string>) => Effect<unknown, PackageManagerError, string>
}

export const PackageManagerService = tag<PackageManagerService>()
