import * as ProjectChecker from '../../../src/services/project-checker'
import { restore, replace, fake, stub } from 'sinon'
import Event from '../../../src/commands/new/event'
import { templates } from '../../../src/templates'
import Mustache = require('mustache')
import * as fs from 'fs-extra'
import { IConfig } from '@oclif/config'
import { expect } from '../../expect'

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
      }
    ]

    beforeEach(() => {
      stub(ProjectChecker, 'checkItIsABoosterProject').returnsThis()
      replace(fs,'outputFile', fake.resolves({}))
    })

    afterEach(() => {
      restore()
    })

    describe('Created correctly', () => { 
      it('with no fields', async () => {
        await new Event([eventName], {} as IConfig).run()
        const renderedEvent = Mustache.render(templates.event, {
          imports: defaultEventImports,
          name: eventName,
          fields: []
        })
        expect(fs.outputFile).to.have.been.calledWithMatch(eventPath,renderedEvent)
      })

      it('creates Event with a string field', async () => {
        await new Event([eventName, '--fields', 'title:string'], {} as IConfig).run()
        const renderedEvent = Mustache.render(templates.event, {
          imports: defaultEventImports,
          name: eventName,
          fields: [{ name: 'title', type: 'string' }]
        })
        expect(fs.outputFile).to.have.been.calledWithMatch(eventPath,renderedEvent)
      })

      it('creates Event with a number field', async () => {
        await new Event([eventName, '--fields', 'quantity:number'], {} as IConfig).run()
        const renderedEvent = Mustache.render(templates.event, {
          imports: defaultEventImports,
          name: eventName,
          fields: [{ name: 'quantity', type: 'number' }]
        })
        expect(fs.outputFile).to.have.been.calledWithMatch(eventPath,renderedEvent)
      })

      it('creates Event with UUID field', async () => {
        await new Event([eventName, '--fields', 'identifier:UUID'], {} as IConfig).run()
        const renderedEvent = Mustache.render(templates.event, {
          imports: defaultEventImports,
          name: eventName,
          fields: [{ name: 'identifier', type: 'UUID' }]
        })
        expect(fs.outputFile).to.have.been.calledWithMatch(eventPath,renderedEvent)
      })

      it('creates Event with multiple fields', async () => {
        await new Event([eventName, '--fields', 'title:string','quantity:number','identifier:UUID'], {} as IConfig).run()
        const renderedEvent = Mustache.render(templates.event, {
          imports: defaultEventImports,
          name: eventName,
          fields: [{ name: 'title', type: 'string' },{ name: 'quantity', type: 'number' },{ name: 'identifier', type: 'UUID' }]
        })
        expect(fs.outputFile).to.have.been.calledWithMatch(eventPath,renderedEvent)
      })
    })

    describe('displays an error', () => { 
      it('with empty Event name', async () => {
        replace(console,'error', fake.resolves({}))
        await new Event([], {} as IConfig).run()
        expect(fs.outputFile).to.have.not.been.calledWithMatch(eventsRoot)
        expect(console.error).to.have.been.calledWith("You haven't provided an event name, but it is required, run with --help for usage")
      })

      it('with empty fields', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          await new Event([eventName, '--fields'], {} as IConfig).run()
        } catch(e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.be.equal('Flag --fields expects a value')
      })

      it('with field with no type', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          await new Event([eventName, '--fields','title'], {} as IConfig).run()
        } catch(e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.contain('Error: Error parsing field title. Fields must be in the form of <field name>:<field type>')
      })

      it('with no field type after :', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          await new Event([eventName, '--fields','title:'], {} as IConfig).run()
        } catch(e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.contain('Error: Error parsing field title:. Fields must be in the form of <field name>:<field type>')
        expect(fs.outputFile).to.have.not.been.calledWithMatch(eventPath)
      })
      
    })

    describe('should display an error but is not currently being validated', () => {
      it('with repeated fields', async () => {
        await new Event([eventName, '--fields', 'title:string','title:string','quantity:number'], {} as IConfig).run()
        const renderedEvent = Mustache.render(templates.event, {
          imports: defaultEventImports,
          name: eventName,
          fields: [{ name: 'title', type: 'string' },{ name: 'title', type: 'string' },{ name: 'quantity', type: 'number' }]
        })
        expect(fs.outputFile).to.have.been.calledWithMatch(eventPath,renderedEvent)
      })
    })
  })
})
