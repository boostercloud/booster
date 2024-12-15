import * as path from 'path'
export const eventsDatabase = internalPath('events.json')

function internalPath(filename: string): string {
  return path.normalize(path.join('.', '.booster', filename))
}
