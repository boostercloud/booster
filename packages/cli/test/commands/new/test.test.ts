import * as ProjectChecker from '../../../src/services/project-checker'
import { restore, replace, fake, stub } from 'sinon'
import Test from '../../../src/commands/new/test'
import { templates } from '../../../src/templates'
import Mustache = require('mustache')
import * as fs from 'fs-extra'
import { IConfig } from '@oclif/config'
import { expect } from '../../expect'

describe('new', (): void => {
  describe('Test', () => {
    const testName = 'ExampleTest'
    const testsRoot = 'test/'
    const testPath = `${testsRoot}example-test.test.ts`
    const defaultTestImports = [
      {
        packagePath: '@boostercloud/framework-types',
        commaSeparatedComponents: 'BoosterConfig',
      },
      {
        packagePath: 'chai',
        commaSeparatedComponents: 'expect',
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
      await new Test([], {} as IConfig).init()
      expect(ProjectChecker.checkCurrentDirBoosterVersion).to.have.been.called
    })

    describe('Created correctly', () => {
      it('with valid test name', async () => {
        await new Test([testName], {} as IConfig).run()
        const renderedTest = Mustache.render(templates.test, {
          imports: defaultTestImports,
          name: testName,
          projectName: '@boostercloud/cli',
        })
        expect(fs.outputFile).to.have.been.calledWithMatch(testPath, renderedTest)
      })
    })

    describe('displays an error', () => {
      it('with empty test name', async () => {
        replace(console, 'error', fake.resolves({}))
        await new Test([], {} as IConfig).run()
        expect(fs.outputFile).to.have.not.been.calledWithMatch(testsRoot)
        expect(console.error).to.have.been.calledWithMatch(/You haven't provided a test name/)
      })

      it('with unexpected second parameter', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''

        try {
          await new Test([testName, 'AnotherTestParam'], {} as IConfig).run()
        } catch (error) {
          exceptionThrown = true
          exceptionMessage = error.message
        }

        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.contain('Unexpected argument: AnotherTestParam')
        expect(fs.outputFile).to.have.not.been.calledWithMatch(testPath)
      })
    })
  })
})
