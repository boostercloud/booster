import * as fs from 'fs'

export const readFileContent = (filePath: string): string => fs.readFileSync(filePath, 'utf-8')

export const writeFileContent = (filePath: string, data: any): void => fs.writeFileSync(filePath, data)

export const removeFile = fs.unlinkSync

export const removeFiles = (filePaths: Array<string>): Array<Promise<void>> => {
  return filePaths.map((file: string) => {
    return new Promise((resolve) => {
      fs.unlinkSync(file)
      resolve()
    })
  })
}
