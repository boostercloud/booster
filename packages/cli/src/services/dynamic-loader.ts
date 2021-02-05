/* eslint-disable @typescript-eslint/no-explicit-any */
export async function dynamicLoad(module: string): Promise<any> {
  // We wrap the call to import here to ease testing dynamic module loading
  // The `import` expression returns a promise that will be rejected when the module is not found
  return import(module)
}
