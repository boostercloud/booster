import * as path from 'path'
import * as fs from 'fs-extra'
import * as Mustache from 'mustache'
import { Target, FileDir } from './generator/target'

export async function generate<TInfo>(target: Target<TInfo>): Promise<void> {
  const rendered = Mustache.render(target.template, target.info)
  const renderPath = filePath<TInfo>(target)
  await fs.outputFile(renderPath, rendered)
}

export function filePath<TInfo>(target: FileDir<TInfo>): string {
  return path.join(process.cwd(), target.placementDir, `${target.name}${target.extension}`)
}
