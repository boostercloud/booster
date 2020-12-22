import * as ProjectChecker from '../../src/services/project-checker'
import { restore, replace, fake, stub, spy } from 'sinon'
import ErrnoException = NodeJS.ErrnoException
import { ProjectInitializerConfig } from '../../src/services/project-initializer'
import ReadModel from '../../src/commands/new/read-model'
import { templates } from '../../src/templates'
import Mustache = require('mustache')
import * as fs_simple from 'fs'
import * as fs from 'fs-extra'
import * as childProcessPromise from 'child-process-promise'
import { IConfig } from '@oclif/config'
import { expect } from '../expect'
import * as Project from '../../src/commands/new/project'
import * as ProjectInitializer from '../../src/services/project-initializer'

describe('new', (): void => {
  describe('Read model', () => {
    const readModel = 'example-read-model'
    const readModelsRoot = './src/read-models/'
    const readModelPath = `${readModelsRoot}${readModel}.ts`
    afterEach(() => {
      if (fs.existsSync(readModelPath)) {
        fs_simple.rmdir(readModelsRoot, { recursive: true }, (err: ErrnoException | null) => console.log(err))
      }
      restore()
    })
    context('projections', () => {
      it('renders according to the template', async () => {
        stub(ProjectChecker, 'checkCurrentDirIsABoosterProject').returnsThis()
        await new ReadModel([readModel, '--fields', 'title:string', '--projects', 'Post:id'], {} as IConfig).run()
        const readModelFileContent = fs.readFileSync(readModelPath).toString()
        const renderedReadModel = Mustache.render(templates.readModel, {
          imports: [
            {
              packagePath: '@boostercloud/framework-core',
              commaSeparatedComponents: 'ReadModel, Projects',
            },
            {
              packagePath: '@boostercloud/framework-types',
              commaSeparatedComponents: 'UUID, ProjectionResult',
            },
            {
              packagePath: '../entities/post',
              commaSeparatedComponents: 'Post',
            },
          ],
          name: readModel,
          fields: [{ name: 'title', type: 'string' }],
          projections: [
            {
              entityName: 'Post',
              entityId: 'id',
            },
          ],
        })

        expect(readModelFileContent).to.be.equal(renderedReadModel)
      })
    })
  })
  describe('project', () => {
    context('file generation', () => {
      const projectName = 'test-project'
      const projectDirectory = `./${projectName}`
      const defaultProvider = '@boostercloud/framework-provider-aws'

      const expectedDirectoryContent = {
        rootPath: [
          '.git',
          '.eslintignore',
          '.eslintrc.js',
          '.gitignore',
          '.prettierrc.yaml',
          'package.json',
          'src',
          'tsconfig.eslint.json',
          'tsconfig.json',
        ],
        src: [
          'commands',
          'events',
          'event-handlers',
          'entities',
          'read-models',
          'scheduled-commands',
          'config',
          'common',
          'index.ts',
        ],
      }
      
      const expectFilesAndDirectoriesCreated = (projectName: string) => {
        expect(fs.mkdirs).to.have.been.calledWithMatch(`${projectName}/src/commands`)
        expect(fs.mkdirs).to.have.been.calledWithMatch(`${projectName}/src/events`)
        expect(fs.mkdirs).to.have.been.calledWithMatch(`${projectName}/src/entities`)
        expect(fs.mkdirs).to.have.been.calledWithMatch(`${projectName}/src/read-models`)
        expect(fs.mkdirs).to.have.been.calledWithMatch(`${projectName}/src/config`)
        expect(fs.mkdirs).to.have.been.calledWithMatch(`${projectName}/src/common`)
        expect(fs.mkdirs).to.have.been.calledWithMatch(`${projectName}/src/event-handlers`)
        expect(fs.mkdirs).to.have.been.calledWithMatch(`${projectName}/src/scheduled-commands`)
        expect(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/.eslintignore`)
        expect(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/.eslintrc.js`)
        expect(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/.gitignore`)
        expect(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/package.json`)
        expect(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/tsconfig.json`)
        expect(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/tsconfig.eslint.json`)
        expect(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/.prettierrc.yaml`)
        expect(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/src/config/config.ts`)
        expect(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/src/index.ts`)
      }

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

      afterEach(() => {
        if (fs.existsSync(projectDirectory)) {
          fs.rmdirSync(projectDirectory, { recursive: true })
        }
        restore()
      })

      it('generates all required files and folders', async () => {
        replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))
        replace(ProjectInitializer, 'installDependencies', fake.returns({}))
        replace(fs,'mkdirs', fake.resolves({}))
        replace(fs,'outputFile', fake.resolves({}))
        replace(childProcessPromise, 'exec', fake.resolves({}))

        await new Project.default([projectName], {} as IConfig).run()

        expect(childProcessPromise.exec).to.have.been.calledWithMatch('git init && git add -A && git commit -m "Initial commit"')
        //expect(childProcessPromise.exec).to.have.been.calledWithMatch('npx yarn install')
        expectFilesAndDirectoriesCreated(projectName)
      })

      it('generates all required files and folders without installing dependencies', async () => {
        replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))
        const installDependenciesSpy = spy(ProjectInitializer, 'installDependencies')
        replace(fs,'mkdirs', fake.resolves({}))
        replace(fs,'outputFile', fake.resolves({}))
        replace(childProcessPromise, 'exec', fake.resolves({}))

        await new Project.default([projectName, '--skipInstall'], {} as IConfig).run()

        expect(installDependenciesSpy).to.have.not.been.calledOnce
        expect(childProcessPromise.exec).to.have.not.been.calledWithMatch('npx yarn install')
        expect(childProcessPromise.exec).to.have.been.calledWithMatch('git init && git add -A && git commit -m "Initial commit"')
        expectFilesAndDirectoriesCreated(projectName)
      })

      it('generates project with default parameters when using --default flag', async () => {
        const parseConfigSpy = spy(Project, 'parseConfig')
        replace(ProjectInitializer, 'installDependencies', fake.returns({}))
        replace(fs,'mkdirs', fake.resolves({}))
        replace(fs,'outputFile', fake.resolves({}))
        replace(childProcessPromise, 'exec', fake.resolves({}))
        
        await new Project.default([projectName, '--default'], { version: '0.5.1' } as IConfig).run()

        expectFilesAndDirectoriesCreated(projectName)
        expect(parseConfigSpy).to.have.been.calledOnce
        expect(await parseConfigSpy.firstCall.returnValue).to.be.deep.equal(
          defaultProjectInitializerConfig as ProjectInitializerConfig
        )
  
        const expectedPackageJson = fs.readFileSync('./resources/commands/new_package.json').toString() 
        expect(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/package.json`,expectedPackageJson)
      })

      it('initializes git repository', async () => {
        replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))
        replace(ProjectInitializer, 'installDependencies', fake.returns({}))
        const initializeGitSpy = spy(ProjectInitializer, 'initializeGit')

        await new Project.default([projectName], {} as IConfig).run()

        expect(initializeGitSpy).to.have.been.calledOnce
        expect(fs.readdirSync(projectDirectory)).to.have.all.members(expectedDirectoryContent.rootPath)
        expect(fs.readdirSync(`${projectDirectory}/src`)).to.have.all.members(expectedDirectoryContent.src)
      })

      it('skips git repository initialization', async () => {
        replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))
        replace(ProjectInitializer, 'installDependencies', fake.returns({}))
        const initializeGitSpy = spy(ProjectInitializer, 'initializeGit')

        await new Project.default([projectName, '--skipGit'], {} as IConfig).run()

        expect(initializeGitSpy).to.not.have.been.calledOnce
        expect(fs.existsSync('.git')).to.be.false
        expect(fs.existsSync(projectDirectory)).to.be.true
      })
    })
  })
})
