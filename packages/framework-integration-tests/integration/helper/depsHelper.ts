import * as path from 'path'
import * as fs from 'fs'
import { runCommand } from './runCommand'

export async function overrideWithBoosterLocalDependencies(projectPath: string): Promise<void> {
  const projectRelativePath = path.relative(__dirname, projectPath)
  const packageJSON = require(path.join(projectRelativePath, 'package.json'))
  // To compile the project with the current version we need to replace all Booster dependencies
  // by the versions under development.
  await overrideBoosterPackages(packageJSON, 'dependencies')
  await overrideBoosterPackages(packageJSON, 'devDependencies')
  fs.writeFileSync(path.join(projectPath, 'package.json'), JSON.stringify(packageJSON, undefined, 2))
}

async function overrideBoosterPackages(
  packageJSON: { dependencies: Record<string, string>; devDependencies: Record<string, string> },
  dependenciesEntry: 'dependencies' | 'devDependencies'
): Promise<void> {
  for (const packageName in packageJSON[dependenciesEntry]) {
    if (/@boostercloud/.test(packageName)) {
      const dependencyName = packageName.replace('@boostercloud/', '')

      const dotBooster = '.booster'
      if (!fs.existsSync(dotBooster)) {
        fs.mkdirSync(dotBooster)
      }

      const execution = await runCommand(dotBooster, `npm pack ${path.join('..', '..', dependencyName)}`)
      // eslint-disable-next-line @typescript-eslint/no-non-null-assertion
      const packedDependencyFileName = execution.stdout
        ?.trim()
        ?.split('\n')
        ?.pop()!
      const dotBoosterAbsolutePath = path.resolve(dotBooster)
      // Now override the dependency with the path to the packed dependency
      packageJSON[dependenciesEntry][packageName] = `file:${path.join(
        dotBoosterAbsolutePath,
        packedDependencyFileName
      )}`
    }
  }
}
