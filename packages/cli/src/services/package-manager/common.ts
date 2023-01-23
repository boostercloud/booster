import { mapError, gen, pipe, Ref } from '@boostercloud/framework-types/dist/effect'
import { InstallDependenciesError, PackageManagerService, RunScriptError } from '.'
import { ProcessService } from '../process'
import { FileSystemService } from '../file-system'

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
 * Checks if a script exists in the package.json file
 */
const checkScriptExists = (processService: ProcessService, fileSystemService: FileSystemService, scriptName: string) =>
  gen(function* ($) {
    const { cwd } = processService
    const { readFileContents } = fileSystemService
    const pwd = yield* $(cwd())
    const packageJson = yield* $(readFileContents(`${pwd}/package.json`))
    const packageJsonContents = JSON.parse(packageJson)
    return packageJsonContents.scripts && packageJsonContents.scripts[scriptName]
  })

/**
 * Function that returns a function to run the build script in the project directory.
 */
const makeRunBuildScript = (command: string, projectDirRef: Ref.Ref<string>) =>
  gen(function* ($) {
    const run = yield* $(makeScopedRun(command, projectDirRef))
    const processService = yield* $(ProcessService)
    const fileSystemService = yield* $(FileSystemService)
    return (args: ReadonlyArray<string>) =>
      gen(function* ($) {
        const scriptExists = yield* $(checkScriptExists(processService, fileSystemService, 'compile'))
        const scriptName = scriptExists ? 'run compile' : 'build'
        return yield* $(run(scriptName, null, args))
      })
  })

/**
 * Returns a function that executes a package manager command in the project directory.
 */
export const makeScopedRun = (packageManagerCommand: string, projectDirRef: Ref.Ref<string>) =>
  gen(function* ($) {
    const processService = yield* $(ProcessService)
    return (scriptName: string, subscriptName: string | null, args: ReadonlyArray<string>) =>
      gen(function* ($) {
        const projectDir = yield* $(ensureProjectDir(processService, projectDirRef))
        return yield* $(
          processService.exec(
            `${packageManagerCommand} ${scriptName} ${subscriptName ? subscriptName + ' ' : ''}${args.join(
              ' '
            )}`.trim(),
            projectDir
          )
        )
      })
  })

export const makePackageManager = (packageManagerCommand: string) =>
  gen(function* ($) {
    // Create a reference to store the current project directory
    const projectDirRef = yield* $(Ref.makeRef(''))

    // Create a function to run a script in the project directory
    const run = yield* $(makeScopedRun(packageManagerCommand, projectDirRef))

    const runBuild = yield* $(makeRunBuildScript(packageManagerCommand, projectDirRef))

    const service: PackageManagerService = {
      setProjectRoot: (projectDir: string) => Ref.set_(projectDirRef, projectDir),
      runScript: (scriptName: string, args: ReadonlyArray<string>) =>
        pipe(
          run('run', scriptName, args),
          mapError((error) => new RunScriptError(error.error))
        ),
      build: (args: ReadonlyArray<string>) =>
        pipe(
          runBuild(args),
          mapError((error) => new RunScriptError(error.error))
        ),
      installProductionDependencies: () =>
        pipe(
          run('install', null, ['--production', '--no-bin-links', '--no-optional']),
          mapError((error) => new InstallDependenciesError(error.error))
        ),
      installAllDependencies: () =>
        pipe(
          run('install', null, []),
          mapError((error) => new InstallDependenciesError(error.error))
        ),
    }
    return service
  })
