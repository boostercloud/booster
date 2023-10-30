import * as ProjectChecker from '../../../src/services/project-checker'
import { restore, replace, fake, stub } from 'sinon'
import EventHandler from '../../../src/commands/new/event-handler'
import Mustache = require('mustache')
import * as fs from 'fs-extra'
import { Config } from '@oclif/core'
import { expect } from '../../expect'
import { template } from '../../../src/services/generator'

describe('new', (): void => {
  describe('Event', () => {
    const eventHandlerName = 'ExampleEventHandler'
    const eventHandlersRoot = 'src/event-handlers/'
    const eventHandlerPath = `${eventHandlersRoot}example-event-handler.ts`
    const defaultEventHandlerImports = [
      {
        packagePath: '../events/comment-posted',
        commaSeparatedComponents: 'CommentPosted',
      },
      {
        packagePath: '@boostercloud/framework-core',
        commaSeparatedComponents: 'EventHandler',
      },
      {
        packagePath: '@boostercloud/framework-types',
        commaSeparatedComponents: 'Register',
      },
    ]

    beforeEach(() => {
      stub(ProjectChecker, 'checkCurrentDirIsABoosterProject').returnsThis()
      replace(fs, 'outputFile', fake.resolves({}))
      replace(ProjectChecker, 'checkCurrentDirBoosterVersion', fake.resolves({}))
    })

    afterEach(() => {
      restore()
    })

    it('init calls checkCurrentDirBoosterVersion', async () => {
      await new EventHandler([], {} as Config).init()
      expect(ProjectChecker.checkCurrentDirBoosterVersion).to.have.been.called
    })

    describe('Created correctly', () => {
      it('creates Event with a event', async () => {
        await new EventHandler([eventHandlerName, '--event', 'CommentPosted'], {} as Config).run()
        const renderedEventHandler = Mustache.render(template('event-handler'), {
          imports: defaultEventHandlerImports,
          name: eventHandlerName,
          event: 'CommentPosted',
        })
        expect(fs.outputFile).to.have.been.calledWithMatch(eventHandlerPath, renderedEventHandler)
      })
    })

    describe('displays an error', () => {
      it('with no event', async () => {
        replace(console, 'error', fake.resolves({}))
        await new EventHandler([eventHandlerName], {} as Config).run()
        expect(fs.outputFile).to.have.not.been.calledWithMatch(eventHandlerPath)
        expect(console.error).to.have.been.calledWithMatch(/You haven't provided an event/)
      })

      it('with empty EventHandler name', async () => {
        replace(console, 'error', fake.resolves({}))
        await new EventHandler([], {} as Config).run()
        expect(fs.outputFile).to.have.not.been.calledWithMatch(eventHandlersRoot)
        expect(console.error).to.have.been.calledWithMatch(/You haven't provided an event handler name/)
      })

      it('with empty event', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          await new EventHandler([eventHandlerName, '--event'], {} as Config).run()
        } catch (e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.contain('--event expects a value')
        expect(fs.outputFile).to.have.not.been.calledWithMatch(eventHandlerPath)
      })

      it('creates EventHandler with two events', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          await new EventHandler([eventHandlerName, '--event', 'CommentPosted', 'ArticlePosted'], {} as Config).run()
        } catch (e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.contain('Unexpected argument: ArticlePosted')
        expect(fs.outputFile).to.have.not.been.calledWithMatch(eventHandlerPath)
      })
    })
  })
})
