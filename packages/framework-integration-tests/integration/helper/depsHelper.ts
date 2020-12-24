import { exec } from 'child-process-promise'
import * as path from 'path'
import { runCommand } from './runCommand'

function deBoosterize(packageName: string, projectPath: string): string {
  const folderName = packageName.replace('@boostercloud/', '')
  return path.relative(projectPath, path.join('..', folderName))
}

export async function symLinkBoosterDependencies(projectPath: string): Promise<void> {
  const packageJSON = require(path.relative(__dirname, path.join(projectPath, 'package.json')))
  // To compile the project with the current version we need to replace all Booster dependencies
  // by the versions under development.
  for (const packageName in packageJSON.dependencies) {
    if (/@boostercloud/.test(packageName)) {
      await exec(`npx yarn add file:${deBoosterize(packageName, projectPath)}`, { cwd: projectPath })
    }
  }

  for (const packageName in packageJSON.devDependencies) {
    if (/@boostercloud/.test(packageName)) {
      await exec(`npx yarn add file:${deBoosterize(packageName, projectPath)}`, {
        cwd: projectPath,
      })
    }
  }
}

export async function forceLernaRebuild(): Promise<void> {
  await exec('lerna clean --yes && lerna bootstrap && lerna run clean && lerna run compile')
}

export async function installBoosterPackage(packageName: string): Promise<void> {
  await runCommand(path.join('..', packageName), 'npx yarn global add file:$PWD')
}
