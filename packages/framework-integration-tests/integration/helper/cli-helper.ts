import * as path from 'path'
import { runCommand } from '@boostercloud/framework-common-helpers'

// Path to the CLI binary compiled for this project
const cliBinaryPath = path.join(__dirname, '..', '..', 'node_modules', '.bin', 'boost')

export async function deploy(projectPath: string, environmentName = 'production'): Promise<void> {
  // Production dependencies are installed by the cliHelper command
  await runCommand(projectPath, `${cliBinaryPath} deploy --verbose -e ${environmentName}`)
}

export async function nuke(projectPath: string, environmentName = 'production'): Promise<void> {
  // Dependencies should be installed before running the nuke command
  await runCommand(projectPath, 'npm install --omit=dev --omit=optional --no-bin-links')
  await runCommand(projectPath, `${cliBinaryPath} nuke --verbose -e ${environmentName} --force`)
}

export async function start(path: string, environmentName = 'local'): Promise<void> {
  await runCommand(path, `${cliBinaryPath} start --verbose -e ${environmentName}`)
}
