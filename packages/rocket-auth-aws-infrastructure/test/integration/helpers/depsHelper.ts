import { exec } from 'child-process-promise'
import * as path from 'path'
import * as fs from 'fs'

export async function overrideWithBoosterLocalDependencies(projectPath: string): Promise<void> {
  const projectRelativePath = path.relative(__dirname, projectPath)
  const packageJSON = require(path.join(projectRelativePath, 'package.json'))
  // To compile the project with the current version we need to replace all Booster dependencies
  // by the versions under development.
  for (const packageName in packageJSON.devDependencies) {
    if (/@boostercloud\/rocket-auth-aws-infrastructure/.test(packageName)) {
      const dependencyName = packageName.replace('@boostercloud/rocket-auth-aws-infrastructure', '')

      const dotBooster = '.booster'
      if (!fs.existsSync(dotBooster)) {
        fs.mkdirSync(dotBooster)
      }

      const execution = await exec(`npm pack ${path.join('..', dependencyName)}`, { cwd: dotBooster })
      // eslint-disable-next-line @typescript-eslint/no-non-null-assertion
      const packedDependencyFileName = execution.stdout
        ?.trim()
        ?.split('\n')
        ?.pop()!
      const dotBoosterAbsolutePath = path.resolve(dotBooster)
      // Now override the packageJSON dependencies with the path to the packed dependency
      packageJSON.devDependencies[packageName] = `file:${path.join(dotBoosterAbsolutePath, packedDependencyFileName)}`
    }
  }
  fs.writeFileSync(path.join(projectPath, 'package.json'), JSON.stringify(packageJSON, undefined, 2))
}

export async function forceLernaRebuild(): Promise<void> {
  await exec('lerna clean --yes && lerna bootstrap && lerna run clean && lerna run compile')
}
