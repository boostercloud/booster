import * as path from 'path'
import * as fs from 'fs-extra'
import * as Mustache from 'mustache'
import { Target, FileDir } from './generator/target'
import { classNameToFileName, checkResourceNameIsValid } from '../common/filenames'
import { checkResourceExists } from './project-checker'
import {
  checkResourceStubFileExists,
  resourceStubFilePath,
  checkStubsFolderExists,
  resourceTemplateFilePath,
} from './stub-publisher'

export async function generate<TInfo>(target: Target<TInfo>): Promise<void> {
  await checkResourceExists(target.name, target.placementDir, target.extension)
  checkResourceNameIsValid(target.name)
  const rendered = Mustache.render(target.template, { ...target.info })
  const renderPath = filePath<TInfo>(target)
  await fs.outputFile(renderPath, rendered)
}

export async function template(name: string): Promise<string> {
  const stubFileName = resourceStubFilePath(`${name}.ts`)

  if (checkStubsFolderExists() && checkResourceStubFileExists(stubFileName)) {
    return await import(stubFileName).then(({ template }) => template)
  }

  return await import(resourceTemplateFilePath(name)).then(({ template }) => template)
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
