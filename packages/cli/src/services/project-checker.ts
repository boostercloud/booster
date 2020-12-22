import * as fs from 'fs-extra'
import * as path from 'path'
import { dynamicLoad } from './dynamic-loader'

function checkIndexFileIsBooster(indexFilePath: string): void {
  const contents = fs.readFileSync(indexFilePath)
  if (!contents.includes('Booster.start()')) {
    throw new Error(
      'The main application file does not start a Booster application. Verify you are in the right project'
    )
  }
}

export async function checkItIsABoosterProject(): Promise<void> {
  const currentPath = process.cwd()
  try {
    const tsConfigJsonContents = dynamicLoad(path.join(currentPath, 'tsconfig.json'))
    const indexFilePath = path.normalize(
      path.join(currentPath, tsConfigJsonContents.compilerOptions.rootDir, 'index.ts')
    )
    checkIndexFileIsBooster(indexFilePath)
  } catch (e) {
    throw new Error(
      `There was an error when recognizing the application. Make sure you are in the root path of a Booster project:\n${e.message}`
    )
  }
}
