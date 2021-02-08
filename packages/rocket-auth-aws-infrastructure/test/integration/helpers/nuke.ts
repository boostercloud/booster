import { runCommand } from './runCommand'

export async function nuke(projectPath: string, environmentName = 'production'): Promise<void> {
  // Dependencies should be installed before running the nuke command
  await runCommand(projectPath, 'npm install')
  await runCommand(projectPath, `boost nuke -e ${environmentName} --force`)
}
