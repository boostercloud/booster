import util = require('util')
const exec = util.promisify(require('child_process').exec)
import path = require('path')
/*
 * The example application in the `framework-integration-tests` package is part of the package source.
 * We do this to have this example code compiled every time we run `lerna run compile`, which is useful
 * to quickly detect API breaking changes.
 *
 * This file contains a script to set the project in a state that can be deployed to AWS, removing
 * all the lerna stuff and compiling the packages for production to make them smaller.
 */

const integrationTestsPackageRoot = path.dirname(__dirname)

function run(command: string): Promise<void> {
  const subprocess = exec(command, {
    cwd: integrationTestsPackageRoot, // Commands are run in the integration tests package root
  })

  // Redirect process chatter to make it look like it's doing something
  subprocess.child.stdout.pipe(process.stdout)
  subprocess.child.stderr.pipe(process.stderr)

  return subprocess
}

export async function deploy(): Promise<void> {
  // First, we ensure that the project is bootstrapped, and all the dependencies are installed (node_modules is placed at the project root)
  await run('lerna bootstrap && lerna clean --yes')

  // We are about to install the dependencies for production changing the location of the node_modules, so we first
  // rename the `node_modules` from the project root.
  await run('mv ../../node_modules ../../node_modules_dev')

  // Install the dependencies in production mode (inside the example application directory)
  await run('yarn install --production --no-bin-links --modules-folder ./node_modules')

  // Now we undo the name change of the root node_modules. This is needed to compile the project, as:
  // - All the other packages don't see the node_modules inside the example app
  // - We need the dev dependencies that were not installed in the previous command
  await run('mv ../../node_modules_dev ../../node_modules')

  // Compile the project
  await run('lerna run clean && lerna run compile')

  // Remove non-needed packages (lerna adds them as dependencies)
  await run('rm node_modules/@boostercloud/framework-integration-tests')
  await run('rm node_modules/@boostercloud/cli')

  // Finally invoke the "boost deploy" command using the compiled cli.
  await run('../cli/bin/run deploy -e production')
}

export async function nuke(): Promise<void> {
  // Nuke works in the cloud exclusively, no need for preparation
  await run('../cli/bin/run nuke production --force')
}
