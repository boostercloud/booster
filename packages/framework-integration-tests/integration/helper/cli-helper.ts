import * as path from 'path'
import { ChildProcess } from 'child_process'
import { runCommand } from '@boostercloud/framework-common-helpers'

// Path to the CLI binary compiled by lerna
const cliBinaryPath = path.join('..', '..', 'cli', 'bin', 'run')

export async function deploy(projectPath: string, environmentName = 'production'): Promise<void> {
  // Production dependencies are installed by the cliHelper command
  await runCommand(projectPath, `${cliBinaryPath} deploy -e ${environmentName}`)
}

export async function nuke(projectPath: string, environmentName = 'production'): Promise<void> {
  // Dependencies should be installed before running the nuke command
  await runCommand(projectPath, 'npm install --production --no-bin-links --no-optional')
  await runCommand(projectPath, `${cliBinaryPath} nuke -e ${environmentName} --force`)
}

export function start(path: string, environmentName = 'local'): ChildProcess {
  return runCommand(path, `${cliBinaryPath} start -e ${environmentName}`).childProcess
}
