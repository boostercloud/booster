import * as path from 'path'
import { runCommand } from '../../helper/runCommand'

// Path to the CLI binary compiled by lerna
const cliBinaryPath = path.join('..', '..', 'cli', 'bin', 'run')

export async function deploy(projectPath: string, environmentName = 'production'): Promise<void> {
  await runCommand(projectPath, `${cliBinaryPath} deploy -e ${environmentName}`)
}

export async function nuke(projectPath: string, environmentName = 'production'): Promise<void> {
  await runCommand(projectPath, `${cliBinaryPath} nuke -e ${environmentName} --force`)
}
