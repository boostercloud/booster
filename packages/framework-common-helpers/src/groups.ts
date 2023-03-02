/**
 * Removes duplicates from an array.
 */
export function unique<T>(items: Array<T>): Array<T> {
  return Array.from(new Set(items))
}
