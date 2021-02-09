import * as fs from 'fs-extra'
import * as path from 'path'

export async function updatePackageJsonDependencyVersions(version: string, projectPath: string): Promise<void> {
    const projectAbsolutePath = path.resolve(projectPath)
    const packageJsonPath = path.join(projectAbsolutePath, 'package.json')
    try {
        const packageJsonContents = require(packageJsonPath)
        packageJsonContents.dependencies['@boostercloud/framework-core'] = `^${version}`
        if (packageJsonContents.dependencies['@boostercloud/framework-types'] !== undefined) {
            packageJsonContents.dependencies['@boostercloud/framework-types'] = `^${version}`
        }
        fs.outputFile(packageJsonPath, JSON.stringify(packageJsonContents,null,2), function writeJSON(err) {
            if (err) return console.log(err);            
        });
    } catch (e) {
      throw new Error(
        `There was an error when recognizing the application package.json file. Make sure you are in the root path of a Booster project:\n${e.message}`
      )
    }
}
  