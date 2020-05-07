import * as fs from 'fs'

export const readFileContent = async (filePath: string): Promise<string> => {
  return fs.readFileSync(filePath, 'utf-8')
}

export const removeFile = async (filePath: string): Promise<void> => {
  return fs.unlinkSync(filePath)
}