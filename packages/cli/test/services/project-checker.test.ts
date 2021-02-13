import * as path from 'path'
import {
    checkItIsABoosterProject,
    checkCurrentDirIsABoosterProject,
    checkProjectAlreadyExists,
    checkResourceExists,
} from '../../src/services/project-checker'
import { restore, replace, fake, spy, stub } from 'sinon'
import { expect } from '../expect'
import { oraLogger } from '../../src/services/logger'
import * as fs from 'fs-extra'
import { projectDir, ProjectInitializerConfig } from '../../src/services/project-initializer'
import Prompter from '../../src/services/user-prompt'

describe('project checker', (): void => {
    beforeEach(() => {
        replace(oraLogger,'info', fake.resolves({}))
    })

    afterEach(() => {
        restore()
    })

    describe('checkCurrentDirIsABoosterProject', () => {
        it('is a Booster project', async () => {
            replace(process,'cwd', fake.returns(path.join(process.cwd(),'test', 'fixtures', 'mock_project')))
            let exceptionThrown = false
            await checkCurrentDirIsABoosterProject().catch(() => exceptionThrown = true)
            expect(exceptionThrown).to.be.equal(false)
        })

        it('is a Booster project with bad index.ts', async () => {
            replace(process,'cwd', fake.returns(path.join(process.cwd(),'test', 'fixtures', 'mock_project_bad_index')))
            let exceptionThrown = false
            await checkCurrentDirIsABoosterProject().catch(() => exceptionThrown = true)
            expect(exceptionThrown).to.be.equal(true)
        })

        it('is not a Booster project', async () => {
            replace(process,'cwd', fake.returns(path.join(process.cwd(),'test', 'fixtures')))
            let exceptionThrown = false
            await checkCurrentDirIsABoosterProject().catch(() => exceptionThrown = true)
            expect(exceptionThrown).to.be.equal(true)
        })
    })

    describe('checkItIsABoosterProject', (): void => {
        it('is a Booster project', async () => {
            const projectPath = path.join(process.cwd(),'test', 'fixtures', 'mock_project')
            let exceptionThrown = false
            await checkItIsABoosterProject(projectPath).catch(() => exceptionThrown = true)
            expect(exceptionThrown).to.be.equal(false)
        })

        it('is a Booster project with bad index.ts', async () => {
            const projectPath = path.join(process.cwd(),'test', 'fixtures', 'mock_project_bad_index')
            let exceptionThrown = false
            await checkItIsABoosterProject(projectPath).catch(() => exceptionThrown = true)
            expect(exceptionThrown).to.be.equal(true)
        })

        it('is not a Booster project', async () => {
            const projectPath = path.join(process.cwd(),'test', 'fixtures')
            let exceptionThrown = false
            await checkItIsABoosterProject(projectPath).catch(() => exceptionThrown = true)
            expect(exceptionThrown).to.be.equal(true)
        })
    })

    describe('checkProjectAlreadyExists', (): void => {
        it('should do nothing if project with given name does not exist', async () => {
            const existsSyncStub = stub(fs, 'existsSync')
            existsSyncStub.returns(false)
            spy(Prompter, 'confirmPrompt')

            const projectName = path.join('test', 'fixtures', 'mock_project_test')
            const projectPath = projectDir({ projectName } as ProjectInitializerConfig)
            await checkProjectAlreadyExists(projectName)

            expect(fs.existsSync).to.have.been.calledWithMatch(projectPath)
            expect(Prompter.confirmPrompt).not.to.have.been.called
        })

        it('should throw error when project exists and user refuses to overwrite it', async () => {
            const existsSyncStub = stub(fs, 'existsSync')
            existsSyncStub.returns(true)

            const fakePrompter = fake.resolves(false)
            replace(Prompter, 'confirmPrompt', fakePrompter)

            const projectName = path.join('test', 'fixtures', 'mock_project_test')
            const projectPath = projectDir({ projectName } as ProjectInitializerConfig)
            let exceptionThrown = false
            let exceptionMessage = ''

            await checkProjectAlreadyExists(projectName).catch((e) => {
                exceptionThrown = true
                exceptionMessage = e.message
            })

            expect(fs.existsSync).to.have.been.calledWithMatch(projectPath)
            expect(Prompter.confirmPrompt).to.have.been.called
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.be.equal('The folder you\'re trying to use already exists. Please use another project name')
        })

        it('should remove project folder when project already exists and user agreed to overwrite it', async () => {
            replace(fs, 'removeSync', fake.resolves({}))
            const existsSyncStub = stub(fs, 'existsSync')
            existsSyncStub.returns(true)

            const fakePrompter = fake.resolves(true)
            replace(Prompter, 'confirmPrompt', fakePrompter)

            const projectName = path.join('test', 'fixtures', 'mock_project_test')
            const projectPath = projectDir({ projectName } as ProjectInitializerConfig)

            await checkProjectAlreadyExists(projectName)

            expect(fs.removeSync).to.have.been.calledWithMatch(projectPath)
        })
    })

    describe('checkResourceExists', (): void => {
        it('should print info message and do nothing if resource doesn\'t exist', async () => {
            const resourcePath = path.join('test', 'fixtures', 'mock_project', 'src', 'entities')
            const existsSyncStub = stub(fs, 'existsSync')
            existsSyncStub.returns(false)
            spy(Prompter, 'confirmPrompt')

            await checkResourceExists('TestResource', resourcePath, '.ts')

            expect(oraLogger.info).to.have.been.calledWithMatch('Checking if resource already exists...')
            expect(fs.existsSync).to.have.been.calledWithMatch(resourcePath)
            expect(Prompter.confirmPrompt).not.to.have.been.called
        })

        it('should throw error when resource exists and user refuses to overwrite it', async () => {
            const resourcePath = path.join('test', 'fixtures', 'mock_project', 'src', 'entities')
            const fakePrompter = fake.resolves(false)
            const existsSyncStub = stub(fs, 'existsSync')
            let exceptionThrown = false
            let exceptionMessage = ''

            existsSyncStub.returns(true)
            replace(Prompter, 'confirmPrompt', fakePrompter)

            await checkResourceExists('TestResource', resourcePath, '.ts').catch((e) => {
                exceptionThrown = true
                exceptionMessage = e.message
            })

            expect(fs.existsSync).to.have.been.calledWithMatch(resourcePath)
            expect(Prompter.confirmPrompt).to.have.been.called
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.be.equal( 'The \'entities\' resource "test-resource.ts" already exists. Please use another resource name')
        })

        it('should remove resource when it exists and user agreed to overwrite it', async () => {
            const resourcePath = path.join('test', 'fixtures', 'mock_project', 'src', 'entities')
            const fakePrompter = fake.resolves(true)
            const existsSyncStub = stub(fs, 'existsSync')

            existsSyncStub.returns(true)
            replace(fs, 'removeSync', fake.resolves({}))
            replace(Prompter, 'confirmPrompt', fakePrompter)

            await checkResourceExists('TestResource', resourcePath, '.ts')

            expect(fs.removeSync).to.have.been.calledWithMatch(resourcePath)
        })
    })
})
