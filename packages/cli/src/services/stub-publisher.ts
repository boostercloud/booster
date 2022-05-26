import type { Dirent } from 'fs-extra'
import { existsSync, mkdirSync, readdirSync, readFileSync, writeFileSync } from 'fs-extra'
import { join } from 'path'
import { wrapExecError } from '../common/errors'

export type TemplateType =
  | 'command'
  | 'entity'
  | 'event'
  | 'event-handler'
  | 'read-model'
  | 'scheduled-command'
  | 'type'

export const resourceTemplatesPath: string = join(__dirname, '..', 'templates')

export const resourceStubFilePath = (fileName: string): string => join(process.cwd(), 'stubs', fileName)

export const resourceTemplateFilePath = (fileName: string): string => join(resourceTemplatesPath, fileName)

export const checkStubsFolderExists = (): boolean => existsSync(join(process.cwd(), 'stubs'))

export const checkResourceStubFileExists = (filePath: string): boolean => existsSync(filePath)

export const createStubsFolder = (): void => mkdirSync(join(process.cwd(), 'stubs'))

export const createTemplateFileMap = (files: Dirent[]): Record<string, string> =>
  files
    .filter((file: Dirent) => file.isFile() && file.name.includes('.stub'))
    .reduce((files: Record<string, string>, file: Dirent) => {
      const resourceTemplatePath: string = join(resourceTemplatesPath, file.name)

      files[resourceTemplatePath] = join(process.cwd(), 'stubs', file.name)

      return files
    }, {})

export async function publishStubFiles(): Promise<void> {
  const files: Dirent[] = readdirSync(resourceTemplatesPath, { withFileTypes: true })
  const templateFilesMap = createTemplateFileMap(files)

  try {
    for (const [from, to] of Object.entries(templateFilesMap)) {
      copyStubFile(from, to)
    }
  } catch (e) {
    throw wrapExecError(e, 'Error when publishing stubs')
  }
}

const copyStubFile = (from: string, to: string): void => writeFileSync(to, readFileSync(from))
