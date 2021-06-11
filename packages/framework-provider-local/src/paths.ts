import * as path from 'path'
export const registeredUsersDatabase = internalPath('registered_users.json')
export const authenticatedUsersDatabase = internalPath('authenticated_users.json')
export const eventsDatabase = internalPath('events.json')

export const readModelsDatabaseFilename = 'read_models.json'
export const readModelsDatabase = internalPath(readModelsDatabaseFilename)

function internalPath(filename: string): string {
  return path.normalize(path.join('.', '.booster', filename))
}
