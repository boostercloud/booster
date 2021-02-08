import { runCommand } from './runCommand'

export async function deploy(projectPath: string, environmentName = 'production'): Promise<void> {
  // Production dependencies are installed by the deploy command
  await runCommand(projectPath, 'npm install')
  await runCommand(projectPath, `boost deploy -e ${environmentName}`)
}
