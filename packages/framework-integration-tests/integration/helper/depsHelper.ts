import { exec } from 'child-process-promise'
import * as path from 'path'

function deBoosterize(packageName: string): string {
  return packageName.replace('@boostercloud/', '')
}

export async function symLinkBoosterDependencies(projectPath: string): Promise<void> {
  const packageJSON = require(path.join('..', '..', projectPath, 'package.json'))
  // To compile the project with the current version we need to replace all Booster dependencies
  // by the versions under development.
  for (const packageName in packageJSON.dependencies) {
    if (/@boostercloud/.test(packageName)) {
      await exec('npm link', { cwd: path.join('..', deBoosterize(packageName)) })
      await exec(`npm link ${packageName}`, { cwd: projectPath })
    }
  }

  for (const packageName in packageJSON.devDependencies) {
    if (/@boostercloud/.test(packageName)) {
      await exec('npm link', { cwd: path.join('..', deBoosterize(packageName)) })
      await exec(`npm link ${packageName}`, { cwd: projectPath })
    }
  }
}

export async function forceLernaRebuild(): Promise<void> {
  await exec('lerna run clean && lerna run compile')
}
