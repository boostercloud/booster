import * as fs from 'fs'

export const readFileContent = async (filePath: string): Promise<string> => fs.readFileSync(filePath, 'utf-8')

export const writeFileContent = async (filePath: string, data: any): Promise<void> => fs.writeFileSync(filePath, data)

export const removeFile = fs.unlinkSync
