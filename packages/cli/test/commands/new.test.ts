import * as ProjectChecker from '../../src/services/project-checker'
import { restore, replace, fake, stub, spy } from 'sinon'
import ErrnoException = NodeJS.ErrnoException
import { ProjectInitializerConfig } from '../../src/services/project-initializer'
import ReadModel from '../../src/commands/new/read-model'
import { templates } from '../../src/templates'
import Mustache = require('mustache')
import * as fs from 'fs'
import { IConfig } from '@oclif/config'
import { expect } from '../expect'
import * as Project from '../../src/commands/new/project'
import * as ProjectInitializer from '../../src/services/project-initializer'
import * as dependencies from '../../src/services/dependencies'

describe('new', (): void => {
  describe('Read model', () => {
    const readModel = 'example-read-model'
    const readModelsRoot = './src/read-models/'
    const readModelPath = `${readModelsRoot}${readModel}.ts`
    afterEach(() => {
      if (fs.existsSync(readModelPath)) {
        fs.rmdir(readModelsRoot, { recursive: true }, (err: ErrnoException | null) => console.log(err))
      }
      restore()
    })
    context('projections', () => {
      it('renders according to the template', async () => {
        stub(ProjectChecker, 'checkItIsABoosterProject').returnsThis()
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
        fs.rmdirSync(projectDirectory, { recursive: true })
        restore()
      })

      it('generates all required files and folders', async () => {
        replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))
        replace(dependencies, 'installDependencies', fake.returns({}))
        expect(fs.existsSync(projectDirectory)).to.be.false

        await new Project.default([projectName], {} as IConfig).run()

        expect(fs.existsSync(projectDirectory)).to.be.true
        expect(fs.readdirSync(projectDirectory)).to.have.all.members(expectedDirectoryContent.rootPath)
        expect(fs.readdirSync(`${projectDirectory}/src`)).to.have.all.members(expectedDirectoryContent.src)
      })

      it('generates all required files and folders without installing dependencies', async () => {
        replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))
        const installDependenciesSpy = spy(dependencies, 'installDependencies')
        expect(fs.existsSync(projectDirectory)).to.be.false

        await new Project.default([projectName, '--skipInstall'], {} as IConfig).run()

        expect(fs.existsSync(projectDirectory)).to.be.true
        expect(installDependenciesSpy).to.have.not.been.calledOnce
        expect(fs.readdirSync(projectDirectory)).to.have.all.members(expectedDirectoryContent.rootPath)
        expect(fs.readdirSync(`${projectDirectory}/src`)).to.have.all.members(expectedDirectoryContent.src)
      })

      it('generates project with default parameters when using --default flag', async () => {
        const parseConfigSpy = spy(Project, 'parseConfig')
        replace(dependencies, 'installDependencies', fake.returns({}))
        expect(fs.existsSync(projectDirectory)).to.be.false

        await new Project.default([projectName, '--default'], { version: '0.5.1' } as IConfig).run()

        expect(fs.existsSync(projectDirectory)).to.be.true
        expect(parseConfigSpy).to.have.been.calledOnce
        expect(await parseConfigSpy.firstCall.returnValue).to.be.deep.equal(
          defaultProjectInitializerConfig as ProjectInitializerConfig
        )

        const packageJson = JSON.parse(fs.readFileSync(`${projectDirectory}/package.json`).toString())
        expect(packageJson.name).to.be.equal(projectName)
        expect(packageJson.dependencies[defaultProvider]).to.be.equal('*')
        expect(packageJson.description).to.be.equal('')
        expect(packageJson.version).to.be.equal('0.1.0')
        expect(packageJson.author).to.be.equal('')
        expect(packageJson.homepage).to.be.equal('')
        expect(packageJson.license).to.be.equal('MIT')
        expect(packageJson.repository).to.be.equal('')
      })

      it('initializes git repository', async () => {
        replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))
        replace(dependencies, 'installDependencies', fake.returns({}))
        const initializeGitSpy = spy(ProjectInitializer, 'initializeGit')

        await new Project.default([projectName], {} as IConfig).run()

        expect(initializeGitSpy).to.have.been.calledOnce
        expect(fs.readdirSync(projectDirectory)).to.have.all.members(expectedDirectoryContent.rootPath)
        expect(fs.readdirSync(`${projectDirectory}/src`)).to.have.all.members(expectedDirectoryContent.src)
      })

      it('skips git repository initialization', async () => {
        replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))
        replace(dependencies, 'installDependencies', fake.returns({}))
        const initializeGitSpy = spy(ProjectInitializer, 'initializeGit')

        await new Project.default([projectName, '--skipGit'], {} as IConfig).run()

        expect(initializeGitSpy).to.not.have.been.calledOnce
        expect(fs.existsSync('.git')).to.be.false
        expect(fs.existsSync(projectDirectory)).to.be.true
      })
    })
  })
})
