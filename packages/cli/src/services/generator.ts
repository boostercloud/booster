import * as path from 'path'
import { outputFile, readFileSync } from 'fs-extra'
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
import type { TemplateType } from './stub-publisher'

export async function generate<TInfo>(target: Target<TInfo>): Promise<void> {
  await checkResourceExists(target.name, target.placementDir, target.extension)
  checkResourceNameIsValid(target.name)
  const rendered = Mustache.render(target.template, { ...target.info })
  const renderPath = filePath<TInfo>(target)
  await outputFile(renderPath, rendered)
}

export function template(name: TemplateType): string {
  const fileName = `${name}.stub`
  const stubFile = resourceStubFilePath(fileName)

  if (checkStubsFolderExists() && checkResourceStubFileExists(stubFile)) {
    return readFileSync(stubFile).toString()
  }

  return readFileSync(resourceTemplateFilePath(fileName)).toString()
}

export function filePath<TInfo>(target: FileDir<TInfo>): string {
  const fileName = classNameToFileName(target.name)
  return path.join(process.cwd(), target.placementDir, `${fileName}${target.extension}`)
}

export function prepareFields(fields: string[] = []): string[] {
  return fields
    .flatMap((field: string) => field.split(' '))
    .filter((field: string) => field)
    .sort((aField: string, bField: string) => {
      if (aField.includes('?') > bField.includes('?')) return 1
      if (aField.includes('?') < bField.includes('?')) return -1
      return 0
    })
}

/**
 * Split path string to get resource folder name
 *
 * @param resourcePath path string
 */
export function getResourceType(resourcePath: string): string {
  return path.parse(resourcePath).name
}
