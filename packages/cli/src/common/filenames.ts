import * as inflected from 'inflected'

export function classNameToFileName(name: string): string {
  return inflected.dasherize(inflected.underscore(name))
}
