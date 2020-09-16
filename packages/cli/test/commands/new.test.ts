import * as ProjectChecker from '../../src/services/project-checker'
import * as Project from '../../src/commands/new/project'
import * as ProjectInitializer from '../../src/services/project-initializer'
import { IConfig } from '@oclif/config'
import * as fs from 'fs'
import { expect } from '../expect'
import { restore, replace, fake, stub } from 'sinon'
import ErrnoException = NodeJS.ErrnoException
import { ProjectInitializerConfig } from '../../src/services/project-initializer'
import ReadModel from '../../src/commands/new/read-model'
import { templates } from '../../src/templates'
import Mustache = require('mustache')

describe('new', (): void => {
  describe('Read model', () => {
    const readModel = 'ExampleReadModel'
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
              commaSeparatedComponents: 'UUID, ReadModelAction',
            },
            {
              packagePath: '../entities/Post',
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
      const expectedDirectoryContent = {
        rootPath: [
          '.eslintignore',
          '.eslintrc.js',
          '.gitignore',
          '.prettierrc.yaml',
          'package.json',
          'src',
          'tsconfig.eslint.json',
          'tsconfig.json',
        ],
        src: ['commands', 'events', 'event-handlers', 'entities', 'read-models', 'config', 'common', 'index.ts'],
      }
      const projectInitializerConfig = {
        projectName: projectName,
        description: '0.1.0',
        version: '0.1.0',
        author: 'superAuthor',
        homepage: '',
        license: 'MIT',
        repository: '',
        providerPackageName: '@boostercloud/framework-provider-aws',
        boosterVersion: '0.5.1',
      } as ProjectInitializerConfig

      afterEach(() => {
        fs.rmdir(projectDirectory, { recursive: true }, (e: ErrnoException | null) => console.error(e))
        restore()
      })

      it('generates all required files and folders', async () => {
        replace(Project, 'parseConfig', fake.returns(projectInitializerConfig))
        replace(ProjectInitializer, 'installDependencies', fake.returns({}))
        expect(fs.existsSync(projectDirectory)).to.be.false

        await new Project.default([projectName], {} as IConfig).run()

        expect(fs.existsSync(projectDirectory)).to.be.true
        expect(fs.readdirSync(projectDirectory)).to.have.all.members(expectedDirectoryContent.rootPath)
        expect(fs.readdirSync(`${projectDirectory}/src`)).to.have.all.members(expectedDirectoryContent.src)
      })
    })
  })
})
