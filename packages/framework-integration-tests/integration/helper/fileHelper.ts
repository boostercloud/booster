import * as fs from 'fs'

export const readFileContent = async (filePath: string): Promise<string> => {
  return fs.readFileSync(filePath, 'utf-8')
}

export const writeFileContent = async (filePath: string, data: any): Promise<void> => {
  return fs.writeFileSync(filePath, data)
}

export const removeFile = fs.unlinkSync
