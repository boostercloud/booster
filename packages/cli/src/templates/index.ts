import * as Command from './command'
import * as Entity from './entity'
import * as Event from './event'
import * as Type from './type'
import * as Project from './project'
import * as ReadModel from './read-model'
import * as EventHandler from './event-handler'
import * as ScheduledCommand from './scheduled-command'

export const templates = {
  command: Command.template,
  entity: Entity.template,
  event: Event.template,
  eventHandler: EventHandler.template,
  type: Type.template,
  project: Project.templates,
  readModel: ReadModel.template,
  scheduledCommand: ScheduledCommand.template,
}
