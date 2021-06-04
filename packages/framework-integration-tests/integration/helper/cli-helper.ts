import * as path from 'path'
import { runCommand, runCommandBackground } from './run-command'
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

export function start(environmentName = 'local', path: string): ChildProcess {
  return runCommand(path, `../${cliBinaryPath} start -e ${environmentName}`).childProcess
}

export function startInBackground(environmentName = 'local', path: string): ChildProcess {
  return runCommandBackground(path, `../${cliBinaryPath} start -e ${environmentName}`).childProcess
}