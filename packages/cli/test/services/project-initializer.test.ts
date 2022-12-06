import * as fs from 'fs-extra'
import * as childProcessPromise from 'child-process-promise'
import {
  generateConfigFiles,
  installDependencies,
  generateRootDirectory,
  initializeGit,
  ProjectInitializerConfig,
} from '../../src/services/project-initializer'
import { restore, replace, fake } from 'sinon'
import { expect } from '../expect'
import { makeTestPackageManager } from './package-manager/test.impl'
import * as PackageManager from '../../src/services/package-manager/live.impl'
import * as path from 'path'

describe('project initializer', (): void => {
  beforeEach(() => {
    replace(fs, 'mkdirs', fake.resolves({}))
    replace(fs, 'outputFile', fake.resolves({}))
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
    expect(childProcessPromise.exec).to.have.been.calledWithMatch(
      'git init && git add -A && git commit -m "Initial commit"'
    )
  })

  it('Generate root directory', async () => {
    await generateRootDirectory(defaultProjectInitializerConfig)
    expect(fs.mkdirs).to.have.been.calledWithMatch(path.join(`${projectName}/src/commands`))
    expect(fs.mkdirs).to.have.been.calledWithMatch(path.join(`${projectName}/src/events`))
    expect(fs.mkdirs).to.have.been.calledWithMatch(path.join(`${projectName}/src/entities`))
    expect(fs.mkdirs).to.have.been.calledWithMatch(path.join(`${projectName}/src/read-models`))
    expect(fs.mkdirs).to.have.been.calledWithMatch(path.join(`${projectName}/src/config`))
    expect(fs.mkdirs).to.have.been.calledWithMatch(path.join(`${projectName}/src/common`))
    expect(fs.mkdirs).to.have.been.calledWithMatch(path.join(`${projectName}/src/event-handlers`))
    expect(fs.mkdirs).to.have.been.calledWithMatch(path.join(`${projectName}/src/scheduled-commands`))
  })

  it('install dependencies', async () => {
    const TestPackageManager = makeTestPackageManager()
    replace(PackageManager, 'LivePackageManager', TestPackageManager.layer)
    await installDependencies(defaultProjectInitializerConfig)
    expect(TestPackageManager.fakes.installAllDependencies).to.have.been.called
  })

  it('Generate config files', async () => {
    await generateConfigFiles(defaultProjectInitializerConfig)
    expect(fs.outputFile).to.have.been.calledWithMatch(path.join(`${projectName}/.eslintignore`))
    expect(fs.outputFile).to.have.been.calledWithMatch(path.join(`${projectName}/.eslintrc.js`))
    expect(fs.outputFile).to.have.been.calledWithMatch(path.join(`${projectName}/.gitignore`))
    expect(fs.outputFile).to.have.been.calledWithMatch(path.join(`${projectName}/package.json`))
    expect(fs.outputFile).to.have.been.calledWithMatch(path.join(`${projectName}/tsconfig.json`))
    expect(fs.outputFile).to.have.been.calledWithMatch(path.join(`${projectName}/tsconfig.eslint.json`))
    expect(fs.outputFile).to.have.been.calledWithMatch(path.join(`${projectName}/.prettierrc.yaml`))
    expect(fs.outputFile).to.have.been.calledWithMatch(path.join(`${projectName}/src/config/config.ts`))
    expect(fs.outputFile).to.have.been.calledWithMatch(path.join(`${projectName}/src/index.ts`))
    expect(fs.outputFile).to.have.been.calledWithMatch(path.join(`${projectName}/.mocharc.yml`))
  })
})
