import * as inflected from 'inflected'
import { logger } from '../services/logger'
import Brand from './brand'

export function classNameToFileName(name: string): string {
  return inflected.dasherize(inflected.underscore(name))
}

export async function checkResourceNameIsValid(name: string): Promise<void> {
  logger.info(Brand.mellancholize('Checking if resource has valid name...'))

  if (!hasValidResourceName(name))
    throw new Error(`'${name}' is not valid resource name. Please use PascalCase name with valid characters.`)
}

function hasValidResourceName(name: string): boolean {
  const resourceName = formatResourceName(name)
  return resourceName === name
}

function formatResourceName(name: string): null | string {
  const resourceName: string = name
    .replace(/^[\d-]|[#$-/:-?{-~!"^_`[\]]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()

  if (resourceName === '') return null

  if (resourceName.length === 1) return resourceName.toLocaleUpperCase()

  const match = resourceName.match(/[a-zA-Z\d]+/g)

  if (match) return match.map(titleCaseString).join('')

  return resourceName
}

const titleCaseString = (value: string): string => value[0].toLocaleUpperCase() + value.slice(1)
