import { exec } from 'child-process-promise'
import * as path from 'path'
import * as fs from 'fs'

export async function overrideWithBoosterLocalDependencies(projectPath: string): Promise<void> {
  const projectRelativePath = path.relative(__dirname, projectPath)
  const packageJSON = require(path.join(projectRelativePath, 'package.json'))

  overrideWithLocalDeps(packageJSON.dependencies)
  overrideWithLocalDeps(packageJSON.devDependencies)

  fs.writeFileSync(path.join(projectPath, 'package.json'), JSON.stringify(packageJSON, undefined, 2))
}

function overrideWithLocalDeps(dependencies: Record<string, string>): void {
  for (const packageName in dependencies) {
    if (/@boostercloud/.test(packageName)) {
      const sanitizedPackageName = packageName.replace('@', '').replace('/', '-')
      const sanitizedPackageVersion = dependencies[packageName]
        .replace('workspace:', '')
        .replace(/\*/g, '')
        .replace('^', '')
      const packedDependencyFileName = `${sanitizedPackageName}-${sanitizedPackageVersion}.tgz`

      const dotBooster = '.booster'
      if (!fs.existsSync(dotBooster)) {
        fs.mkdirSync(dotBooster)
      }

      const dotBoosterAbsolutePath = path.resolve(dotBooster)
      // Now override the packageJSON dependencies with the path to the packed dependency
      dependencies[packageName] = `file:${path.join(dotBoosterAbsolutePath, packedDependencyFileName)}`
    }
  }
}

export async function forceRepoRebuild(): Promise<void> {
  await exec('rush update && rush rebuild')
}
