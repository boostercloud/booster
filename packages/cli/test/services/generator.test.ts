import * as path from 'path'
import * as fs from 'fs-extra'
import * as Mustache from 'mustache'
import { Target, HasName, HasFields, HasReaction, HasEvent, HasProjections } from '../../src/services/generator/target'
import * as projectChecker from '../../src/services/project-checker'
import { generate } from '../../src/services/generator'
import { templates } from '../../src/templates'
import { restore, replace, fake } from 'sinon'
import { expect } from '../expect'

describe('generate service', (): void => {
  beforeEach(() => {
    replace(fs, 'outputFile', fake.resolves({}))
    replace(projectChecker, 'checkResourceExists', fake.resolves({}))
  })

  afterEach(() => {
    restore()
  })

  it('generates file for a command', async () => {
    type CommandInfo = HasName & HasFields
    const info: CommandInfo = {
      name: 'NewCommand',
      fields: [
        { name: 'name', type: 'string' },
        { name: 'description', type: 'string' },
      ],
    }

    const target: Target<CommandInfo> = {
      name: info.name,
      extension: '.ts',
      placementDir: path.join('src', 'commands'),
      template: templates.command,
      info: info,
    }
    const rendered = Mustache.render(target.template, { ...target.info })

    await generate(target)

    expect(fs.outputFile).to.have.been.calledWithMatch('src/commands/new-command.ts', rendered)
    expect(projectChecker.checkResourceExists).to.have.been.called
  })

  it('generates file for an entity', async () => {
    type EntityInfo = HasName & HasFields & HasReaction
    const info: EntityInfo = {
      name: 'NewEntity',
      fields: [
        { name: 'name', type: 'string' },
        { name: 'description', type: 'string' },
      ],
      events: [{ eventName: 'product-purchased' }],
    }
    const target: Target<EntityInfo> = {
      name: info.name,
      extension: '.ts',
      placementDir: path.join('src', 'entities'),
      template: templates.entity,
      info: info,
    }
    const rendered = Mustache.render(target.template, { ...target.info })

    await generate(target)

    expect(fs.outputFile).to.have.been.calledWithMatch('src/entities/new-entity.ts', rendered)
    expect(projectChecker.checkResourceExists).to.have.been.called
  })

  it('generates file for an event handler', async () => {
    type EventHandlerInfo = HasName & HasEvent
    const info: EventHandlerInfo = {
      name: 'NewEventHandler',
      event: 'product-purchased',
    }

    const target: Target<EventHandlerInfo> = {
      name: info.name,
      extension: '.ts',
      placementDir: path.join('src', 'event-handlers'),
      template: templates.eventHandler,
      info: info,
    }
    const rendered = Mustache.render(target.template, { ...target.info })

    await generate(target)

    expect(fs.outputFile).to.have.been.calledWithMatch('src/event-handlers/new-event-handler.ts', rendered)
    expect(projectChecker.checkResourceExists).to.have.been.called
  })

  it('generates file for an event', async () => {
    type EventInfo = HasName & HasFields
    const info: EventInfo = {
      name: 'NewEvent',
      fields: [
        { name: 'name', type: 'string' },
        { name: 'description', type: 'string' },
      ],
    }

    const target: Target<EventInfo> = {
      name: info.name,
      extension: '.ts',
      placementDir: path.join('src', 'events'),
      template: templates.event,
      info: info,
    }
    const rendered = Mustache.render(target.template, { ...target.info })

    await generate(target)

    expect(fs.outputFile).to.have.been.calledWithMatch('src/events/new-event.ts', rendered)
    expect(projectChecker.checkResourceExists).to.have.been.called
  })

  it('generates file for a read model', async () => {
    type ReadModelInfo = HasName & HasFields & HasProjections
    const info: ReadModelInfo = {
      name: 'NewReadModel',
      fields: [
        { name: 'name', type: 'string' },
        { name: 'description', type: 'string' },
      ],
      projections: [{ entityName: 'account', entityId: 'id' }],
    }

    const target: Target<ReadModelInfo> = {
      name: info.name,
      extension: '.ts',
      placementDir: path.join('src', 'read-models'),
      template: templates.readModel,
      info: info,
    }
    const rendered = Mustache.render(target.template, { ...target.info })

    await generate(target)

    expect(fs.outputFile).to.have.been.calledWithMatch('src/read-models/new-read-model.ts', rendered)
    expect(projectChecker.checkResourceExists).to.have.been.called
  })

  it('generates file for a scheduled command', async () => {
    type ScheduledCommandInfo = HasName
    const info: ScheduledCommandInfo = {
      name: 'NewScheduledCommand',
    }

    const target: Target<ScheduledCommandInfo> = {
      name: info.name,
      extension: '.ts',
      placementDir: path.join('src', 'scheduled-commands'),
      template: templates.scheduledCommand,
      info: info,
    }
    const rendered = Mustache.render(target.template, { ...target.info })

    await generate(target)

    expect(fs.outputFile).to.have.been.calledWithMatch('src/scheduled-commands/new-scheduled-command.ts', rendered)
    expect(projectChecker.checkResourceExists).to.have.been.called
  })

  it('generates file for a type', async () => {
    type TypeInfo = HasName & HasFields
    const info: TypeInfo = {
      name: 'NewType',
      fields: [
        { name: 'name', type: 'string' },
        { name: 'description', type: 'string' },
      ],
    }

    const target: Target<TypeInfo> = {
      name: info.name,
      extension: '.ts',
      placementDir: path.join('src', 'common'),
      template: templates.type,
      info: info,
    }
    const rendered = Mustache.render(target.template, { ...target.info })

    await generate(target)

    expect(fs.outputFile).to.have.been.calledWithMatch('src/common/new-type.ts', rendered)
    expect(projectChecker.checkResourceExists).to.have.been.called
  })
})
