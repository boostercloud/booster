import * as ProjectChecker from '../../../src/services/project-checker'
import { restore, replace, fake, stub } from 'sinon'
import Event from '../../../src/commands/new/event'
import Mustache = require('mustache')
import * as fs from 'fs-extra'
import { Config } from '@oclif/core'
import { expect } from '../../expect'
import { template } from '../../../src/services/generator'

describe('new', (): void => {
  describe('Event', () => {
    const eventName = 'ExampleEvent'
    const eventsRoot = 'src/events/'
    const eventPath = `${eventsRoot}example-event.ts`
    const defaultEventImports = [
      {
        packagePath: '@boostercloud/framework-core',
        commaSeparatedComponents: 'Event',
      },
      {
        packagePath: '@boostercloud/framework-types',
        commaSeparatedComponents: 'UUID',
      },
    ]

    const renderEvent = (name: string, fields: any[]): string => {
      return Mustache.render(template('event'), {
        imports: defaultEventImports,
        name: name,
        fields: fields,
      })
    }

    beforeEach(() => {
      stub(ProjectChecker, 'checkCurrentDirIsABoosterProject').returnsThis()
      replace(fs, 'outputFile', fake.resolves({}))
      replace(ProjectChecker, 'checkCurrentDirBoosterVersion', fake.resolves({}))
    })

    afterEach(() => {
      restore()
    })

    it('init calls checkCurrentDirBoosterVersion', async () => {
      const config = await Config.load()
      await new Event([], config).init()
      expect(ProjectChecker.checkCurrentDirBoosterVersion).to.have.been.called
    })

    describe('Created correctly', () => {
      it('with no fields', async () => {
        const config = await Config.load()
        await new Event([eventName], config).run()
        const renderedEvent = renderEvent(eventName, [])
        expect(fs.outputFile).to.have.been.calledWithMatch(eventPath, renderedEvent)
      })

      it('creates Event with a string field', async () => {
        const config = await Config.load()
        await new Event([eventName, '--fields', 'title:string'], config).run()
        const renderedEvent = renderEvent(eventName, [{ name: 'title', type: 'string' }])
        expect(fs.outputFile).to.have.been.calledWithMatch(eventPath, renderedEvent)
      })

      it('creates Event with a number field', async () => {
        const config = await Config.load()
        await new Event([eventName, '--fields', 'quantity:number'], config).run()
        const renderedEvent = renderEvent(eventName, [{ name: 'quantity', type: 'number' }])
        expect(fs.outputFile).to.have.been.calledWithMatch(eventPath, renderedEvent)
      })

      it('creates Event with UUID field', async () => {
        const config = await Config.load()
        await new Event([eventName, '--fields', 'identifier:UUID'], config).run()
        const renderedEvent = renderEvent(eventName, [{ name: 'identifier', type: 'UUID' }])
        expect(fs.outputFile).to.have.been.calledWithMatch(eventPath, renderedEvent)
      })

      it('creates Event with multiple fields', async () => {
        const config = await Config.load()
        await new Event(
          [eventName, '--fields', 'title:string', 'quantity:number', 'identifier:UUID'],
          config
        ).run()
        const fields = [
          { name: 'title', type: 'string' },
          { name: 'quantity', type: 'number' },
          { name: 'identifier', type: 'UUID' },
        ]
        const renderedEvent = renderEvent(eventName, fields)
        expect(fs.outputFile).to.have.been.calledWithMatch(eventPath, renderedEvent)
      })
    })

    describe('displays an error', () => {
      it('with empty Event name', async () => {
        replace(console, 'error', fake.resolves({}))
        const config = await Config.load()
        await new Event([], config).run()
        expect(fs.outputFile).to.have.not.been.calledWithMatch(eventsRoot)
        expect(console.error).to.have.been.calledWithMatch(/You haven't provided an event name/)
      })

      it('with empty fields', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          const config = await Config.load()
          await new Event([eventName, '--fields'], config).run()
        } catch (e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.contain('--fields expects a value')
      })

      it('with field with no type', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          const config = await Config.load()
          await new Event([eventName, '--fields', 'title'], config).run()
        } catch (e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.contain('Error parsing field title')
      })

      it('with no field type after :', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          const config = await Config.load()
          await new Event([eventName, '--fields', 'title:'], config).run()
        } catch (e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.contain('Error parsing field title')
        expect(fs.outputFile).to.have.not.been.calledWithMatch(eventPath)
      })

      it('with repeated fields', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          const config = await Config.load()
          await new Event(
            [eventName, '--fields', 'title:string', 'title:string', 'quantity:number'],
            config
          ).run()
        } catch (e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.contain('Error parsing field title')
        expect(fs.outputFile).to.have.not.been.calledWithMatch(eventPath)
      })
    })
  })
})
