import { readModelsDatabaseFilename } from '@boostercloud/framework-provider-local'
import * as path from 'path'

const sandboxName = 'local-project-integration-sandbox'

export const readModelDbFilename = (): string => {
    return path.normalize(path.join('.', sandboxName, '.booster', readModelsDatabaseFilename))
}