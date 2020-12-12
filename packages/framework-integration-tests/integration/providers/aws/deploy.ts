import * as path from 'path'
import { runCommand } from '../../helper/runCommand'

// Path to the CLI binary compiled by lerna
const cliBinaryPath = path.join('..', '..', 'cli', 'bin', 'run')

async function runInfrastructureCommand(command: string): Promise<void> {
  const runInProject = runCommand.bind(null, path)

  // Install dependencies
  await runInProject('yarn install')

  // Clean & compile the project
  await runInProject('yarn clean && yarn compile')

  // Install the aws integration package in the general registry to make sure that
  // the CLI can reach it after prunning devDependencies
  await runCommand('.', 'npm install -g packages/framework-provider-aws-infrastructure')

  // Invoke the command
  await runInProject(command)
}

export async function deploy(path: string, environmentName = 'production'): Promise<void> {
  await runInfrastructureCommand(`${cliBinaryPath} deploy -e ${environmentName}`)
}

export async function nuke(path: string, environmentName = 'production'): Promise<void> {
  await runInfrastructureCommand(`${cliBinaryPath} nuke -e ${environmentName} --force`)
}
