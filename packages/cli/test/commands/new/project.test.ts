import { restore, replace, fake } from 'sinon'
import { ProjectInitializerConfig } from '../../../src/services/project-initializer'
import { oraLogger } from '../../../src/services/logger'
import * as fs from 'fs-extra'
import { IConfig } from '@oclif/config'
import { expect } from '../../expect'
import * as Project from '../../../src/commands/new/project'
import * as ProjectInitializer from '../../../src/services/project-initializer'
import * as packageJson from '../../../src/templates/project/package-json'
import * as Mustache from 'mustache'

describe('new', (): void => {
  describe('project', () => {
    context('default provider', () => {
      const projectName = 'test-project'
      const defaultProvider = '@boostercloud/framework-provider-aws'
      const defaultRepository = 'github.com:boostercloud/booster.git'

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
        expect(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/.mocharc.yml`)
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
        skipInstall: false,
        skipGit: false,
        interactive: false,
      } as ProjectInitializerConfig

      const renderPackageJson = (config: ProjectInitializerConfig): string => {
        return Mustache.render(packageJson.template, config)
      }

      beforeEach(() => {
        replace(fs, 'mkdirs', fake.resolves({}))
        replace(fs, 'outputFile', fake.resolves({}))
        replace(ProjectInitializer, 'initializeGit', fake.resolves({}))
        replace(ProjectInitializer, 'installDependencies', fake.resolves({}))
        replace(oraLogger, 'info', fake.resolves({}))
        replace(oraLogger, 'start', fake.resolves({}))
        replace(oraLogger, 'succeed', fake.resolves({}))
      })

      afterEach(() => {
        restore()
      })

      describe('works properly', () => {
        it('with the --interactive flag', async () => {
          replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))

          await new Project.default([projectName, '--interactive'], {} as IConfig).run()

          expect(ProjectInitializer.initializeGit).to.have.been.called
          expect(ProjectInitializer.installDependencies).to.have.been.called
          expect(oraLogger.info).to.have.been.calledWithMatch('Project generated!')
          expectFilesAndDirectoriesCreated(projectName)
        })

        it('skip dependencies installation with --skipInstall', async () => {
          replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))

          await new Project.default([projectName,  '--interactive', '--skipInstall'], {} as IConfig).run()

          expect(ProjectInitializer.installDependencies).to.have.not.been.called
          expect(ProjectInitializer.initializeGit).to.have.been.called
          expect(oraLogger.info).to.have.been.calledWithMatch('Project generated!')
          expectFilesAndDirectoriesCreated(projectName)
        })

        it('skips git repository initialization with --skipGit', async () => {
          replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))

          await new Project.default([projectName, '--interactive', '--skipGit'], {} as IConfig).run()

          expect(oraLogger.info).to.have.been.calledWithMatch('Project generated!')
          expect(ProjectInitializer.initializeGit).to.have.not.been.called
          expectFilesAndDirectoriesCreated(projectName)
          const expectedPackageJson = renderPackageJson(defaultProjectInitializerConfig)
          expect(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/package.json`, expectedPackageJson)
        })

        describe('define homepage', () => {
          it('with --homepage', async () => {
            const config = { ...defaultProjectInitializerConfig, homepage: 'booster.cloud' }
            replace(Project, 'parseConfig', fake.returns(config))

            await new Project.default([projectName, '--interactive', '--homepage', "'booster.cloud'"], {} as IConfig).run()

            expect(ProjectInitializer.initializeGit).to.have.been.called
            expect(ProjectInitializer.installDependencies).to.have.been.called
            expect(oraLogger.info).to.have.been.calledWithMatch('Project generated!')
            expectFilesAndDirectoriesCreated(projectName)
            expect(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/package.json`, renderPackageJson(config))
          })

          it('with -H', async () => {
            const config = { ...defaultProjectInitializerConfig, homepage: 'booster.cloud' }
            replace(Project, 'parseConfig', fake.returns(config))

            await new Project.default([projectName, '--interactive', '-H', "'booster.cloud'"], {} as IConfig).run()

            expect(ProjectInitializer.initializeGit).to.have.been.called
            expect(ProjectInitializer.installDependencies).to.have.been.called
            expect(oraLogger.info).to.have.been.calledWithMatch('Project generated!')
            expectFilesAndDirectoriesCreated(projectName)
            expect(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/package.json`, renderPackageJson(config))
          })
        })

        describe('define author', () => {
          it('with --author', async () => {
            const config = { ...defaultProjectInitializerConfig, author: 'John Doe' }
            replace(Project, 'parseConfig', fake.returns(config))

            await new Project.default([projectName, '--interactive', '--author', "'John Doe'"], {} as IConfig).run()

            expect(ProjectInitializer.initializeGit).to.have.been.called
            expect(ProjectInitializer.installDependencies).to.have.been.called
            expect(oraLogger.info).to.have.been.calledWithMatch('Project generated!')
            expectFilesAndDirectoriesCreated(projectName)
            expect(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/package.json`, renderPackageJson(config))
          })

          it('with -a', async () => {
            const config = { ...defaultProjectInitializerConfig, author: 'John Doe' }
            replace(Project, 'parseConfig', fake.returns(config))

            await new Project.default([projectName, '--interactive', '-a', "'John Doe'"], {} as IConfig).run()

            expect(ProjectInitializer.initializeGit).to.have.been.called
            expect(ProjectInitializer.installDependencies).to.have.been.called
            expect(oraLogger.info).to.have.been.calledWithMatch('Project generated!')
            expectFilesAndDirectoriesCreated(projectName)
            expect(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/package.json`, renderPackageJson(config))
          })
        })

        describe('define description', () => {
          it('with --description', async () => {
            const config = { ...defaultProjectInitializerConfig, description: 'a short description' }
            replace(Project, 'parseConfig', fake.returns(config))

            await new Project.default([projectName, '--interactive', '--description', "'a short description'"], {} as IConfig).run()

            expect(ProjectInitializer.initializeGit).to.have.been.called
            expect(ProjectInitializer.installDependencies).to.have.been.called
            expect(oraLogger.info).to.have.been.calledWithMatch('Project generated!')
            expectFilesAndDirectoriesCreated(projectName)
            expect(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/package.json`, renderPackageJson(config))
          })

          it('with -d', async () => {
            const config = { ...defaultProjectInitializerConfig, description: 'a short description' }
            replace(Project, 'parseConfig', fake.returns(config))

            await new Project.default([projectName, '--interactive', '-d', "'a short description'"], {} as IConfig).run()

            expect(ProjectInitializer.initializeGit).to.have.been.called
            expect(ProjectInitializer.installDependencies).to.have.been.called
            expect(oraLogger.info).to.have.been.calledWithMatch('Project generated!')
            expectFilesAndDirectoriesCreated(projectName)
            expect(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/package.json`, renderPackageJson(config))
          })
        })

        describe('define license', () => {
          it('with --license', async () => {
            const config = { ...defaultProjectInitializerConfig, license: 'GPL' }
            replace(Project, 'parseConfig', fake.returns(config))

            await new Project.default([projectName, '--interactive', '--license', 'GPL'], {} as IConfig).run()

            expect(ProjectInitializer.initializeGit).to.have.been.called
            expect(ProjectInitializer.installDependencies).to.have.been.called
            expect(oraLogger.info).to.have.been.calledWithMatch('Project generated!')
            expectFilesAndDirectoriesCreated(projectName)
            expect(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/package.json`, renderPackageJson(config))
          })

          it('with -l', async () => {
            const config = { ...defaultProjectInitializerConfig, license: 'GPL' }
            replace(Project, 'parseConfig', fake.returns(config))

            await new Project.default([projectName, '--interactive', '-l', 'GPL'], {} as IConfig).run()

            expect(ProjectInitializer.initializeGit).to.have.been.called
            expect(ProjectInitializer.installDependencies).to.have.been.called
            expect(oraLogger.info).to.have.been.calledWithMatch('Project generated!')
            expectFilesAndDirectoriesCreated(projectName)
            expect(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/package.json`, renderPackageJson(config))
          })
        })

        describe('define provider', () => {
          it('with --providerPackageName', async () => {
            replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))

            await new Project.default([projectName, '--providerPackageName', defaultProvider], {} as IConfig).run()

            expect(ProjectInitializer.initializeGit).to.have.been.called
            expect(ProjectInitializer.installDependencies).to.have.been.called
            expect(oraLogger.info).to.have.been.calledWithMatch('Project generated!')
            expectFilesAndDirectoriesCreated(projectName)
          })

          it('with -p', async () => {
            replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))

            await new Project.default([projectName, '-p', defaultProvider], {} as IConfig).run()

            expect(ProjectInitializer.initializeGit).to.have.been.called
            expect(ProjectInitializer.installDependencies).to.have.been.called
            expect(oraLogger.info).to.have.been.calledWithMatch('Project generated!')
            expectFilesAndDirectoriesCreated(projectName)
          })
        })

        describe('define repository', () => {
          it('with --repository', async () => {
            const config = { ...defaultProjectInitializerConfig, repository: defaultRepository }
            replace(Project, 'parseConfig', fake.returns(config))

            await new Project.default([projectName, '--interactive', '--repository', defaultRepository], {} as IConfig).run()

            expect(ProjectInitializer.initializeGit).to.have.been.called
            expect(ProjectInitializer.installDependencies).to.have.been.called
            expect(oraLogger.info).to.have.been.calledWithMatch('Project generated!')
            expectFilesAndDirectoriesCreated(projectName)
            expect(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/package.json`, renderPackageJson(config))
          })

          it('with -r', async () => {
            const config = { ...defaultProjectInitializerConfig, repository: defaultRepository }
            replace(Project, 'parseConfig', fake.returns(config))

            await new Project.default([projectName, '--interactive', '-r', defaultRepository], {} as IConfig).run()

            expect(ProjectInitializer.initializeGit).to.have.been.called
            expect(ProjectInitializer.installDependencies).to.have.been.called
            expect(oraLogger.info).to.have.been.calledWithMatch('Project generated!')
            expectFilesAndDirectoriesCreated(projectName)
            expect(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/package.json`, renderPackageJson(config))
          })
        })

        describe('define version', () => {
          it('with --version', async () => {
            const config = { ...defaultProjectInitializerConfig, version: '1.0.0' }
            replace(Project, 'parseConfig', fake.returns(config))

            await new Project.default([projectName, '--interactive', '--version', '1.0.0'], {} as IConfig).run()

            expect(ProjectInitializer.initializeGit).to.have.been.called
            expect(ProjectInitializer.installDependencies).to.have.been.called
            expect(oraLogger.info).to.have.been.calledWithMatch('Project generated!')
            expectFilesAndDirectoriesCreated(projectName)
            expect(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/package.json`, renderPackageJson(config))
          })

          it('with -v', async () => {
            const config = { ...defaultProjectInitializerConfig, version: '1.0.0' }
            replace(Project, 'parseConfig', fake.returns(config))

            await new Project.default([projectName, '--interactive', '-v', '1.0.0'], {} as IConfig).run()

            expect(ProjectInitializer.initializeGit).to.have.been.called
            expect(ProjectInitializer.installDependencies).to.have.been.called
            expect(oraLogger.info).to.have.been.calledWithMatch('Project generated!')
            expectFilesAndDirectoriesCreated(projectName)
            expect(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/package.json`, renderPackageJson(config))
          })
        })

        describe('define multiple flags', () => {
          it('with all options (long flags)', async () => {
            await new Project.default(
              [
                projectName,
                '--version',
                '1.0.0',
                '--author',
                "'John Doe'",
                '--description',
                "'a new description'",
                '--homepage',
                'booster.cloud',
                '--repository',
                'github.com/boostercloud/booster.git',
                '--license',
                'GPL',
                '--providerPackageName',
                defaultProvider,
                '--skipInstall',
                '--skipGit',
              ],
              {} as IConfig
            ).run()

            expect(ProjectInitializer.initializeGit).to.have.not.been.called
            expect(ProjectInitializer.installDependencies).to.have.not.been.called
            expect(oraLogger.info).to.have.been.calledWithMatch('Project generated!')
            expectFilesAndDirectoriesCreated(projectName)
          })

          it('with all options (short flags)', async () => {
            await new Project.default(
              [
                projectName,
                '-v',
                '1.0.0',
                '-a',
                "'John Doe'",
                '-d',
                "'a new description'",
                '-H',
                'booster.cloud',
                '-r',
                'github.com/boostercloud/booster.git',
                '-l',
                'GPL',
                '-p',
                defaultProvider,
                '--skipInstall',
                '--skipGit',
              ],
              {} as IConfig
            ).run()

            expect(ProjectInitializer.initializeGit).to.have.not.been.called
            expect(ProjectInitializer.installDependencies).to.have.not.been.called
            expect(oraLogger.info).to.have.been.calledWithMatch('Project generated!')
            expectFilesAndDirectoriesCreated(projectName)
          })
        })
      })

      describe('displays an error', () => {
        it('with empty project name', async () => {
          replace(console, 'error', fake.resolves({}))
          await new Project.default([], {} as IConfig).run()
          expect(fs.mkdirs).to.have.not.been.calledWithMatch(`${projectName}/src`)
          expect(console.error).to.have.been.calledWithMatch(/You haven't provided a project name/)
          expect(oraLogger.info).to.have.not.been.calledWithMatch('Project generated!')
        })

        it('with neither the interactive flag, not provider package name', async () => {
          replace(console, 'error', fake.resolves({}))
          await new Project.default([projectName], {} as IConfig).run()
          expect(fs.mkdirs).to.have.not.been.calledWithMatch(`${projectName}/src`)
          expect(console.error).to.have.been.calledWithMatch(
            /You must set a provider runtime package using the --provider flag or use the interactive mode with the --interactive flag./
          )
          expect(oraLogger.info).to.have.not.been.calledWithMatch('Project generated!')
        })

        it('with nonexisting option', async () => {
          let exceptionThrown = false
          let exceptionMessage = ''
          try {
            await new Project.default([projectName, '--nonexistingoption'], {} as IConfig).run()
          } catch (e) {
            exceptionThrown = true
            exceptionMessage = e.message
          }
          expect(exceptionThrown).to.be.equal(true)
          expect(exceptionMessage).to.contain('Unexpected argument: --nonexistingoption')
          expect(oraLogger.info).to.have.not.been.calledWithMatch('Project generated!')
          expect(fs.mkdirs).to.have.not.been.calledWithMatch(`${projectName}/src`)
        })

        describe('define homepage badly', () => {
          it('with --homepage and no value', async () => {
            replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))

            let exceptionThrown = false
            let exceptionMessage = ''
            try {
              await new Project.default([projectName, '--homepage'], {} as IConfig).run()
            } catch (e) {
              exceptionThrown = true
              exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.contain('--homepage expects a value')
          })

          it('with -H and no value', async () => {
            replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))

            let exceptionThrown = false
            let exceptionMessage = ''
            try {
              await new Project.default([projectName, '-H'], {} as IConfig).run()
            } catch (e) {
              exceptionThrown = true
              exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.contain('--homepage expects a value')
          })
        })

        describe('define author badly', () => {
          it('with --author and no author', async () => {
            replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))

            let exceptionThrown = false
            let exceptionMessage = ''
            try {
              await new Project.default([projectName, '--author'], {} as IConfig).run()
            } catch (e) {
              exceptionThrown = true
              exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.contain('--author expects a value')
          })

          it('with -a and no author', async () => {
            replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))

            let exceptionThrown = false
            let exceptionMessage = ''
            try {
              await new Project.default([projectName, '-a'], {} as IConfig).run()
            } catch (e) {
              exceptionThrown = true
              exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.contain('--author expects a value')
          })
        })

        describe('define description badly', () => {
          it('with --description and no description', async () => {
            replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))

            let exceptionThrown = false
            let exceptionMessage = ''
            try {
              await new Project.default([projectName, '--description'], {} as IConfig).run()
            } catch (e) {
              exceptionThrown = true
              exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.contain('--description expects a value')
          })

          it('with -d and no description', async () => {
            replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))

            let exceptionThrown = false
            let exceptionMessage = ''
            try {
              await new Project.default([projectName, '-d'], {} as IConfig).run()
            } catch (e) {
              exceptionThrown = true
              exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.contain('--description expects a value')
          })
        })

        describe('define license badly', () => {
          it('with --license and no license name', async () => {
            replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))

            let exceptionThrown = false
            let exceptionMessage = ''
            try {
              await new Project.default([projectName, '--license'], {} as IConfig).run()
            } catch (e) {
              exceptionThrown = true
              exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.contain('--license expects a value')
          })

          it('with -l and no license name', async () => {
            replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))

            let exceptionThrown = false
            let exceptionMessage = ''
            try {
              await new Project.default([projectName, '-l'], {} as IConfig).run()
            } catch (e) {
              exceptionThrown = true
              exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.contain('--license expects a value')
          })
        })

        describe('define provider badly', () => {
          it('with --providerPackageName and no provider', async () => {
            replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))

            let exceptionThrown = false
            let exceptionMessage = ''
            try {
              await new Project.default([projectName, '--providerPackageName'], {} as IConfig).run()
            } catch (e) {
              exceptionThrown = true
              exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.contain('--providerPackageName expects a value')
          })

          it('with -p and no provider', async () => {
            replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))

            let exceptionThrown = false
            let exceptionMessage = ''
            try {
              await new Project.default([projectName, '-p'], {} as IConfig).run()
            } catch (e) {
              exceptionThrown = true
              exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.contain('--providerPackageName expects a value')
          })
        })

        describe('define repository badly', () => {
          it('with --repository and no repository name', async () => {
            replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))

            let exceptionThrown = false
            let exceptionMessage = ''
            try {
              await new Project.default([projectName, '--repository'], {} as IConfig).run()
            } catch (e) {
              exceptionThrown = true
              exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.contain('--repository expects a value')
          })

          it('with -r and no repository name', async () => {
            replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))

            let exceptionThrown = false
            let exceptionMessage = ''
            try {
              await new Project.default([projectName, '-r'], {} as IConfig).run()
            } catch (e) {
              exceptionThrown = true
              exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.contain('--repository expects a value')
          })
        })

        describe('define version badly', () => {
          it('with --version and no version number', async () => {
            replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))

            let exceptionThrown = false
            let exceptionMessage = ''
            try {
              await new Project.default([projectName, '--version'], {} as IConfig).run()
            } catch (e) {
              exceptionThrown = true
              exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.contain('--version expects a value')
          })

          it('with -v and no version number', async () => {
            replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))

            let exceptionThrown = false
            let exceptionMessage = ''
            try {
              await new Project.default([projectName, '-v'], {} as IConfig).run()
            } catch (e) {
              exceptionThrown = true
              exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.contain('--version expects a value')
          })
        })
      })

      xdescribe('should display an error but is not validated', () => {
        describe('define provider badly', () => {
          xit('with --providerPackageName and an an noneexisting provider', async () => {
            replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))

            let exceptionThrown = false
            let exceptionMessage = ''
            try {
              await new Project.default(
                [projectName, '--providerPackageName', 'nonexistingProvider'],
                {} as IConfig
              ).run()
            } catch (e) {
              exceptionThrown = true
              exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.contain('--providerPackageName expects a value')
          })
        })

        describe('define repository badly', () => {
          xit('with --repository and invalid URL', async () => {
            replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))

            let exceptionThrown = false
            let exceptionMessage = ''
            try {
              await new Project.default([projectName, '--repository', 'invalidUrl'], {} as IConfig).run()
            } catch (e) {
              exceptionThrown = true
              exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.contain('--repository expects a url')
          })
        })
      })
    })
  })
})
