import * as path from 'path'
export const registeredUsersDatabase = internalPath('registered_users.json')
export const authenticatedUsersDatabase = internalPath('authenticated_users.json')
export const eventsDatabase = internalPath('events.json')

function internalPath(filename: string): string {
  return path.normalize(path.join('.', '.booster', filename))
}
