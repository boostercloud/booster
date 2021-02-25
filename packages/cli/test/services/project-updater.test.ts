import { updatePackageJsonDependencyVersions } from '../../src/services/project-updater'
import * as fs from 'fs-extra'
import * as path from 'path'
import { restore, replace, fake } from 'sinon'
import { expect } from '../expect'

describe('project updater', () => {

    afterEach(() => {
        restore()
    })

    describe('updatePackageJsonDependencyVersions', () => {
        beforeEach(() => {
            replace(fs, 'outputFile', fake.resolves({}))
        })

        it('inside a Booster project', async () => {
            const projectPath: string = path.join(process.cwd(),'test', 'fixtures', 'mock_project')
            const packageJsonPath: string = path.join(projectPath,'package.json')
            await updatePackageJsonDependencyVersions('3.0.0', projectPath)

            //dependencies
            const boosterCoreLine = `"@boostercloud/framework-core": "^3.0.0",`
            const boosterTypesLine = `"@boostercloud/framework-types": "^3.0.0",`
            const boosterAwsLine = `"@boostercloud/framework-provider-aws": "*",`
            const tslibLine = `"tslib": "^2.0.3"`
            expect(fs.outputFile).to.have.been.calledWithMatch(packageJsonPath, boosterCoreLine)
            expect(fs.outputFile).to.have.been.calledWithMatch(packageJsonPath, boosterTypesLine)
            expect(fs.outputFile).to.have.been.calledWithMatch(packageJsonPath, boosterAwsLine)
            expect(fs.outputFile).to.have.been.calledWithMatch(packageJsonPath, tslibLine)

            //dev dependencies
            const boosterDevAwsInfLine = `"@boostercloud/framework-provider-aws-infrastructure": "*",`
            const boosterDevRocketBackupLine = `"@boostercloud/rocket-backup-aws-infrastructure": "^3.0.0",`
            const boosterDevRocketStaticLine = `"@boostercloud/rocket-static-sites-aws-infrastructure": "^3.0.0",`
            const rimrafLine = `"rimraf": "^3.0.1",`
            expect(fs.outputFile).to.have.been.calledWithMatch(packageJsonPath, boosterDevAwsInfLine)
            expect(fs.outputFile).to.have.been.calledWithMatch(packageJsonPath, boosterDevRocketBackupLine)
            expect(fs.outputFile).to.have.been.calledWithMatch(packageJsonPath, boosterDevRocketStaticLine)
            expect(fs.outputFile).to.have.been.calledWithMatch(packageJsonPath, rimrafLine)
        })

        it('outside a Booster project', async () => {
            const projectPath: string = path.join(process.cwd(),'test', 'fixtures')
            const packageJsonPath: string = path.join(projectPath,'package.json')
            let exceptionThrown = false
            let exceptionMessage = ''
            await updatePackageJsonDependencyVersions('3.0.0', projectPath).catch((e) => {
                exceptionThrown = true
                exceptionMessage = e.message
            })
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.contain('There was an error when recognizing the application package.json file')
            expect(fs.outputFile).to.have.not.been.calledWithMatch(packageJsonPath)
        })
    })
})