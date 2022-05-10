import { AnyClass } from '@boostercloud/framework-types'
import { ClassMetadata } from 'metadata-booster'
import 'reflect-metadata'

export function getClassMetadata(classType: AnyClass): ClassMetadata {
  const meta: ClassMetadata = Reflect.getMetadata('booster:typeinfo', classType)
  if (!meta) {
    throw Error(`Couldn't get proper metadata information of ${classType.name}`)
  }
  return meta
}

/**
 * Get the argument names from a given function.
 *
 * This implementation is a TypeScript adaptation of a JavaScript implementation
 * borrowed from the promisify-node code and can be found in the following link:
 * https://github.com/nodegit/promisify-node/blob/02fc47cfc00146a533193bc4740e2e3e3be81c6f/utils/args.js
 *
 * @param {Function} func - The function to parse.
 * @returns {Array} arg - List of arguments in the function.
 */
// TODO: Consider extending `metadata-booster` to yield constructor argument types
// eslint-disable-next-line @typescript-eslint/ban-types
export function getFunctionArguments(func: Function): Array<string> {
  // First match everything inside the function argument parens.
  const args = func.toString().match(/([^(])*\(([^)]*)\)/)?.[2]

  if (!args) return []

  // Split the arguments string into an array comma delimited.
  return args
    .split(', ')
    .map(function (arg) {
      // Ensure no inline comments are parsed and trim the whitespace.
      return arg.replace(/\/\*.*\*\//, '').trim()
    })
    .filter(function (arg) {
      // Ensure no undefineds are added.
      return arg
    })
}
