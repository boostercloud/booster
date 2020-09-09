import * as Project from '../../src/commands/new/project'
import * as ProjectInitializer from '../../src/services/project-initializer'
import { IConfig } from '@oclif/config'
import * as fs from 'fs'
import { expect } from '../expect'
import { restore, replace, fake } from 'sinon'
import ErrnoException = NodeJS.ErrnoException
import { ProjectInitializerConfig } from '../../src/services/project-initializer'

describe('new', (): void => {
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
        repository: '@boostercloud/framework-provider-aws (AWS)',
        providerPackageName: '',
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
