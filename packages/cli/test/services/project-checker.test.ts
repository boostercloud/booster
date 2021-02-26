import * as path from 'path'
import {
    checkItIsABoosterProject,
    checkCurrentDirIsABoosterProject,
    checkProjectAlreadyExists,
    checkResourceExists,
    checkCurrentDirBoosterVersion
} from '../../src/services/project-checker'
import { restore, replace, fake, spy, stub } from 'sinon'
import { logger } from '../../src/services/logger'
import * as fs from 'fs-extra'
import { projectDir, ProjectInitializerConfig } from '../../src/services/project-initializer'
import Prompter from '../../src/services/user-prompt'
import { expect } from '../expect'

describe('project checker', (): void => {
   
    afterEach(() => {
        restore()
    })

    describe('checkCurrentDirIsABoosterProject', () => {
        it('is a Booster project', async () => {
            replace(process, 'cwd', fake.returns(path.join(process.cwd(), 'test', 'fixtures', 'mock_project')))
            let exceptionThrown = false
            await checkCurrentDirIsABoosterProject().catch(() => exceptionThrown = true)
            expect(exceptionThrown).to.be.equal(false)
        })

        it('is a Booster project with bad index.ts', async () => {
            replace(process, 'cwd', fake.returns(path.join(process.cwd(), 'test', 'fixtures', 'mock_project_bad_index')))
            let exceptionThrown = false
            await checkCurrentDirIsABoosterProject().catch(() => exceptionThrown = true)
            expect(exceptionThrown).to.be.equal(true)
        })

        it('is not a Booster project', async () => {
            replace(process, 'cwd', fake.returns(path.join(process.cwd(), 'test', 'fixtures')))
            let exceptionThrown = false
            await checkCurrentDirIsABoosterProject().catch(() => exceptionThrown = true)
            expect(exceptionThrown).to.be.equal(true)
        })
    })

    describe('checkItIsABoosterProject', (): void => {
        it('is a Booster project', async () => {
            const projectPath = path.join(process.cwd(), 'test', 'fixtures', 'mock_project')
            let exceptionThrown = false
            await checkItIsABoosterProject(projectPath).catch(() => exceptionThrown = true)
            expect(exceptionThrown).to.be.equal(false)
        })

        it('is a Booster project with bad index.ts', async () => {
            const projectPath = path.join(process.cwd(), 'test', 'fixtures', 'mock_project_bad_index')
            let exceptionThrown = false
            await checkItIsABoosterProject(projectPath).catch(() => exceptionThrown = true)
            expect(exceptionThrown).to.be.equal(true)
        })

        it('is not a Booster project', async () => {
            const projectPath = path.join(process.cwd(), 'test', 'fixtures')
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
        beforeEach(() => {
            replace(logger, 'info', fake.resolves({}))
        })

        it('should print info message and do nothing if resource doesn\'t exist', async () => {
            const resourcePath = path.join('test', 'fixtures', 'mock_project', 'src', 'entities')
            const existsSyncStub = stub(fs, 'existsSync')
            existsSyncStub.returns(false)
            spy(Prompter, 'confirmPrompt')

            await checkResourceExists('TestResource', resourcePath, '.ts')

            expect(logger.info).to.have.been.calledWithMatch('Checking if resource already exists...')
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

    describe('checkCurrentDirBoosterVersion', (): void => {
        
        beforeEach(() => {
            replace(logger, 'info', fake.resolves({}))
        })

        describe('inside a Booster project', () => {
            //project version in mocked package.json is 1.11.2
            beforeEach(() => {
                replace(process, 'cwd', fake.returns(path.join(process.cwd(), 'test', 'fixtures', 'mock_project')))
            })

            it('versions match', async () => {
                const userAgent = '@boostercloud/cli/1.11.2 darwin-x64 node-v12.10.0'
                let exceptionThrown = false
                await checkCurrentDirBoosterVersion(userAgent).catch(() => exceptionThrown = true)
                expect(exceptionThrown).to.be.equal(false)
                expect(logger.info).have.not.been.called
            })

            it('versions match on linux', async () => {
                const userAgent = '@boostercloud/cli/1.11.2 linux-x64 node-v12.10.0'
                let exceptionThrown = false
                await checkCurrentDirBoosterVersion(userAgent).catch(() => exceptionThrown = true)
                expect(exceptionThrown).to.be.equal(false)
                expect(logger.info).have.not.been.called
            })

            it('versions differs in fix number with cli version greater than project version', async () => {
                const userAgent = '@boostercloud/cli/1.11.3 darwin-x64 node-v12.10.0'
                let exceptionThrown = false
                await checkCurrentDirBoosterVersion(userAgent).catch(() => exceptionThrown = true)
                expect(exceptionThrown).to.be.equal(false)
                expect(logger.info).have.been.calledWithMatch(/WARNING: Project Booster version differs in the 'fix' section/)
            })
    
            it('versions differs in fix number with cli version lower than project version', async () => {
                const userAgent = '@boostercloud/cli/1.11.0 darwin-x64 node-v12.10.0'
                let exceptionThrown = false
                await checkCurrentDirBoosterVersion(userAgent).catch(() => exceptionThrown = true)
                expect(exceptionThrown).to.be.equal(false)
                expect(logger.info).have.been.calledWithMatch(/WARNING: Project Booster version differs in the 'fix' section/)
            })
    
            it('cli lower than project version in <feature> section', async () => {
                const userAgent = '@boostercloud/cli/1.10.2 darwin-x64 node-v12.10.0'
                let exceptionThrown = false
                let exceptionMessage = ''
                await checkCurrentDirBoosterVersion(userAgent).catch((e) => {
                    exceptionThrown = true
                    exceptionMessage = e.message
                })
                expect(exceptionThrown).to.be.equal(true)
                expect(exceptionMessage).to.contain('Please upgrade your @boostercloud/cli to the same version with "npm')
                expect(logger.info).have.not.been.called
            })
    
            it('cli lower than project version in <breaking> section', async () => {
                const userAgent = '@boostercloud/cli/0.11.2 darwin-x64 node-v12.10.0'
                let exceptionThrown = false
                let exceptionMessage = ''
                await checkCurrentDirBoosterVersion(userAgent).catch((e) => {
                    exceptionThrown = true
                    exceptionMessage = e.message
                })
                expect(exceptionThrown).to.be.equal(true)
                expect(exceptionMessage).to.contain('Please upgrade your @boostercloud/cli to the same version with "npm')
                expect(logger.info).have.not.been.called
            })
        
            it('cli version higher than project version in <feature> section', async () => {
                const userAgent = '@boostercloud/cli/1.12.2 darwin-x64 node-v12.10.0'
                let exceptionThrown = false
                let exceptionMessage = ''
                await checkCurrentDirBoosterVersion(userAgent).catch((e) => {
                    exceptionThrown = true
                    exceptionMessage = e.message
                })
                expect(exceptionThrown).to.be.equal(true)
                expect(exceptionMessage).to.contain('Please upgrade your project Booster dependencies')
                expect(logger.info).have.not.been.called
            })

            it('cli version higher than project version in <breaking> section', async () => {
                const userAgent = '@boostercloud/cli/2.11.2 darwin-x64 node-v12.10.0'
                let exceptionThrown = false
                let exceptionMessage = ''
                await checkCurrentDirBoosterVersion(userAgent).catch((e) => {
                    exceptionThrown = true
                    exceptionMessage = e.message
                })
                expect(exceptionThrown).to.be.equal(true)
                expect(exceptionMessage).to.contain('Please upgrade your project Booster dependencies')
                expect(logger.info).have.not.been.called
            })
    
            it('cli version wrong length shorter', async () => {
                const userAgent = '@boostercloud/cli/1.11 darwin-x64 node-v12.10.0'
                let exceptionThrown = false
                let exceptionMessage = ''
                await checkCurrentDirBoosterVersion(userAgent).catch((e) => {
                    exceptionThrown = true
                    exceptionMessage = e.message
                })
                expect(exceptionThrown).to.be.equal(true)
                expect(exceptionMessage).to.contain('Versions must follow semantic convention X.Y.Z')
                expect(logger.info).have.not.been.called
            })
    
            it('cli version wrong length longer', async () => {
                const userAgent = '@boostercloud/cli/1.11.2.1 darwin-x64 node-v12.10.0'
                let exceptionThrown = false
                let exceptionMessage = ''
                await checkCurrentDirBoosterVersion(userAgent).catch((e) => {
                    exceptionThrown = true
                    exceptionMessage = e.message
                })
                expect(exceptionThrown).to.be.equal(true)
                expect(exceptionMessage).to.contain('Versions must follow semantic convention X.Y.Z')
                expect(logger.info).have.not.been.called
            })
        })
        

        it('outside a Booster project', async () => {
            replace(process, 'cwd', fake.returns(path.join(process.cwd(), 'test', 'fixtures')))
            const userAgent = '@boostercloud/cli/1.11.2 darwin-x64 node-v12.10.0'
            let exceptionThrown = false
            let exceptionMessage = ''
            await checkCurrentDirBoosterVersion(userAgent).catch((e) => {
                exceptionThrown = true
                exceptionMessage = e.message
            })
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.contain('There was an error when recognizing the application')
            expect(logger.info).have.not.been.called
        })
    })
})