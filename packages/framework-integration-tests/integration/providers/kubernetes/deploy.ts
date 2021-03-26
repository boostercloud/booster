import * as path from 'path'
import { runCommand } from '../../helper/run-command'

// Path to the CLI binary compiled by lerna
const cliBinaryPath = path.join('..', '..', 'cli', 'bin', 'run')

export async function deploy(projectPath: string, environmentName = 'kubernetes'): Promise<void> {
  // Production dependencies are installed by the deploy command
  await runCommand(projectPath, `${cliBinaryPath} deploy -e ${environmentName}`)
}
