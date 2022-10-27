import { gen, Ref } from '@boostercloud/framework-types/src/effect'
import { PackageManagerService } from '.'
import { guardError } from '../../common/errors'
import { ProcessService } from '../process'

/**
 * Gets the project root directory from the reference.
 * If the reference is an empty string, it will set it
 * to the current working directory and return it.
 */
const ensureProjectDir = (processService: ProcessService, projectDirRef: Ref.Ref<string>) =>
  gen(function* ($) {
    const { cwd } = processService
    const pwd = yield* $(cwd())
    const projectDir = yield* $(Ref.updateAndGet_(projectDirRef, (dir) => dir || pwd))
    return projectDir
  })

/**
 * Returns a function that executes a package manager command in the project directory.
 */
export const makeScopedRun = (command: string, projectDirRef: Ref.Ref<string>) =>
  gen(function* ($) {
    const processService = yield* $(ProcessService)
    const { exec } = processService
    return (scriptName: string, args: ReadonlyArray<string>) =>
      gen(function* ($) {
        const projectDir = yield* $(ensureProjectDir(processService, projectDirRef))
        return yield* $(exec(`${command} ${scriptName} ${args.join(' ')}`, projectDir))
      })
  })

export const makePackageManager = (packageManagerCommand: string) =>
  gen(function* ($) {
    // Create a reference to store the current project directory
    const projectDirRef = yield* $(Ref.makeRef(''))

    // Create a function to run a script in the project directory
    const run = yield* $(makeScopedRun(packageManagerCommand, projectDirRef))

    const service: PackageManagerService = {
      setProjectRoot: (projectDir: string) => Ref.set_(projectDirRef, projectDir),
      runScript: (scriptName: string, args: ReadonlyArray<string>) => run('run', [scriptName, ...args]),
      installProductionDependencies: () =>
        guardError('Could not install production dependencies')(
          run('install', ['--production', '--no-bin-links', '--no-optional'])
        ),
      installAllDependencies: () => guardError('Could not install dependencies')(run('install', [])),
    }
    return service
  })
