import * as path from 'path'
import { runCommand } from '../../helper/runCommand'

// Path to the CLI binary compiled by lerna
const cliBinaryPath = path.join('..', '..', 'cli', 'bin', 'run')

export async function deploy(path: string, environmentName = 'production'): Promise<void> {
  const runInProject = runCommand.bind(null, path)

  // Install dependencies
  await runInProject('yarn install')

  // Clean & compile the project
  await runInProject('yarn clean && yarn compile')

  // Invoke the "boost deploy" command using the cli.
  await runInProject(`${cliBinaryPath} deploy -e ${environmentName}`)
}

export async function nuke(path: string, environmentName = 'production'): Promise<void> {
  const runInProject = runCommand.bind(null, path)

  // Install dependencies
  await runInProject('yarn install')

  // Clean & compile the project
  await runInProject('yarn clean && yarn compile')

  // Nuke works in the cloud exclusively, no need for preparation
  await runInProject(`${cliBinaryPath} nuke -e ${environmentName} --force`)
}
