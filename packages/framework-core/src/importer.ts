import * as fs from 'fs'
import * as path from 'path'

export class Importer {
  public static importUserProjectFiles(codeRootPath: string): void {
    Importer.getImportFiles(codeRootPath).forEach(Importer.importWithoutExtension)
  }

  private static importWithoutExtension(file: string): void {
    require(Importer.removeDevExtension(file))
  }

  private static getImportFiles(codeRootPath: string): Array<string> {
    return Importer.walkDir(codeRootPath)
      .filter(Importer.isJavaScriptFile)
      .filter(Importer.isNotIndexJs)
  }

  private static walkDir(dir: string): Array<string> {
    const files: Array<string> = []
    Importer.listDirectory(dir).forEach((file: string): void => {
      const fileName = path.join(dir, file)
      if (Importer.isDirectory(fileName)) {
        const filesInDirectory = Importer.walkDir(fileName)
        files.push(...filesInDirectory)
      } else {
        files.push(fileName)
      }
    })
    return files
  }

  private static isJavaScriptFile(file: string): boolean {
    return file.match(/.*\.js$/) != null
  }

  private static isNotIndexJs(file: string): boolean {
    return file.match(/index.js$/) == null
  }
  private static removeDevExtension(file: string): string {
    return path.join(file.replace(/(\.d)?(\.ts|\.js)/, ''))
  }

  private static listDirectory(dir: string): Array<string> {
    return fs.readdirSync(dir)
  }

  private static isDirectory(fileName: string): boolean {
    return fs.statSync(fileName).isDirectory()
  }
}
