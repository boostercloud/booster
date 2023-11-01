import * as path from 'path'
import { exec } from 'child-process-promise'
import {
  createFolder,
  dirContents,
  fileExists,
  loadFixture,
  readFileContent,
  removeFolders,
} from '../../helper/file-helper'
import { ChildProcess } from 'child_process'
import { overrideWithBoosterLocalDependencies } from '../../helper/deps-helper'
import { expect } from '../../helper/expect'

// The Booster CLI version used should match the integration tests' version
const BOOSTER_VERSION = require('../../../package.json').version

const TEST_TIMEOUT = 80000
const DESCRIPTION = 'cart-demo'
const VERSION = '1.0.0'
const AUTHOR = 'The Agile Monkeys'
const HOMEPAGE = 'https://www.boosterframework.com/'
const LICENSE = 'Apache'
const REPO_URL = 'https://github.com/boostercloud/booster/'
const PROVIDER = '@boostercloud/framework-provider-aws'

describe('Project', () => {
  const SANDBOX_INTEGRATION_DIR = 'new-project-integration-sandbox'

  before(async () => {
    // Required by Github actions CI/CD, because it doesn't have git configured
    await exec('git config --global user.name || git config --global user.name "Booster Test"')
    await exec('git config --global user.email || git config --global user.email "test@booster.cloud"')

    createFolder(SANDBOX_INTEGRATION_DIR)
  })

  after(async () => {
    await removeFolders([SANDBOX_INTEGRATION_DIR])
  })

  const cliPath = path.join('..', 'node_modules', '.bin', 'boost')
  const expectedOutputRegex = new RegExp(
    [
      'boost new',
      'Creating project root',
      'Generating config files',
      'Installing dependencies',
      'Initializing git repository',
      'Project generated',
    ].join('(.|\n)*'),
    'm'
  )

  interface PromptAnswers {
    description?: string
    version?: string
    author?: string
    website?: string
    license?: string
    repository?: string
    provider?: 'default' | string
  }

  const DOWN_KEY = '\u001b[B'
  const INTRO = '\r\n'

  const handlePrompt = async (cliProcess: ChildProcess, answers: PromptAnswers): Promise<void> => {
    const writtenAnswers: string[] = []
    return new Promise((resolve, reject) => {
      if (cliProcess.stdout) {
        cliProcess.stdout.on('data', (data) => {
          for (const prompt in answers) {
            if (!writtenAnswers.includes(prompt) && new RegExp(prompt).test(data)) {
              // We need to handle the provider selector
              if (prompt == 'provider') {
                if (answers[prompt as keyof PromptAnswers] == 'default') {
                  // Just "hit intro" to select the first option
                  cliProcess.stdin?.write(INTRO)
                  break
                } else {
                  // "Push" the "down arrow" to show the prompt
                  cliProcess.stdin?.write(DOWN_KEY)
                  cliProcess.stdin?.write(answers[prompt as keyof PromptAnswers] + INTRO)
                }
              } else {
                cliProcess.stdin?.write(answers[prompt as keyof PromptAnswers] + INTRO)
              }
              writtenAnswers.push(prompt)
              break
            }
          }
        })

        cliProcess.stdout.on('end', () => {
          cliProcess.stdin?.end()
          resolve()
        })

        cliProcess.stdout.on('error', () => {
          cliProcess.stdin?.end()
          reject()
        })
      } else {
        reject('Unable to read CLI prompt.')
      }
    })
  }

  const execNewProject = async (
    projectName: string,
    flags: Array<string> = [],
    promptAnswers?: PromptAnswers
  ): Promise<{ stdout: string; stderr: string }> => {
    const cliProcess = exec(`${cliPath} new:project ${projectName} ${flags.join(' ')}`, {
      cwd: SANDBOX_INTEGRATION_DIR,
    })

    if (promptAnswers) {
      await handlePrompt(cliProcess.childProcess, promptAnswers)
    }

    const { stdout, stderr } = await cliProcess
    return { stdout, stderr }
  }

  const packageJsonAssertions = (
    expectedJson: string,
    jsonContent: string,
    objectsToCompareJustKeys: Array<string>,
    checkKeysAndValues = true
  ): void => {
    const expectedJsonObj = JSON.parse(expectedJson)
    const jsonContentObj = JSON.parse(jsonContent)

    Object.entries(expectedJsonObj).forEach(([key]) => {
      if (objectsToCompareJustKeys.includes(key)) {
        expect(Object.prototype.hasOwnProperty.call(jsonContentObj, key)).true
        return packageJsonAssertions(
          JSON.stringify(expectedJsonObj[key]),
          JSON.stringify(jsonContentObj[key]),
          objectsToCompareJustKeys,
          false
        )
      } else {
        checkKeysAndValues
          ? expect(jsonContentObj[key]).to.deep.equals(expectedJsonObj[key])
          : expect(jsonContentObj).to.haveOwnProperty(key)
      }
    })
  }

  const projectPath = (projectName: string, fileName = ''): string =>
    path.join(SANDBOX_INTEGRATION_DIR, projectName, fileName)

  const projectFileExists = (projectName: string, fileName: string): boolean =>
    fileExists(projectPath(projectName, fileName))

  const projectFileContents = (projectName: string, fileName: string): string =>
    readFileContent(projectPath(projectName, fileName))

  const projectDirContents = (projectName: string, dirName: string): Array<string> =>
    dirContents(projectPath(projectName, dirName))

  const assertions = async (
    output: { stdout: string; stderr: string },
    projectName: string,
    flags?: string[]
  ): Promise<void> => {
    const { stdout, stderr } = output
    const fileContents = projectFileContents.bind(null, projectName)
    const dirContents = projectDirContents.bind(null, projectName)

    expect(stderr).to.be.empty
    expect(stdout).to.match(expectedOutputRegex)

    expect(dirContents('/src/commands')).is.empty
    expect(dirContents('/src/common')).is.empty
    expect(dirContents('/src/config').length).equals(1)
    expect(dirContents('/src/entities')).is.empty
    expect(dirContents('/src/events')).is.empty

    const expectedCartDemoConfig = loadFixture('cart-demo/src/config/config.ts', [
      ['project_name_placeholder', projectName],
    ])
    const cartDemoConfigContent = fileContents('src/config/config.ts')
    expect(cartDemoConfigContent).to.equal(expectedCartDemoConfig)

    const expectedCartDemoIndex = loadFixture('cart-demo/src/index.ts')
    const cartDemoIndexContent = fileContents('src/index.ts')
    expect(cartDemoIndexContent).to.equal(expectedCartDemoIndex)

    const expectedCartDemoEslintIgnore = loadFixture('cart-demo/.eslintignore')
    const cartDemoEslintIgnoreContent = fileContents('.eslintignore')
    expect(cartDemoEslintIgnoreContent).to.equal(expectedCartDemoEslintIgnore)

    const expectedCartDemoEslintRc = loadFixture('cart-demo/.eslintrc.js')
    const cartDemoEslintRcContent = fileContents('.eslintrc.js')
    expect(cartDemoEslintRcContent).to.equal(expectedCartDemoEslintRc)

    const expectedCartDemoGitIgnore = loadFixture('cart-demo/.gitignore')
    const cartDemoGitIgnoreContent = fileContents('.gitignore')
    expect(cartDemoGitIgnoreContent).to.equal(expectedCartDemoGitIgnore)

    const expectedCartDemoPretierRc = loadFixture('cart-demo/.prettierrc.yaml')
    const cartDemoPretierRcContent = fileContents('.prettierrc.yaml')
    expect(cartDemoPretierRcContent).to.equal(expectedCartDemoPretierRc)

    const expectedCartDemoMochaRc = loadFixture('cart-demo/.mocharc.yml')
    const cartDemoMochaRcContent = fileContents('.mocharc.yml')
    expect(cartDemoMochaRcContent).to.equal(expectedCartDemoMochaRc)

    const defaults = flags?.includes('--default')
    const expectedCartDemoPackageJson = loadFixture('cart-demo/package.json', [
      ['project_name_placeholder', projectName],
      ['description_placeholder', defaults ? '' : DESCRIPTION],
      ['version_placeholder', defaults ? '0.1.0' : VERSION],
      ['author_placeholder', defaults ? '' : AUTHOR],
      ['homepage_placeholder', defaults ? '' : HOMEPAGE],
      ['license_placeholder', defaults ? 'MIT' : LICENSE],
      ['repository_placeholder', defaults ? '' : REPO_URL],
    ])
    const cartDemoPackageJsonContent = fileContents('package.json')
    packageJsonAssertions(expectedCartDemoPackageJson, cartDemoPackageJsonContent, ['dependencies', 'devDependencies'])
    const cartDemoPackageJsonObject = JSON.parse(cartDemoPackageJsonContent)
    expect(cartDemoPackageJsonObject['dependencies']['@boostercloud/framework-core']).to.equal(`^${BOOSTER_VERSION}`)
    expect(cartDemoPackageJsonObject['dependencies']['@boostercloud/framework-types']).to.equal(`^${BOOSTER_VERSION}`)

    const expectedCartDemoTsConfigEslint = loadFixture('cart-demo/tsconfig.eslint.json')
    const cartDemoTsConfigEslintContent = fileContents('tsconfig.eslint.json')
    expect(cartDemoTsConfigEslintContent).to.equal(expectedCartDemoTsConfigEslint)

    const expectedCartDemoTsConfig = loadFixture('cart-demo/tsconfig.json')
    const cartDemoTsConfigContent = fileContents('tsconfig.json')
    expect(cartDemoTsConfigContent).to.equal(expectedCartDemoTsConfig)
  }

  context('Valid project', () => {
    describe('using flags', () => {
      it('should create a new project using short flags to configure it', async () => {
        const projectName = 'cart-demo-short-flags'
        const flags = [
          `-a "${AUTHOR}"`,
          `-d "${DESCRIPTION}"`,
          `-H "${HOMEPAGE}"`,
          `-l "${LICENSE}"`,
          `-p "${PROVIDER}"`,
          `-r "${REPO_URL}"`,
          `-v "${VERSION}"`,
          // We skip dependencies and git installation to make this test faster
          '--skipInstall',
          '--skipGit',
        ]
        const output = await execNewProject(projectName, flags)

        await assertions(output, projectName)
      }).timeout(TEST_TIMEOUT)

      it('should create a new project using long flags to configure it', async () => {
        const projectName = 'cart-demo-long-flags'
        const flags = [
          `--author "${AUTHOR}"`,
          `--description "${DESCRIPTION}"`,
          `--homepage "${HOMEPAGE}"`,
          `--license "${LICENSE}"`,
          `--providerPackageName "${PROVIDER}"`,
          `--repository "${REPO_URL}"`,
          `--version "${VERSION}"`,
          // We skip dependencies and git installation to make this test faster
          '--skipInstall',
          '--skipGit',
        ]
        const output = await execNewProject(projectName, flags)

        await assertions(output, projectName)
      }).timeout(TEST_TIMEOUT)

      context('with default parameters', async () => {
        const projectName = 'cart-demo-default'
        const flags = ['--default', '--skipInstall']
        let output: { stdout: string; stderr: string }

        before(async () => {
          output = await execNewProject(projectName, flags)
        })

        it('generates the expected project', async () => {
          await assertions(output, projectName, flags)
        })

        // We don't check for the dependencies installation at this point because it will pickup the `workspace:`
        // protocol which is not supported out of a workspace.
        it('installs dependencies', () => {})

        it('initializes git', () => {
          expect(projectFileExists(projectName, '.git')).to.be.true
        })

        it('passes linter', async () => {
          await expect(exec('npm run lint:check', { cwd: projectPath(projectName), capture: ['stderr', 'stdout'] })).to
            .be.eventually.fulfilled
        }).timeout(TEST_TIMEOUT)

        // TODO: Remove the skip when there is at leas one version published of framework-common-helpers
        it('compiles', async () => {
          const fullProjectPath = projectPath(projectName)
          // Rewrite dependencies to use local versions
          await overrideWithBoosterLocalDependencies(fullProjectPath)
          // Install those dependencies
          await exec('npm install --omit=dev --omit=optional --no-bin-links', { cwd: fullProjectPath })

          await expect(exec('npm run build', { cwd: fullProjectPath })).to.be.eventually.fulfilled
        })
      })

      it('initializes a git repo', async () => {
        const projectName = 'cart-demo-with-git'
        // We skip dependencies installation to make the test faster
        const flags = ['--default', '--skipInstall']

        await execNewProject(projectName, flags)
      }).timeout(TEST_TIMEOUT)
    })

    describe('using command prompt', () => {
      it('should create a new project', async () => {
        const projectName = 'cart-demo-command-prompt'
        const promptAnswers = {
          description: DESCRIPTION,
          version: VERSION,
          author: AUTHOR,
          website: HOMEPAGE,
          license: LICENSE,
          repository: REPO_URL,
          provider: 'default', // Just "hit enter" to choose the default one
        }
        // We skip dependencies and git installation to make this test faster
        const output = await execNewProject(projectName, ['--skipInstall', '--skipGit'], promptAnswers)

        await assertions(output, projectName)
      }).timeout(TEST_TIMEOUT)

      it('should create a new project using a custom provider', async () => {
        const projectName = 'cart-demo-custom-provider'
        const promptAnswers = {
          description: DESCRIPTION,
          version: VERSION,
          author: AUTHOR,
          website: HOMEPAGE,
          license: LICENSE,
          repository: REPO_URL,
          provider: PROVIDER,
        }
        // We skip dependencies and git installation to make this test faster
        const output = await execNewProject(projectName, ['--skipInstall', '--skipGit'], promptAnswers)

        await assertions(output, projectName)
      }).timeout(TEST_TIMEOUT)
    })

    describe('using flags and command prompt', () => {
      it('should create a new project', async () => {
        const projectName = 'cart-demo-flags-and-command-prompt'
        const promptAnswers = {
          description: DESCRIPTION,
          version: VERSION,
          author: AUTHOR,
          website: HOMEPAGE,
          license: LICENSE,
        }
        const flags = [
          `--providerPackageName "${PROVIDER}"`,
          `--repository "${REPO_URL}"`,
          // We skip dependencies and git installation to make this test faster
          '--skipInstall',
          '--skipGit',
        ]
        const output = await execNewProject(projectName, flags, promptAnswers)

        await assertions(output, projectName)
      }).timeout(TEST_TIMEOUT)
    })
  })

  context('Invalid project', () => {
    describe('missing project name', () => {
      it('should fail', async () => {
        const { stderr } = await exec(`${cliPath} new:project`, { cwd: SANDBOX_INTEGRATION_DIR })

        expect(stderr).to.match(/You haven't provided a project name, but it is required, run with --help for usage/)
      })
    })

    describe('using an invalid provider', () => {
      it('should fail', async () => {
        const expectedOutputRegex = new RegExp(
          ['boost new', 'Creating project root', 'Generating config files', 'Installing dependencies'].join('(.|\n)*')
        )

        const flags = [
          `--author "${AUTHOR}"`,
          `--description "${DESCRIPTION}"`,
          `--homepage "${HOMEPAGE}"`,
          `--license "${LICENSE}"`,
          '--providerPackageName "invalid-provider"',
          `--repository "${REPO_URL}"`,
          `--version "${VERSION}"`,
          // We skip dependencies and git installation to make this test faster
          '--skipInstall',
          '--skipGit',
        ]
        const output = await execNewProject('cart-demo-invalid-provider', flags)

        expect(output.stdout).to.match(expectedOutputRegex)
      }).timeout(TEST_TIMEOUT)
    })
  })
})
