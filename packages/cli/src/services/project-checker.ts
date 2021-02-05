import * as fs from 'fs-extra'
import * as path from 'path'
import { dynamicLoad } from './dynamic-loader'

function checkIndexFileIsBooster(indexFilePath: string): void {
  const contents = fs.readFileSync(indexFilePath)
  if (!contents.includes('Booster.start(')) {
    throw new Error(
      'The main application file does not start a Booster application. Verify you are in the right project'
    )
  }
}

export async function checkCurrentDirIsABoosterProject(): Promise<void> {
  return checkItIsABoosterProject(process.cwd())
}

export async function checkItIsABoosterProject(projectPath: string): Promise<void> {
  const projectAbsolutePath = path.resolve(projectPath)
  try {
    const tsConfigJsonContents = await dynamicLoad(path.join(projectAbsolutePath, 'tsconfig.json'))
    const indexFilePath = path.normalize(
      path.join(projectAbsolutePath, tsConfigJsonContents.compilerOptions.rootDir, 'index.ts')
    )
    checkIndexFileIsBooster(indexFilePath)
  } catch (e) {
    throw new Error(
      `There was an error when recognizing the application. Make sure you are in the root path of a Booster project:\n${e.message}`
    )
  }
}
