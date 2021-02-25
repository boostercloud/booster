import * as fs from 'fs-extra'
import * as path from 'path'

export async function updatePackageJsonDependencyVersions(version: string, projectPath: string): Promise<void> {
    const projectAbsolutePath = path.resolve(projectPath)
    const packageJsonPath = path.join(projectAbsolutePath, 'package.json')
    try {
        const packageJsonContents = require(packageJsonPath)
        for (let dependency in packageJsonContents.dependencies) {
          let depVersion = packageJsonContents.dependencies[dependency]
          if (dependency.startsWith('@boostercloud') && depVersion !== '*') {
            packageJsonContents.dependencies[dependency] = `^${version}`
          }
        }
        for (let dependency in packageJsonContents.devDependencies) {
          let depVersion = packageJsonContents.devDependencies[dependency]
          if (dependency.startsWith('@boostercloud') && depVersion !== '*') {
            packageJsonContents.devDependencies[dependency] = `^${version}`
          }
        }
        fs.outputFile(packageJsonPath, JSON.stringify(packageJsonContents,null,2))
    } catch (e) {
      throw new Error(
        `There was an error when recognizing the application package.json file. Make sure you are in the root path of a Booster project:\n${e.message}`
      )
    }
}
  