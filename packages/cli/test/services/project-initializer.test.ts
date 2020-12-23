import * as fs from 'fs-extra'
import * as path from 'path'
import * as childProcessPromise from 'child-process-promise'
import { 
    generateConfigFiles, 
    installDependencies, 
    generateRootDirectory, 
    initializeGit, 
    ProjectInitializerConfig 
} from '../../src/services/project-initializer'
import { restore, replace, fake } from 'sinon'
import { expect } from '../expect'

describe('project initializer', (): void => {

    beforeEach(() => {
        replace(fs,'mkdirs', fake.resolves({}))
        replace(fs,'outputFile', fake.resolves({}))
        replace(childProcessPromise, 'exec', fake.resolves({}))
    })

    afterEach(() => {
        restore()
    })

    const projectName = 'test-project'  
    const defaultProvider = '@boostercloud/framework-provider-aws'
    const defaultProjectInitializerConfig = {
        projectName: projectName,
        description: '',
        version: '0.1.0',
        author: '',
        homepage: '',
        license: 'MIT',
        repository: '',
        providerPackageName: defaultProvider,
        boosterVersion: '0.5.1',
        default: true,
        skipInstall: false,
        skipGit: false,
      } as ProjectInitializerConfig

    it('initialize Git', async () => {
        await initializeGit(defaultProjectInitializerConfig)
        expect(childProcessPromise.exec).to.have.been.calledWithMatch('git init && git add -A && git commit -m "Initial commit"')
    })

    it('Generate root directory', async () => {
        await generateRootDirectory(defaultProjectInitializerConfig)
        expect(fs.mkdirs).to.have.been.calledWithMatch(`${projectName}/src/commands`)
        expect(fs.mkdirs).to.have.been.calledWithMatch(`${projectName}/src/events`)
        expect(fs.mkdirs).to.have.been.calledWithMatch(`${projectName}/src/entities`)
        expect(fs.mkdirs).to.have.been.calledWithMatch(`${projectName}/src/read-models`)
        expect(fs.mkdirs).to.have.been.calledWithMatch(`${projectName}/src/config`)
        expect(fs.mkdirs).to.have.been.calledWithMatch(`${projectName}/src/common`)
        expect(fs.mkdirs).to.have.been.calledWithMatch(`${projectName}/src/event-handlers`)
        expect(fs.mkdirs).to.have.been.calledWithMatch(`${projectName}/src/scheduled-commands`)
    })
    
    it('install dependencies', async () => {
        await installDependencies(defaultProjectInitializerConfig)
        expect(childProcessPromise.exec).to.have.been.calledWithMatch('npx yarn install', { cwd: path.join(process.cwd(),projectName) })
    })

    it('Generate config files', async () => {
        await generateConfigFiles(defaultProjectInitializerConfig)
        expect(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/.eslintignore`)
        expect(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/.eslintrc.js`)
        expect(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/.gitignore`)
        expect(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/package.json`)
        expect(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/tsconfig.json`)
        expect(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/tsconfig.eslint.json`)
        expect(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/.prettierrc.yaml`)
        expect(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/src/config/config.ts`)
        expect(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/src/index.ts`)
    })
    
})