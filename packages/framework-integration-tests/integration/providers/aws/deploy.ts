import * as path from 'path'
import { runCommand } from '../../helper/runCommand'

// Path to the CLI binary compiled by lerna
const cliBinaryPath = path.join('..', '..', 'cli', 'bin', 'run')

async function runInfrastructureCommand(projectPath: string, command: string): Promise<void> {
  const runInProject = runCommand.bind(null, projectPath)

  // Install project dependencies
  await runInProject('yarn install')

  // Clean & compile the project
  await runInProject('yarn clean && yarn compile')

  // Link the aws integration package in the general registry to allow
  // the CLI reach it after prunning devDependencies
  // await runCommand(path.join('..', '..', 'packages', 'framework-provider-aws-infrastructure'), 'npm link')

  // Invoke the command
  await runInProject(command)
}

export async function deploy(projectPath: string, environmentName = 'production'): Promise<void> {
  await runInfrastructureCommand(projectPath, `${cliBinaryPath} deploy -e ${environmentName}`)
}

export async function nuke(projectPath: string, environmentName = 'production'): Promise<void> {
  await runInfrastructureCommand(projectPath, `${cliBinaryPath} nuke -e ${environmentName} --force`)
}
