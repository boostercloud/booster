import type { Dirent } from 'fs-extra'
import { existsSync, mkdirSync, readdirSync, readFileSync, writeFileSync } from 'fs-extra'
import { wrapExecError } from '../common/errors'
import { join } from 'path'

export const stubFolderPath: string = join(process.cwd(), 'stubs')

export const stubsFolderExists = (): boolean => existsSync(stubFolderPath)

export const createStubsFolder = (): void => mkdirSync(stubFolderPath)

export async function publishStubFiles(): Promise<void> {
  const resourceTemplatesPath = join(__dirname, '..', 'templates')

  const files: Dirent[] = readdirSync(resourceTemplatesPath, { withFileTypes: true })
  const templateFilesMap = files
    .filter((file: Dirent) => file.isFile() && file.name !== 'index.ts')
    .reduce((files: Record<string, string>, file: Dirent) => {
      const resourceTemplatePath: string = join(resourceTemplatesPath, file.name)

      files[resourceTemplatePath] = join(stubFolderPath, file.name)

      return files
    }, {})

  try {
    for (const [from, to] of Object.entries(templateFilesMap)) {
      copyStubFile(from, to)
    }
  } catch (e) {
    throw wrapExecError(e, 'Error when publishing stubs')
  }
}

const copyStubFile = (from: string, to: string): void => writeFileSync(to, readFileSync(from))
