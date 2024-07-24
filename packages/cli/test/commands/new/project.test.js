"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const sinon_1 = require("sinon");
const logger_1 = require("../../../src/services/logger");
const fs = require("fs-extra");
const expect_1 = require("../../expect");
const Project = require("../../../src/commands/new/project");
const ProjectInitializer = require("../../../src/services/project-initializer");
const packageJson = require("../../../src/templates/project/package-json");
const Mustache = require("mustache");
describe('new', () => {
    describe('project', () => {
        context('default provider', () => {
            const projectName = 'test-project';
            const defaultProvider = '@boostercloud/framework-provider-aws';
            const defaultRepository = 'github.com:boostercloud/booster.git';
            const expectFilesAndDirectoriesCreated = (projectName) => {
                (0, expect_1.expect)(fs.mkdirs).to.have.been.calledWithMatch(`${projectName}/src/commands`);
                (0, expect_1.expect)(fs.mkdirs).to.have.been.calledWithMatch(`${projectName}/src/events`);
                (0, expect_1.expect)(fs.mkdirs).to.have.been.calledWithMatch(`${projectName}/src/entities`);
                (0, expect_1.expect)(fs.mkdirs).to.have.been.calledWithMatch(`${projectName}/src/read-models`);
                (0, expect_1.expect)(fs.mkdirs).to.have.been.calledWithMatch(`${projectName}/src/config`);
                (0, expect_1.expect)(fs.mkdirs).to.have.been.calledWithMatch(`${projectName}/src/common`);
                (0, expect_1.expect)(fs.mkdirs).to.have.been.calledWithMatch(`${projectName}/src/event-handlers`);
                (0, expect_1.expect)(fs.mkdirs).to.have.been.calledWithMatch(`${projectName}/src/scheduled-commands`);
                (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/.eslintignore`);
                (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/.eslintrc.js`);
                (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/.gitignore`);
                (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/package.json`);
                (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/tsconfig.json`);
                (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/tsconfig.eslint.json`);
                (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/.prettierrc.yaml`);
                (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/src/config/config.ts`);
                (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/src/index.ts`);
                (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/.mocharc.yml`);
            };
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
            };
            const renderPackageJson = (config) => {
                return Mustache.render(packageJson.template, config);
            };
            beforeEach(() => {
                (0, sinon_1.replace)(fs, 'mkdirs', sinon_1.fake.resolves({}));
                (0, sinon_1.replace)(fs, 'outputFile', sinon_1.fake.resolves({}));
                (0, sinon_1.replace)(ProjectInitializer, 'initializeGit', sinon_1.fake.resolves({}));
                (0, sinon_1.replace)(ProjectInitializer, 'installDependencies', sinon_1.fake.resolves({}));
                (0, sinon_1.replace)(logger_1.oraLogger, 'info', sinon_1.fake.resolves({}));
                (0, sinon_1.replace)(logger_1.oraLogger, 'start', sinon_1.fake.resolves({}));
                (0, sinon_1.replace)(logger_1.oraLogger, 'succeed', sinon_1.fake.resolves({}));
            });
            afterEach(() => {
                (0, sinon_1.restore)();
            });
            describe('works properly', () => {
                it('without flags', async () => {
                    (0, sinon_1.replace)(Project, 'parseConfig', sinon_1.fake.returns(defaultProjectInitializerConfig));
                    await new Project.default([projectName], {}).run();
                    (0, expect_1.expect)(ProjectInitializer.initializeGit).to.have.been.called;
                    (0, expect_1.expect)(ProjectInitializer.installDependencies).to.have.been.called;
                    (0, expect_1.expect)(logger_1.oraLogger.info).to.have.been.calledWithMatch('Project generated!');
                    expectFilesAndDirectoriesCreated(projectName);
                });
                it('skip dependencies installation with --skipInstall', async () => {
                    (0, sinon_1.replace)(Project, 'parseConfig', sinon_1.fake.returns(defaultProjectInitializerConfig));
                    await new Project.default([projectName, '--skipInstall'], {}).run();
                    (0, expect_1.expect)(ProjectInitializer.installDependencies).to.have.not.been.called;
                    (0, expect_1.expect)(ProjectInitializer.initializeGit).to.have.been.called;
                    (0, expect_1.expect)(logger_1.oraLogger.info).to.have.been.calledWithMatch('Project generated!');
                    expectFilesAndDirectoriesCreated(projectName);
                });
                it('generates project with default parameters when using --default flag', async () => {
                    const parseConfigSpy = (0, sinon_1.spy)(Project, 'parseConfig');
                    await new Project.default([projectName, '--default'], { version: '0.5.1' }).run();
                    (0, expect_1.expect)(logger_1.oraLogger.info).to.have.been.calledWithMatch('Project generated!');
                    expectFilesAndDirectoriesCreated(projectName);
                    (0, expect_1.expect)(parseConfigSpy).to.have.been.calledOnce;
                    (0, expect_1.expect)(await parseConfigSpy.firstCall.returnValue).to.be.deep.equal(defaultProjectInitializerConfig);
                    const expectedPackageJson = renderPackageJson(defaultProjectInitializerConfig);
                    (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/package.json`, expectedPackageJson);
                    (0, expect_1.expect)(ProjectInitializer.installDependencies).to.have.been.called;
                });
                it('skips git repository initialization with --skipGit', async () => {
                    (0, sinon_1.replace)(Project, 'parseConfig', sinon_1.fake.returns(defaultProjectInitializerConfig));
                    await new Project.default([projectName, '--skipGit'], {}).run();
                    (0, expect_1.expect)(logger_1.oraLogger.info).to.have.been.calledWithMatch('Project generated!');
                    (0, expect_1.expect)(ProjectInitializer.initializeGit).to.have.not.been.called;
                    expectFilesAndDirectoriesCreated(projectName);
                    const expectedPackageJson = renderPackageJson(defaultProjectInitializerConfig);
                    (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/package.json`, expectedPackageJson);
                });
                describe('define homepage', () => {
                    it('with --homepage', async () => {
                        const config = { ...defaultProjectInitializerConfig, homepage: 'boosterframework.com' };
                        (0, sinon_1.replace)(Project, 'parseConfig', sinon_1.fake.returns(config));
                        await new Project.default([projectName, '--homepage', "'boosterframework.com'"], {}).run();
                        (0, expect_1.expect)(ProjectInitializer.initializeGit).to.have.been.called;
                        (0, expect_1.expect)(ProjectInitializer.installDependencies).to.have.been.called;
                        (0, expect_1.expect)(logger_1.oraLogger.info).to.have.been.calledWithMatch('Project generated!');
                        expectFilesAndDirectoriesCreated(projectName);
                        (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/package.json`, renderPackageJson(config));
                    });
                    it('with -H', async () => {
                        const config = { ...defaultProjectInitializerConfig, homepage: 'boosterframework.com' };
                        (0, sinon_1.replace)(Project, 'parseConfig', sinon_1.fake.returns(config));
                        await new Project.default([projectName, '-H', "'boosterframework.com'"], {}).run();
                        (0, expect_1.expect)(ProjectInitializer.initializeGit).to.have.been.called;
                        (0, expect_1.expect)(ProjectInitializer.installDependencies).to.have.been.called;
                        (0, expect_1.expect)(logger_1.oraLogger.info).to.have.been.calledWithMatch('Project generated!');
                        expectFilesAndDirectoriesCreated(projectName);
                        (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/package.json`, renderPackageJson(config));
                    });
                });
                describe('define author', () => {
                    it('with --author', async () => {
                        const config = { ...defaultProjectInitializerConfig, author: 'John Doe' };
                        (0, sinon_1.replace)(Project, 'parseConfig', sinon_1.fake.returns(config));
                        await new Project.default([projectName, '--author', "'John Doe'"], {}).run();
                        (0, expect_1.expect)(ProjectInitializer.initializeGit).to.have.been.called;
                        (0, expect_1.expect)(ProjectInitializer.installDependencies).to.have.been.called;
                        (0, expect_1.expect)(logger_1.oraLogger.info).to.have.been.calledWithMatch('Project generated!');
                        expectFilesAndDirectoriesCreated(projectName);
                        (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/package.json`, renderPackageJson(config));
                    });
                    it('with -a', async () => {
                        const config = { ...defaultProjectInitializerConfig, author: 'John Doe' };
                        (0, sinon_1.replace)(Project, 'parseConfig', sinon_1.fake.returns(config));
                        await new Project.default([projectName, '-a', "'John Doe'"], {}).run();
                        (0, expect_1.expect)(ProjectInitializer.initializeGit).to.have.been.called;
                        (0, expect_1.expect)(ProjectInitializer.installDependencies).to.have.been.called;
                        (0, expect_1.expect)(logger_1.oraLogger.info).to.have.been.calledWithMatch('Project generated!');
                        expectFilesAndDirectoriesCreated(projectName);
                        (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/package.json`, renderPackageJson(config));
                    });
                });
                describe('define description', () => {
                    it('with --description', async () => {
                        const config = { ...defaultProjectInitializerConfig, description: 'a short description' };
                        (0, sinon_1.replace)(Project, 'parseConfig', sinon_1.fake.returns(config));
                        await new Project.default([projectName, '--description', "'a short description'"], {}).run();
                        (0, expect_1.expect)(ProjectInitializer.initializeGit).to.have.been.called;
                        (0, expect_1.expect)(ProjectInitializer.installDependencies).to.have.been.called;
                        (0, expect_1.expect)(logger_1.oraLogger.info).to.have.been.calledWithMatch('Project generated!');
                        expectFilesAndDirectoriesCreated(projectName);
                        (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/package.json`, renderPackageJson(config));
                    });
                    it('with -d', async () => {
                        const config = { ...defaultProjectInitializerConfig, description: 'a short description' };
                        (0, sinon_1.replace)(Project, 'parseConfig', sinon_1.fake.returns(config));
                        await new Project.default([projectName, '-d', "'a short description'"], {}).run();
                        (0, expect_1.expect)(ProjectInitializer.initializeGit).to.have.been.called;
                        (0, expect_1.expect)(ProjectInitializer.installDependencies).to.have.been.called;
                        (0, expect_1.expect)(logger_1.oraLogger.info).to.have.been.calledWithMatch('Project generated!');
                        expectFilesAndDirectoriesCreated(projectName);
                        (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/package.json`, renderPackageJson(config));
                    });
                });
                describe('define license', () => {
                    it('with --license', async () => {
                        const config = { ...defaultProjectInitializerConfig, license: 'GPL' };
                        (0, sinon_1.replace)(Project, 'parseConfig', sinon_1.fake.returns(config));
                        await new Project.default([projectName, '--license', 'GPL'], {}).run();
                        (0, expect_1.expect)(ProjectInitializer.initializeGit).to.have.been.called;
                        (0, expect_1.expect)(ProjectInitializer.installDependencies).to.have.been.called;
                        (0, expect_1.expect)(logger_1.oraLogger.info).to.have.been.calledWithMatch('Project generated!');
                        expectFilesAndDirectoriesCreated(projectName);
                        (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/package.json`, renderPackageJson(config));
                    });
                    it('with -l', async () => {
                        const config = { ...defaultProjectInitializerConfig, license: 'GPL' };
                        (0, sinon_1.replace)(Project, 'parseConfig', sinon_1.fake.returns(config));
                        await new Project.default([projectName, '-l', 'GPL'], {}).run();
                        (0, expect_1.expect)(ProjectInitializer.initializeGit).to.have.been.called;
                        (0, expect_1.expect)(ProjectInitializer.installDependencies).to.have.been.called;
                        (0, expect_1.expect)(logger_1.oraLogger.info).to.have.been.calledWithMatch('Project generated!');
                        expectFilesAndDirectoriesCreated(projectName);
                        (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/package.json`, renderPackageJson(config));
                    });
                });
                describe('define provider', () => {
                    it('with --providerPackageName', async () => {
                        (0, sinon_1.replace)(Project, 'parseConfig', sinon_1.fake.returns(defaultProjectInitializerConfig));
                        await new Project.default([projectName, '--providerPackageName', defaultProvider], {}).run();
                        (0, expect_1.expect)(ProjectInitializer.initializeGit).to.have.been.called;
                        (0, expect_1.expect)(ProjectInitializer.installDependencies).to.have.been.called;
                        (0, expect_1.expect)(logger_1.oraLogger.info).to.have.been.calledWithMatch('Project generated!');
                        expectFilesAndDirectoriesCreated(projectName);
                    });
                    it('with -p', async () => {
                        (0, sinon_1.replace)(Project, 'parseConfig', sinon_1.fake.returns(defaultProjectInitializerConfig));
                        await new Project.default([projectName, '-p', defaultProvider], {}).run();
                        (0, expect_1.expect)(ProjectInitializer.initializeGit).to.have.been.called;
                        (0, expect_1.expect)(ProjectInitializer.installDependencies).to.have.been.called;
                        (0, expect_1.expect)(logger_1.oraLogger.info).to.have.been.calledWithMatch('Project generated!');
                        expectFilesAndDirectoriesCreated(projectName);
                    });
                });
                describe('define repository', () => {
                    it('with --repository', async () => {
                        const config = { ...defaultProjectInitializerConfig, repository: defaultRepository };
                        (0, sinon_1.replace)(Project, 'parseConfig', sinon_1.fake.returns(config));
                        await new Project.default([projectName, '--repository', defaultRepository], {}).run();
                        (0, expect_1.expect)(ProjectInitializer.initializeGit).to.have.been.called;
                        (0, expect_1.expect)(ProjectInitializer.installDependencies).to.have.been.called;
                        (0, expect_1.expect)(logger_1.oraLogger.info).to.have.been.calledWithMatch('Project generated!');
                        expectFilesAndDirectoriesCreated(projectName);
                        (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/package.json`, renderPackageJson(config));
                    });
                    it('with -r', async () => {
                        const config = { ...defaultProjectInitializerConfig, repository: defaultRepository };
                        (0, sinon_1.replace)(Project, 'parseConfig', sinon_1.fake.returns(config));
                        await new Project.default([projectName, '-r', defaultRepository], {}).run();
                        (0, expect_1.expect)(ProjectInitializer.initializeGit).to.have.been.called;
                        (0, expect_1.expect)(ProjectInitializer.installDependencies).to.have.been.called;
                        (0, expect_1.expect)(logger_1.oraLogger.info).to.have.been.calledWithMatch('Project generated!');
                        expectFilesAndDirectoriesCreated(projectName);
                        (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/package.json`, renderPackageJson(config));
                    });
                });
                describe('define version', () => {
                    it('with --version', async () => {
                        const config = { ...defaultProjectInitializerConfig, version: '1.0.0' };
                        (0, sinon_1.replace)(Project, 'parseConfig', sinon_1.fake.returns(config));
                        await new Project.default([projectName, '--version', '1.0.0'], {}).run();
                        (0, expect_1.expect)(ProjectInitializer.initializeGit).to.have.been.called;
                        (0, expect_1.expect)(ProjectInitializer.installDependencies).to.have.been.called;
                        (0, expect_1.expect)(logger_1.oraLogger.info).to.have.been.calledWithMatch('Project generated!');
                        expectFilesAndDirectoriesCreated(projectName);
                        (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/package.json`, renderPackageJson(config));
                    });
                    it('with -v', async () => {
                        const config = { ...defaultProjectInitializerConfig, version: '1.0.0' };
                        (0, sinon_1.replace)(Project, 'parseConfig', sinon_1.fake.returns(config));
                        await new Project.default([projectName, '-v', '1.0.0'], {}).run();
                        (0, expect_1.expect)(ProjectInitializer.initializeGit).to.have.been.called;
                        (0, expect_1.expect)(ProjectInitializer.installDependencies).to.have.been.called;
                        (0, expect_1.expect)(logger_1.oraLogger.info).to.have.been.calledWithMatch('Project generated!');
                        expectFilesAndDirectoriesCreated(projectName);
                        (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(`${projectName}/package.json`, renderPackageJson(config));
                    });
                });
                describe('define multiple flags', () => {
                    it('with all options (long flags)', async () => {
                        await new Project.default([projectName,
                            '--version', '1.0.0',
                            '--author', "'John Doe'",
                            '--description', "'a new description'",
                            '--homepage', 'boosterframework.com',
                            '--repository', 'github.com/boostercloud/booster.git',
                            '--license', 'GPL',
                            '--providerPackageName', defaultProvider,
                            '--skipInstall',
                            '--skipGit'
                        ], {}).run();
                        (0, expect_1.expect)(ProjectInitializer.initializeGit).to.have.not.been.called;
                        (0, expect_1.expect)(ProjectInitializer.installDependencies).to.have.not.been.called;
                        (0, expect_1.expect)(logger_1.oraLogger.info).to.have.been.calledWithMatch('Project generated!');
                        expectFilesAndDirectoriesCreated(projectName);
                    });
                    it('with all options (short flags)', async () => {
                        await new Project.default([projectName,
                            '-v', '1.0.0',
                            '-a', "'John Doe'",
                            '-d', "'a new description'",
                            '-H', 'boosterframework.com',
                            '-r', 'github.com/boostercloud/booster.git',
                            '-l', 'GPL',
                            '-p', defaultProvider,
                            '--skipInstall',
                            '--skipGit'
                        ], {}).run();
                        (0, expect_1.expect)(ProjectInitializer.initializeGit).to.have.not.been.called;
                        (0, expect_1.expect)(ProjectInitializer.installDependencies).to.have.not.been.called;
                        (0, expect_1.expect)(logger_1.oraLogger.info).to.have.been.calledWithMatch('Project generated!');
                        expectFilesAndDirectoriesCreated(projectName);
                    });
                });
            });
            describe('displays an error', () => {
                it('with empty project name', async () => {
                    (0, sinon_1.replace)(console, 'error', sinon_1.fake.resolves({}));
                    await new Project.default([], {}).run();
                    (0, expect_1.expect)(fs.mkdirs).to.have.not.been.calledWithMatch(`${projectName}/src`);
                    (0, expect_1.expect)(console.error).to.have.been.calledWithMatch(/You haven't provided a project name/);
                    (0, expect_1.expect)(logger_1.oraLogger.info).to.have.not.been.calledWithMatch('Project generated!');
                });
                it('with nonexisting option', async () => {
                    let exceptionThrown = false;
                    let exceptionMessage = '';
                    try {
                        await new Project.default([projectName, '--nonexistingoption'], {}).run();
                    }
                    catch (e) {
                        exceptionThrown = true;
                        exceptionMessage = e.message;
                    }
                    (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                    (0, expect_1.expect)(exceptionMessage).to.contain('Nonexistent flag: --nonexistingoption');
                    (0, expect_1.expect)(logger_1.oraLogger.info).to.have.not.been.calledWithMatch('Project generated!');
                    (0, expect_1.expect)(fs.mkdirs).to.have.not.been.calledWithMatch(`${projectName}/src`);
                });
                describe('define homepage badly', () => {
                    it('with --homepage and no value', async () => {
                        (0, sinon_1.replace)(Project, 'parseConfig', sinon_1.fake.returns(defaultProjectInitializerConfig));
                        let exceptionThrown = false;
                        let exceptionMessage = '';
                        try {
                            await new Project.default([projectName, '--homepage'], {}).run();
                        }
                        catch (e) {
                            exceptionThrown = true;
                            exceptionMessage = e.message;
                        }
                        (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                        (0, expect_1.expect)(exceptionMessage).to.contain('--homepage expects a value');
                    });
                    it('with -H and no value', async () => {
                        (0, sinon_1.replace)(Project, 'parseConfig', sinon_1.fake.returns(defaultProjectInitializerConfig));
                        let exceptionThrown = false;
                        let exceptionMessage = '';
                        try {
                            await new Project.default([projectName, '-H'], {}).run();
                        }
                        catch (e) {
                            exceptionThrown = true;
                            exceptionMessage = e.message;
                        }
                        (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                        (0, expect_1.expect)(exceptionMessage).to.contain('--homepage expects a value');
                    });
                });
                describe('define author badly', () => {
                    it('with --author and no author', async () => {
                        (0, sinon_1.replace)(Project, 'parseConfig', sinon_1.fake.returns(defaultProjectInitializerConfig));
                        let exceptionThrown = false;
                        let exceptionMessage = '';
                        try {
                            await new Project.default([projectName, '--author'], {}).run();
                        }
                        catch (e) {
                            exceptionThrown = true;
                            exceptionMessage = e.message;
                        }
                        (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                        (0, expect_1.expect)(exceptionMessage).to.contain('--author expects a value');
                    });
                    it('with -a and no author', async () => {
                        (0, sinon_1.replace)(Project, 'parseConfig', sinon_1.fake.returns(defaultProjectInitializerConfig));
                        let exceptionThrown = false;
                        let exceptionMessage = '';
                        try {
                            await new Project.default([projectName, '-a'], {}).run();
                        }
                        catch (e) {
                            exceptionThrown = true;
                            exceptionMessage = e.message;
                        }
                        (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                        (0, expect_1.expect)(exceptionMessage).to.contain('--author expects a value');
                    });
                });
                describe('define description badly', () => {
                    it('with --description and no description', async () => {
                        (0, sinon_1.replace)(Project, 'parseConfig', sinon_1.fake.returns(defaultProjectInitializerConfig));
                        let exceptionThrown = false;
                        let exceptionMessage = '';
                        try {
                            await new Project.default([projectName, '--description'], {}).run();
                        }
                        catch (e) {
                            exceptionThrown = true;
                            exceptionMessage = e.message;
                        }
                        (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                        (0, expect_1.expect)(exceptionMessage).to.contain('--description expects a value');
                    });
                    it('with -d and no description', async () => {
                        (0, sinon_1.replace)(Project, 'parseConfig', sinon_1.fake.returns(defaultProjectInitializerConfig));
                        let exceptionThrown = false;
                        let exceptionMessage = '';
                        try {
                            await new Project.default([projectName, '-d'], {}).run();
                        }
                        catch (e) {
                            exceptionThrown = true;
                            exceptionMessage = e.message;
                        }
                        (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                        (0, expect_1.expect)(exceptionMessage).to.contain('--description expects a value');
                    });
                });
                describe('define license badly', () => {
                    it('with --license and no license name', async () => {
                        (0, sinon_1.replace)(Project, 'parseConfig', sinon_1.fake.returns(defaultProjectInitializerConfig));
                        let exceptionThrown = false;
                        let exceptionMessage = '';
                        try {
                            await new Project.default([projectName, '--license'], {}).run();
                        }
                        catch (e) {
                            exceptionThrown = true;
                            exceptionMessage = e.message;
                        }
                        (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                        (0, expect_1.expect)(exceptionMessage).to.contain('--license expects a value');
                    });
                    it('with -l and no license name', async () => {
                        (0, sinon_1.replace)(Project, 'parseConfig', sinon_1.fake.returns(defaultProjectInitializerConfig));
                        let exceptionThrown = false;
                        let exceptionMessage = '';
                        try {
                            await new Project.default([projectName, '-l'], {}).run();
                        }
                        catch (e) {
                            exceptionThrown = true;
                            exceptionMessage = e.message;
                        }
                        (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                        (0, expect_1.expect)(exceptionMessage).to.contain('--license expects a value');
                    });
                });
                describe('define provider badly', () => {
                    it('with --providerPackageName and no provider', async () => {
                        (0, sinon_1.replace)(Project, 'parseConfig', sinon_1.fake.returns(defaultProjectInitializerConfig));
                        let exceptionThrown = false;
                        let exceptionMessage = '';
                        try {
                            await new Project.default([projectName, '--providerPackageName'], {}).run();
                        }
                        catch (e) {
                            exceptionThrown = true;
                            exceptionMessage = e.message;
                        }
                        (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                        (0, expect_1.expect)(exceptionMessage).to.contain('--providerPackageName expects a value');
                    });
                    it('with -p and no provider', async () => {
                        (0, sinon_1.replace)(Project, 'parseConfig', sinon_1.fake.returns(defaultProjectInitializerConfig));
                        let exceptionThrown = false;
                        let exceptionMessage = '';
                        try {
                            await new Project.default([projectName, '-p'], {}).run();
                        }
                        catch (e) {
                            exceptionThrown = true;
                            exceptionMessage = e.message;
                        }
                        (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                        (0, expect_1.expect)(exceptionMessage).to.contain('--providerPackageName expects a value');
                    });
                });
                describe('define repository badly', () => {
                    it('with --repository and no repository name', async () => {
                        (0, sinon_1.replace)(Project, 'parseConfig', sinon_1.fake.returns(defaultProjectInitializerConfig));
                        let exceptionThrown = false;
                        let exceptionMessage = '';
                        try {
                            await new Project.default([projectName, '--repository'], {}).run();
                        }
                        catch (e) {
                            exceptionThrown = true;
                            exceptionMessage = e.message;
                        }
                        (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                        (0, expect_1.expect)(exceptionMessage).to.contain('--repository expects a value');
                    });
                    it('with -r and no repository name', async () => {
                        (0, sinon_1.replace)(Project, 'parseConfig', sinon_1.fake.returns(defaultProjectInitializerConfig));
                        let exceptionThrown = false;
                        let exceptionMessage = '';
                        try {
                            await new Project.default([projectName, '-r'], {}).run();
                        }
                        catch (e) {
                            exceptionThrown = true;
                            exceptionMessage = e.message;
                        }
                        (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                        (0, expect_1.expect)(exceptionMessage).to.contain('--repository expects a value');
                    });
                });
                describe('define version badly', () => {
                    it('with --version and no version number', async () => {
                        (0, sinon_1.replace)(Project, 'parseConfig', sinon_1.fake.returns(defaultProjectInitializerConfig));
                        let exceptionThrown = false;
                        let exceptionMessage = '';
                        try {
                            await new Project.default([projectName, '--version'], {}).run();
                        }
                        catch (e) {
                            exceptionThrown = true;
                            exceptionMessage = e.message;
                        }
                        (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                        (0, expect_1.expect)(exceptionMessage).to.contain('--version expects a value');
                    });
                    it('with -v and no version number', async () => {
                        (0, sinon_1.replace)(Project, 'parseConfig', sinon_1.fake.returns(defaultProjectInitializerConfig));
                        let exceptionThrown = false;
                        let exceptionMessage = '';
                        try {
                            await new Project.default([projectName, '-v'], {}).run();
                        }
                        catch (e) {
                            exceptionThrown = true;
                            exceptionMessage = e.message;
                        }
                        (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                        (0, expect_1.expect)(exceptionMessage).to.contain('--version expects a value');
                    });
                });
            });
            xdescribe('should display an error but is not validated', () => {
                describe('define provider badly', () => {
                    xit('with --providerPackageName and an an noneexisting provider', async () => {
                        (0, sinon_1.replace)(Project, 'parseConfig', sinon_1.fake.returns(defaultProjectInitializerConfig));
                        let exceptionThrown = false;
                        let exceptionMessage = '';
                        try {
                            await new Project.default([projectName, '--providerPackageName', 'nonexistingProvider'], {}).run();
                        }
                        catch (e) {
                            exceptionThrown = true;
                            exceptionMessage = e.message;
                        }
                        (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                        (0, expect_1.expect)(exceptionMessage).to.contain('--providerPackageName expects a value');
                    });
                });
                describe('define repository badly', () => {
                    xit('with --repository and invalid URL', async () => {
                        (0, sinon_1.replace)(Project, 'parseConfig', sinon_1.fake.returns(defaultProjectInitializerConfig));
                        let exceptionThrown = false;
                        let exceptionMessage = '';
                        try {
                            await new Project.default([projectName, '--repository', 'invalidUrl'], {}).run();
                        }
                        catch (e) {
                            exceptionThrown = true;
                            exceptionMessage = e.message;
                        }
                        (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                        (0, expect_1.expect)(exceptionMessage).to.contain('--repository expects a url');
                    });
                });
            });
        });
    });
});
