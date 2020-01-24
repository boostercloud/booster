import * as Command from './command'
import * as Entity from './entity'
import * as Event from './event'
import * as Type from './type'
import * as Project from './project'

export const templates = {
  command: Command.template,
  entity: Entity.template,
  event: Event.template,
  type: Type.template,
  project: Project.templates,
}
