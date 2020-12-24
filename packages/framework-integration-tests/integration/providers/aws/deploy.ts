import * as path from 'path'
import { installBoosterPackage } from '../../helper/depsHelper'
import { runCommand } from '../../helper/runCommand'

// Path to the CLI binary compiled by lerna
const cliBinaryPath = path.join('..', '..', 'cli', 'bin', 'run')

export async function deploy(projectPath: string, environmentName = 'production'): Promise<void> {
  await installBoosterPackage('framework-provider-aws-infrastructure')
  // Production dependencies are installed by the deploy command
  await runCommand(projectPath, `${cliBinaryPath} deploy -e ${environmentName}`)
}

export async function nuke(projectPath: string, environmentName = 'production'): Promise<void> {
  await installBoosterPackage('framework-provider-aws-infrastructure')
  // Dependencies should be installed before running the nuke command
  await runCommand(projectPath, 'npx yarn install')
  await runCommand(projectPath, `${cliBinaryPath} nuke -e ${environmentName} --force`)
}
