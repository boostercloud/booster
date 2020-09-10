import { ProjectInitializerConfig } from '../../src/services/project-initializer'
import * as fs from 'fs'
import { restore, replace, fake, spy } from 'sinon'
import ErrnoException = NodeJS.ErrnoException
import * as ProjectInitializer from '../../src/services/project-initializer'
import * as Project from '../../src/commands/new/project'
import { IConfig } from '@oclif/config'
import { expect } from '../expect'
import sinon = require('sinon')

describe('new', (): void => {
  describe('project', () => {
    context('file generation', () => {
      const projectName = 'test-project'
      const projectDirectory = `./${projectName}`
      const defaultProvider = '@boostercloud/framework-provider-aws'

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

      it('generates project with default parameters when using --default flag', async () => {
        replace(ProjectInitializer, 'installDependencies', fake.returns({}))
        expect(fs.existsSync(projectDirectory)).to.be.false
        const parseConfigSpy = spy(Project, 'parseConfig')

        await new Project.default([projectName, '--default'], {} as IConfig).run()
        // TODO: check spy (not checking returned value properly) and booster version issue
        expect(fs.existsSync(projectDirectory)).to.be.true
        expect(parseConfigSpy).to.have.returned(
          sinon.match.same(
            Promise.resolve({
              projectName: projectName,
              providerPackageName: defaultProvider,
              description: '',
              version: '0.1.0',
              author: '',
              homepage: '',
              license: 'MIT',
              repository: '',
              boosterVersion: '0.5.1',
              default: true,
            })
          )
        ).and.to.have.been.calledOnce

        const packageJson = JSON.parse(fs.readFileSync(`${projectDirectory}/package.json`).toString())
        expect(packageJson.name).to.be.equal(projectName)
        expect(packageJson.dependencies[defaultProvider]).to.not.be.undefined
        expect(packageJson.dependencies[defaultProvider]).to.be.equal('*')
        expect(packageJson.description).to.be.equal('')
        expect(packageJson.version).to.be.equal('0.1.0')
        expect(packageJson.author).to.be.equal('')
        expect(packageJson.homepage).to.be.equal('')
        expect(packageJson.license).to.be.equal('MIT')
        expect(packageJson.repository).to.be.equal('')
        expect(packageJson.boosterVersion).to.be.equal('0.5.1')
      })
    })
  })
})
