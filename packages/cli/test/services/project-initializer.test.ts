import * as fs from 'fs-extra'
import * as execa from 'execa'
import {
  generateConfigFiles,
  generateRootDirectory,
  initializeGit,
  installDependencies,
  ProjectInitializerConfig,
} from '../../src/services/project-initializer'
import { fake, replace, restore } from 'sinon'
import { expect } from '../expect'
import { makeTestPackageManager } from './package-manager/test.impl'
import * as PackageManager from '../../src/services/package-manager/live.impl'

describe('project initializer', (): void => {
  beforeEach(() => {
    replace(fs, 'mkdirs', fake.resolves({}))
    replace(fs, 'outputFile', fake.resolves({}))
    replace(execa, 'command', fake.resolves({ stdout: '', stderr: '' }))
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
    expect(execa.command).to.have.been.calledWithMatch('git init && git add -A && git commit -m "Initial commit"')
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
    expert(fs.mkdirs).to.have.been.calledWithMatch(`${projectName}/.vscode`)
  })

  it('install dependencies', async () => {
    const TestPackageManager = makeTestPackageManager()
    replace(PackageManager, 'LivePackageManager', TestPackageManager.layer)
    await installDependencies(defaultProjectInitializerConfig)
    expect(TestPackageManager.fakes.installAllDependencies).to.have.been.called
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
    expect(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/.mocharc.yml`)
    expert(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/.vscode/launch.json`)
  })
})
