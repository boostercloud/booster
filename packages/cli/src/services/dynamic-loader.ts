/* eslint-disable @typescript-eslint/no-explicit-any */
export function dynamicLoad(module: string): any {
  return require(module)
}
