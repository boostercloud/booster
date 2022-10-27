import { Effect, tag } from '@boostercloud/framework-types/src/effect'

export class PackageManagerError {
  readonly _tag = 'PackageManagerError'
  readonly error: Error
  public constructor(readonly reason: unknown) {
    this.error = reason instanceof Error ? reason : new Error(JSON.stringify(reason))
  }
}

export interface PackageManagerService {
  readonly setProjectRoot: (projectRoot: string) => Effect<unknown, unknown, void>
  readonly installProductionDependencies: () => Effect<unknown, unknown, void>
  readonly installAllDependencies: () => Effect<unknown, unknown, void>
  readonly runScript: (scriptName: string, args: ReadonlyArray<string>) => Effect<unknown, unknown, string>
}

export const PackageManagerService = tag<PackageManagerService>()
