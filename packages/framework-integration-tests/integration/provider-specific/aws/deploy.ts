import * as path from 'path'
import { runCommand } from '../../helper/run-command'

// Path to the CLI binary compiled by lerna
const cliBinaryPath = path.join('..', '..', 'cli', 'bin', 'run')

export async function deploy(projectPath: string, environmentName = 'production'): Promise<void> {
  // Production dependencies are installed by the deploy command
  await runCommand(projectPath, `${cliBinaryPath} deploy -e ${environmentName}`)
}

export async function nuke(projectPath: string, environmentName = 'production'): Promise<void> {
  // Dependencies should be installed before running the nuke command
  await runCommand(projectPath, 'npm install')
  await runCommand(projectPath, `${cliBinaryPath} nuke -e ${environmentName} --force`)
}
