import { UUID } from '@boostercloud/framework-types'
import uuid = require('uuid/v4')

export function generateUUID(): UUID {
  return uuid()
}
