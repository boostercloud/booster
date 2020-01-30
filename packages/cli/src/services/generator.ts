import * as path from 'path'
import * as fs from 'fs-extra'
import * as Mustache from 'mustache'
import { Target } from './generator/target'

export async function generate<TInfo>(target: Target<TInfo>): Promise<void> {
  const rendered = Mustache.render(target.template, target.info)
  const renderPath = path.join(process.cwd(), target.placementDir, `${target.name}${target.extension}`)
  await fs.outputFile(renderPath, rendered)
}

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
    const tsConfigJsonContents = require(path.join(currentPath, 'tsconfig.json'))
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
