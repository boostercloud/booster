import * as inflected from 'inflected'

export function classNameToFileName(name: string): string {
  return inflected.dasherize(inflected.underscore(name))
}

export function resourceNameToClassName(name: string): string {
  return inflected.classify(inflected.underscore(inflected.parameterize(inflected.tableize(name))))
}
