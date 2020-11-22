import * as path from 'path'
import { ChildProcess } from 'child_process'
import { exec, ChildProcessPromise, PromiseResult } from 'child-process-promise'
// const fs = require('fs')

/*
 * The example application in the `framework-integration-tests` package is part of the package source.
 * We do this to have this example code compiled every time we run `lerna run compile`, which is useful
 * to quickly detect API breaking changes.
 *
 * This file contains a script to set the project in a state that can be deployed to AWS, removing
 * all the lerna stuff and compiling the packages for production to make them smaller.
 */

const integrationTestsPackageRoot = path.dirname(__dirname)
const cliBinaryPath = path.join('..', 'cli', 'bin', 'run')

function run(command: string, path?: string): ChildProcessPromise<PromiseResult<string>> {
  const subprocess = exec(command, {
    cwd: path ?? integrationTestsPackageRoot, // Commands are run in the integration tests package root
  })

  if (subprocess.childProcess && process) {
    // Redirect process chatter to make it look like it's doing something
    subprocess.childProcess?.stdout?.pipe(process.stdout)
    subprocess.childProcess?.stderr?.pipe(process.stderr)
  }

  return subprocess
}

async function setEnv(): Promise<void> {
  if (process.env.BOOSTER_APP_SUFFIX == undefined) {
    // If the user doesn't set an app name suffix, use the current git commit hash
    // to build a unique suffix for the application name in AWS to avoid collisions
    // between tests from different branches.
    const { stdout } = await exec('git rev-parse HEAD')
    process.env['BOOSTER_APP_SUFFIX'] = stdout.trim().substring(0, 6)
  }
}

export async function deploy(environmentName = 'production'): Promise<void> {
  await setEnv()

  process.chdir(integrationTestsPackageRoot)

  // First, we ensure that all the dependencies are installed (node_modules is placed at the project root)
  // await run('lerna clean --yes && lerna bootstrap')

  // We are about to install the dependencies for production changing the location of the node_modules, so we first
  // rename the `node_modules` from the project root.
  // fs.renameSync('../../node_modules', '../../node_modules_dev')

  // Install the dependencies in production mode (inside the example application directory)
  await run('npm install --production --no-bin-links')

  // Now we undo the name change of the root node_modules. This is needed to compile the project, as:
  // * All the other packages don't see the node_modules inside the example app
  // * We need the dev dependencies that were not installed in the previous command
  // fs.renameSync('../../node_modules_dev', '../../node_modules')

  // Compile the project
  await run('lerna run clean')
  await run('lerna run compile')

  // Remove non-needed packages (lerna adds them as dependencies)
  // fs.unlinkSync('./node_modules/@boostercloud/framework-integration-tests')
  // fs.unlinkSync('./node_modules/@boostercloud/cli')

  // Finally invoke the "boost deploy" command using the compiled cli.
  await run(`${cliBinaryPath} deploy -e ${environmentName}`)
}

export async function nuke(environmentName = 'production'): Promise<void> {
  await setEnv()

  // It looks like when the project is not pre-compiled the nuke command fails
  await run('lerna run clean')
  await run('lerna run compile')

  // Nuke works in the cloud exclusively, no need for preparation
  await run(`${cliBinaryPath} nuke -e ${environmentName} --force`)
}

export function start(environmentName = 'local', path?: string): ChildProcess {
  return run(`../${cliBinaryPath} start -e ${environmentName}`, path).childProcess
}
