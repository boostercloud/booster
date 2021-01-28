import { restore, replace, fake, spy } from 'sinon'
import { ProjectInitializerConfig } from '../../../src/services/project-initializer'
import { oraLogger } from '../../../src/services/logger'
import * as fs from 'fs-extra'
import * as childProcessPromise from 'child-process-promise'
import { IConfig } from '@oclif/config'
import { expect } from '../../expect'
import * as Project from '../../../src/commands/new/project'
import * as ProjectInitializer from '../../../src/services/project-initializer'

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

      beforeEach(() => {
        replace(fs, 'mkdirs', fake.resolves({}))
        replace(fs, 'outputFile', fake.resolves({}))
        replace(childProcessPromise, 'exec', fake.resolves({}))
        replace(oraLogger, 'info', fake.resolves({}))
        replace(oraLogger, 'start', fake.resolves({}))
        replace(oraLogger, 'succeed', fake.resolves({}))
      })

      afterEach(() => {
        restore()
      })

      describe('works properly', () => {
        it('without flags', async () => {
          replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))

          await new Project.default([projectName], {} as IConfig).run()

          expect(childProcessPromise.exec).to.have.been.calledWithMatch('git init && git add -A && git commit -m "Initial commit"')
          expect(childProcessPromise.exec).to.have.been.calledWithMatch('npm install')
          expect(oraLogger.info).to.have.been.calledWithMatch('Project generated!')
          expectFilesAndDirectoriesCreated(projectName)
        })

        it('skip dependencies installation with --skipInstall', async () => {
          replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))
          const installDependenciesSpy = spy(ProjectInitializer, 'installDependencies')

          await new Project.default([projectName, '--skipInstall'], {} as IConfig).run()

          expect(installDependenciesSpy).to.have.not.been.calledOnce
          expect(childProcessPromise.exec).to.have.not.been.calledWithMatch('npm install')
          expect(childProcessPromise.exec).to.have.been.calledWithMatch('git init && git add -A && git commit -m "Initial commit"')
          expect(oraLogger.info).to.have.been.calledWithMatch('Project generated!')
          expectFilesAndDirectoriesCreated(projectName)
        })

        it('generates project with default parameters when using --default flag', async () => {
          const parseConfigSpy = spy(Project, 'parseConfig')
          replace(ProjectInitializer, 'installDependencies', fake.returns({}))

          await new Project.default([projectName, '--default'], { version: '0.5.1' } as IConfig).run()

          expect(oraLogger.info).to.have.been.calledWithMatch('Project generated!')
          expectFilesAndDirectoriesCreated(projectName)
          expect(parseConfigSpy).to.have.been.calledOnce
          expect(await parseConfigSpy.firstCall.returnValue).to.be.deep.equal(
            defaultProjectInitializerConfig as ProjectInitializerConfig
          )

          const expectedPackageJson = fs.readFileSync('./test/fixtures/commands/new_package.json').toString() 
          expect(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/package.json`,expectedPackageJson)
        })

        it('skips git repository initialization with --skipGit', async () => {
          replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))
          const initializeGitSpy = spy(ProjectInitializer, 'initializeGit')

          await new Project.default([projectName, '--skipGit'], {} as IConfig).run()

          expect(oraLogger.info).to.have.been.calledWithMatch('Project generated!')
          expect(initializeGitSpy).to.not.have.been.calledOnce
          expect(childProcessPromise.exec).to.not.have.been.calledWithMatch('git init && git add -A && git commit -m "Initial commit"')
          expectFilesAndDirectoriesCreated(projectName)
        })

        describe('define homepage', () => { 
          it('with --homepage', async () => {
            replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))

            await new Project.default([projectName,'--homepage',"'booster.cloud'"], {} as IConfig).run()

            expect(childProcessPromise.exec).to.have.been.calledWithMatch('git init && git add -A && git commit -m "Initial commit"')
            expect(childProcessPromise.exec).to.have.been.calledWithMatch('npm install')
            expect(oraLogger.info).to.have.been.calledWithMatch('Project generated!')
            expectFilesAndDirectoriesCreated(projectName)
          })

          it('with -H', async () => {
            replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))

            await new Project.default([projectName,'-H',"'booster.cloud'"], {} as IConfig).run()

            expect(childProcessPromise.exec).to.have.been.calledWithMatch('git init && git add -A && git commit -m "Initial commit"')
            expect(childProcessPromise.exec).to.have.been.calledWithMatch('npm install')
            expect(oraLogger.info).to.have.been.calledWithMatch('Project generated!')
            expectFilesAndDirectoriesCreated(projectName)
          })
        })

        describe('define author', () => { 
          it('with --author', async () => {
            replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))

            await new Project.default([projectName,'--author',"'John Doe'"], {} as IConfig).run()

            expect(childProcessPromise.exec).to.have.been.calledWithMatch('git init && git add -A && git commit -m "Initial commit"')
            expect(childProcessPromise.exec).to.have.been.calledWithMatch('npm install')
            expect(oraLogger.info).to.have.been.calledWithMatch('Project generated!')
            expectFilesAndDirectoriesCreated(projectName)
          })

          it('with -a', async () => {
            replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))

            await new Project.default([projectName,'-a',"'John Doe'"], {} as IConfig).run()

            expect(childProcessPromise.exec).to.have.been.calledWithMatch('git init && git add -A && git commit -m "Initial commit"')
            expect(childProcessPromise.exec).to.have.been.calledWithMatch('npm install')
            expect(oraLogger.info).to.have.been.calledWithMatch('Project generated!')
            expectFilesAndDirectoriesCreated(projectName)
          })
        })

        describe('define description', () => { 
          it('with --description', async () => {
            replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))

            await new Project.default([projectName,'--description',"'a short description'"], {} as IConfig).run()

            expect(childProcessPromise.exec).to.have.been.calledWithMatch('git init && git add -A && git commit -m "Initial commit"')
            expect(childProcessPromise.exec).to.have.been.calledWithMatch('npm install')
            expect(oraLogger.info).to.have.been.calledWithMatch('Project generated!')
            expectFilesAndDirectoriesCreated(projectName)
          })

          it('with -d', async () => {
            replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))

            await new Project.default([projectName,'-d',"'a short description'"], {} as IConfig).run()

            expect(childProcessPromise.exec).to.have.been.calledWithMatch('git init && git add -A && git commit -m "Initial commit"')
            expect(childProcessPromise.exec).to.have.been.calledWithMatch('npm install')
            expect(oraLogger.info).to.have.been.calledWithMatch('Project generated!')
            expectFilesAndDirectoriesCreated(projectName)
          })
        })

        describe('define license', () => { 
          it('with --license', async () => {
            replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))

            await new Project.default([projectName,'--license','GPL'], {} as IConfig).run()

            expect(childProcessPromise.exec).to.have.been.calledWithMatch('git init && git add -A && git commit -m "Initial commit"')
            expect(childProcessPromise.exec).to.have.been.calledWithMatch('npm install')
            expect(oraLogger.info).to.have.been.calledWithMatch('Project generated!')
            expectFilesAndDirectoriesCreated(projectName)
          })

          it('with -l', async () => {
            replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))

            await new Project.default([projectName,'-l','GPL'], {} as IConfig).run()

            expect(childProcessPromise.exec).to.have.been.calledWithMatch('git init && git add -A && git commit -m "Initial commit"')
            expect(childProcessPromise.exec).to.have.been.calledWithMatch('npm install')
            expect(oraLogger.info).to.have.been.calledWithMatch('Project generated!')
            expectFilesAndDirectoriesCreated(projectName)
          })
        })

        describe('define provider', () => { 
          it('with --providerPackageName', async () => {
            replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))

            await new Project.default([projectName,'--providerPackageName',defaultProvider], {} as IConfig).run()

            expect(childProcessPromise.exec).to.have.been.calledWithMatch('git init && git add -A && git commit -m "Initial commit"')
            expect(childProcessPromise.exec).to.have.been.calledWithMatch('npm install')
            expect(oraLogger.info).to.have.been.calledWithMatch('Project generated!')
            expectFilesAndDirectoriesCreated(projectName)
          })

          it('with -p', async () => {
            replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))

            await new Project.default([projectName,'-p',defaultProvider], {} as IConfig).run()

            expect(childProcessPromise.exec).to.have.been.calledWithMatch('git init && git add -A && git commit -m "Initial commit"')
            expect(childProcessPromise.exec).to.have.been.calledWithMatch('npm install')
            expect(oraLogger.info).to.have.been.calledWithMatch('Project generated!')
            expectFilesAndDirectoriesCreated(projectName)
          })
        })

        describe('define repository', () => { 
          it('with --repository', async () => {
            replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))

            await new Project.default([projectName,'--repository',defaultRepository], {} as IConfig).run()

            expect(childProcessPromise.exec).to.have.been.calledWithMatch('git init && git add -A && git commit -m "Initial commit"')
            expect(childProcessPromise.exec).to.have.been.calledWithMatch('npm install')
            expect(oraLogger.info).to.have.been.calledWithMatch('Project generated!')
            expectFilesAndDirectoriesCreated(projectName)
          })

          it('with -r', async () => {
            replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))

            await new Project.default([projectName,'-r',defaultRepository], {} as IConfig).run()

            expect(childProcessPromise.exec).to.have.been.calledWithMatch('git init && git add -A && git commit -m "Initial commit"')
            expect(childProcessPromise.exec).to.have.been.calledWithMatch('npm install')
            expect(oraLogger.info).to.have.been.calledWithMatch('Project generated!')
            expectFilesAndDirectoriesCreated(projectName)
          })
        })

        describe('define version', () => { 
          it('with --version', async () => {
            replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))

            await new Project.default([projectName,'--version','1.0.0'], {} as IConfig).run()

            expect(childProcessPromise.exec).to.have.been.calledWithMatch('git init && git add -A && git commit -m "Initial commit"')
            expect(childProcessPromise.exec).to.have.been.calledWithMatch('npm install')
            expect(oraLogger.info).to.have.been.calledWithMatch('Project generated!')
            expectFilesAndDirectoriesCreated(projectName)
          })

          it('with -v', async () => {
            replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))

            await new Project.default([projectName,'-v','1.0.0'], {} as IConfig).run()

            expect(childProcessPromise.exec).to.have.been.calledWithMatch('git init && git add -A && git commit -m "Initial commit"')
            
            expect(childProcessPromise.exec).to.have.been.calledWithMatch('npm install')
            expect(oraLogger.info).to.have.been.calledWithMatch('Project generated!')
            expectFilesAndDirectoriesCreated(projectName)
          })
        })

        describe('define multiple flags', () => { 
            it('with all options (long flags)', async () => {
              await new Project.default([projectName,
                '--version','1.0.0',
                '--author',"'John Doe'",
                '--description',"'a new description'",
                '--homepage','booster.cloud',
                '--repository','github.com/boostercloud/booster.git',
                '--license','GPL',
                '--providerPackageName',defaultProvider,
                '--skipInstall',
                '--skipGit'
              ], {} as IConfig).run()
  
              expect(childProcessPromise.exec).to.have.not.been.calledWithMatch('git init && git add -A && git commit -m "Initial commit"')
              expect(childProcessPromise.exec).to.have.not.been.calledWithMatch('npm install')
              expect(oraLogger.info).to.have.been.calledWithMatch('Project generated!')
              expectFilesAndDirectoriesCreated(projectName)
            })

            it('with all options (short flags)', async () => {
                await new Project.default([projectName,
                  '-v','1.0.0',
                  '-a',"'John Doe'",
                  '-d',"'a new description'",
                  '-H','booster.cloud',
                  '-r','github.com/boostercloud/booster.git',
                  '-l','GPL',
                  '-p',defaultProvider,
                  '--skipInstall',
                  '--skipGit'
                ], {} as IConfig).run()
    
                expect(childProcessPromise.exec).to.have.not.been.calledWithMatch('git init && git add -A && git commit -m "Initial commit"')
                expect(childProcessPromise.exec).to.have.not.been.calledWithMatch('npm install')
                expect(oraLogger.info).to.have.been.calledWithMatch('Project generated!')
                expectFilesAndDirectoriesCreated(projectName)
            })
        })
      })

      describe('displays an error', () => {
        it('with empty project name', async () => {
          replace(console,'error', fake.resolves({}))
          await new Project.default([], {} as IConfig).run()
          expect(fs.mkdirs).to.have.not.been.calledWithMatch(`${projectName}/src`)
          expect(console.error).to.have.been.calledWith("You haven't provided a project name, but it is required, run with --help for usage")
          expect(oraLogger.info).to.have.not.been.calledWithMatch('Project generated!')
        })

        it('with nonexisting option', async () => {
            let exceptionThrown = false
            let exceptionMessage = ''
            try {
              await new Project.default([projectName,'--nonexistingoption'], {} as IConfig).run()
            } catch(e) {
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
              await new Project.default([projectName,'--homepage'], {} as IConfig).run()
            } catch(e) {
              exceptionThrown = true
              exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.be.equal('Flag --homepage expects a value')
          })

          it('with -H and no value', async () => {
            replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))

            let exceptionThrown = false
            let exceptionMessage = ''
            try {
              await new Project.default([projectName,'-H'], {} as IConfig).run()
            } catch(e) {
              exceptionThrown = true
              exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.be.equal('Flag --homepage expects a value')
          })
        })

        describe('define author badly', () => {
          it('with --author and no author', async () => {
            replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))

            let exceptionThrown = false
            let exceptionMessage = ''
            try {
              await new Project.default([projectName,'--author'], {} as IConfig).run()
            } catch(e) {
              exceptionThrown = true
              exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.be.equal('Flag --author expects a value')
          })

          it('with -a and no author', async () => {
            replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))

            let exceptionThrown = false
            let exceptionMessage = ''
            try {
              await new Project.default([projectName,'-a'], {} as IConfig).run()
            } catch(e) {
              exceptionThrown = true
              exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.be.equal('Flag --author expects a value')
          })
        })

        describe('define description badly', () => {
          it('with --description and no description', async () => {
            replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))

            let exceptionThrown = false
            let exceptionMessage = ''
            try {
              await new Project.default([projectName,'--description'], {} as IConfig).run()
            } catch(e) {
              exceptionThrown = true
              exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.be.equal('Flag --description expects a value')
          })

          it('with -d and no description', async () => {
            replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))

            let exceptionThrown = false
            let exceptionMessage = ''
            try {
              await new Project.default([projectName,'-d'], {} as IConfig).run()
            } catch(e) {
              exceptionThrown = true
              exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.be.equal('Flag --description expects a value')
          })
        })

        describe('define license badly', () => {
          it('with --license and no license name', async () => {
            replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))

            let exceptionThrown = false
            let exceptionMessage = ''
            try {
              await new Project.default([projectName,'--license'], {} as IConfig).run()
            } catch(e) {
              exceptionThrown = true
              exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.be.equal('Flag --license expects a value')
          })

          it('with -l and no license name', async () => {
            replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))

            let exceptionThrown = false
            let exceptionMessage = ''
            try {
              await new Project.default([projectName,'-l'], {} as IConfig).run()
            } catch(e) {
              exceptionThrown = true
              exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.be.equal('Flag --license expects a value')
          })
        })

        describe('define provider badly', () => {
          it('with --providerPackageName and no provider', async () => {
            replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))

            let exceptionThrown = false
            let exceptionMessage = ''
            try {
              await new Project.default([projectName,'--providerPackageName'], {} as IConfig).run()
            } catch(e) {
              exceptionThrown = true
              exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.be.equal('Flag --providerPackageName expects a value')
          })

          it('with -p and no provider', async () => {
            replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))

            let exceptionThrown = false
            let exceptionMessage = ''
            try {
              await new Project.default([projectName,'-p'], {} as IConfig).run()
            } catch(e) {
              exceptionThrown = true
              exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.be.equal('Flag --providerPackageName expects a value')
          })
        })

        describe('define repository badly', () => { 
          it('with --repository and no repository name', async () => {
            replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))

            let exceptionThrown = false
            let exceptionMessage = ''
            try {
              await new Project.default([projectName,'--repository'], {} as IConfig).run()
            } catch(e) {
              exceptionThrown = true
              exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.be.equal('Flag --repository expects a value')
          })

          it('with -r and no repository name', async () => {
            replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))

            let exceptionThrown = false
            let exceptionMessage = ''
            try {
              await new Project.default([projectName,'-r'], {} as IConfig).run()
            } catch(e) {
              exceptionThrown = true
              exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.be.equal('Flag --repository expects a value')
          })
        })

        describe('define version badly', () => { 
          it('with --version and no version number', async () => {
            replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))

            let exceptionThrown = false
            let exceptionMessage = ''
            try {
              await new Project.default([projectName,'--version'], {} as IConfig).run()
            } catch(e) {
              exceptionThrown = true
              exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.be.equal('Flag --version expects a value')
          })

          it('with -v and no version number', async () => {
            replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))

            let exceptionThrown = false
            let exceptionMessage = ''
            try {
              await new Project.default([projectName,'-v'], {} as IConfig).run()
            } catch(e) {
              exceptionThrown = true
              exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.be.equal('Flag --version expects a value')
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
              await new Project.default([projectName,'--providerPackageName','nonexistingProvider'], {} as IConfig).run()
            } catch(e) {
              exceptionThrown = true
              exceptionMessage = e.message
            }
            expect(exceptionThrown).to.be.equal(true)
            expect(exceptionMessage).to.be.equal('Flag --providerPackageName expects a value')
          })
        })

        describe('define repository badly', () => {
            xit('with --repository and invalid URL', async () => {
              replace(Project, 'parseConfig', fake.returns(defaultProjectInitializerConfig))
    
              let exceptionThrown = false
              let exceptionMessage = ''
              try {
                await new Project.default([projectName,'--repository','invalidUrl'], {} as IConfig).run()
              } catch(e) {
                exceptionThrown = true
                exceptionMessage = e.message
              }
              expect(exceptionThrown).to.be.equal(true)
              expect(exceptionMessage).to.be.equal('Flag --repository expects a url')
            })
          })
      })

      
    })
  })
})
