import * as path from 'path'
import * as fs from 'fs-extra'
import * as Mustache from 'mustache'
import { Target, FileDir } from './generator/target'
import { classNameToFileName } from '../common/filenames'
import { checkResourceExists } from './project-checker'

export async function generate<TInfo>(target: Target<TInfo>): Promise<void> {
  await checkResourceExists(target.name, target.placementDir, target.extension)
  const rendered = Mustache.render(target.template, target.info)
  const renderPath = filePath<TInfo>(target)
  await fs.outputFile(renderPath, rendered)
}

export function filePath<TInfo>(target: FileDir<TInfo>): string {
  const fileName = classNameToFileName(target.name)
  return path.join(process.cwd(), target.placementDir, `${fileName}${target.extension}`)
}

/**
 * Split path string to get resource folder name
 *
 * @param resourcePath path string
 */
export function getResourceType(resourcePath: string): string {
  return path.parse(resourcePath).name
}
