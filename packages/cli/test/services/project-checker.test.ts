import * as path from 'path'
import { checkItIsABoosterProject, checkCurrentDirIsABoosterProject } from '../../src/services/project-checker'
import { restore, replace, fake } from 'sinon'
import { expect } from '../expect'

describe('project checker', (): void => {

    afterEach(() => {
        restore()
    })

    describe('checkCurrentDirIsABoosterProject', () => {
        it('is a Booster project', async () => {
            replace(process,'cwd', fake.returns(path.join(process.cwd(),'resources', 'mock_project')))
            let exceptionThrown = false
            await checkCurrentDirIsABoosterProject().catch(() => exceptionThrown = true)
            expect(exceptionThrown).to.be.equal(false)
        })
    
        it('is a Booster project with bad index.ts', async () => {
            replace(process,'cwd', fake.returns(path.join(process.cwd(),'resources', 'mock_project_bad_index')))
            let exceptionThrown = false
            await checkCurrentDirIsABoosterProject().catch(() => exceptionThrown = true)
            expect(exceptionThrown).to.be.equal(true)
        })
    
        it('is not a Booster project', async () => {
            replace(process,'cwd', fake.returns(path.join(process.cwd(),'resources')))
            let exceptionThrown = false
            await checkCurrentDirIsABoosterProject().catch(() => exceptionThrown = true)
            expect(exceptionThrown).to.be.equal(true)
        })
    })

    describe('checkItIsABoosterProject', (): void => {
        it('is a Booster project', async () => {
            const projectPath = path.join(process.cwd(),'resources', 'mock_project')
            let exceptionThrown = false
            await checkItIsABoosterProject(projectPath).catch(() => exceptionThrown = true)
            expect(exceptionThrown).to.be.equal(false)
        })
    
        it('is a Booster project with bad index.ts', async () => {
            const projectPath = path.join(process.cwd(),'resources', 'mock_project_bad_index')
            let exceptionThrown = false
            await checkItIsABoosterProject(projectPath).catch(() => exceptionThrown = true)
            expect(exceptionThrown).to.be.equal(true)
        })
    
        it('is not a Booster project', async () => {
            const projectPath = path.join(process.cwd(),'resources')
            let exceptionThrown = false
            await checkItIsABoosterProject(projectPath).catch(() => exceptionThrown = true)
            expect(exceptionThrown).to.be.equal(true)
        })
    })
})