import * as path from 'path'

/* eslint-disable @typescript-eslint/no-explicit-any */
export async function dynamicLoadFile(filePath: string): Promise<any> {
  // Import is always relative to current file, so we need to "relativize" the path
  return dynamicLoadModule(path.relative(__dirname, filePath))
}

export async function dynamicLoadModule(packageName: string): Promise<any> {
  // The `import` expression returns a promise that will be rejected when the module is not found
  return import(packageName)
}
