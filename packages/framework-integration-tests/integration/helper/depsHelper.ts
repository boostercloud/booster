import { exec } from 'child-process-promise'
import * as path from 'path'
import * as fs from 'fs'

export async function overrideWithBoosterLocalDependencies(projectPath: string): Promise<void> {
  const projectRelativePath = path.relative(__dirname, projectPath)
  const packageJSON = require(path.join(projectRelativePath, 'package.json'))
  // To compile the project with the current version we need to replace all Booster dependencies
  // by the versions under development.
  for (const packageName in packageJSON.dependencies) {
    if (/@boostercloud/.test(packageName)) {
      const dependencyName = packageName.replace('@boostercloud/', '')
      // Pack all booster dependencies in a temporal directory
      await exec(`cd .booster && npm pack ${path.relative(projectPath, path.join('..', dependencyName))}`)
      // Now override the pakcageJSON dependencies with the path to the packed dependency
      packageJSON.dependencies[
        packageName
      ] = `file:../.booster/boostercloud-${dependencyName}-${packageJSON.version}.tgz`
    }
  }
  fs.writeFileSync(path.join(projectPath, 'package.json'), JSON.stringify(packageJSON, undefined, 2))
}

export async function forceLernaRebuild(): Promise<void> {
  await exec('lerna clean --yes && lerna bootstrap && lerna run clean && lerna run compile')
}
