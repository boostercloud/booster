import * as path from 'path'
import { runCommand } from './run-command'
import { ChildProcess } from 'child_process'

// Path to the CLI binary compiled by lerna
const cliBinaryPath = path.join('..', '..', 'cli', 'bin', 'run')

export async function deploy(projectPath: string, environmentName = 'production'): Promise<void> {
  // Production dependencies are installed by the cliHelper command
  await runCommand(projectPath, `${cliBinaryPath} deploy -e ${environmentName}`)
}

export async function nuke(projectPath: string, environmentName = 'production'): Promise<void> {
  // Dependencies should be installed before running the nuke command
  await runCommand(projectPath, 'npm install')
  await runCommand(projectPath, `${cliBinaryPath} nuke -e ${environmentName} --force`)
}

<<<<<<< HEAD
export function start(path: string, environmentName = 'local'): ChildProcess {
=======
export function start(environmentName = 'local', path: string): ChildProcess {
>>>>>>> 0f61eaab (local provider start and stop methods for integration tests)
  return runCommand(path, `${cliBinaryPath} start -e ${environmentName}`).childProcess
}