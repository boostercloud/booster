"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const fs = require("fs-extra");
const childProcessPromise = require("child-process-promise");
const project_initializer_1 = require("../../src/services/project-initializer");
const sinon_1 = require("sinon");
const expect_1 = require("../expect");
const test_impl_1 = require("./package-manager/test.impl");
const PackageManager = require("../../src/services/package-manager/live.impl");
describe('project initializer', () => {
    beforeEach(() => {
        (0, sinon_1.replace)(fs, 'mkdirs', sinon_1.fake.resolves({}));
        (0, sinon_1.replace)(fs, 'outputFile', sinon_1.fake.resolves({}));
        (0, sinon_1.replace)(childProcessPromise, 'exec', sinon_1.fake.resolves({}));
    });
    afterEach(() => {
        (0, sinon_1.restore)();
    });
    const projectName = 'test-project';
    const defaultProvider = '@boostercloud/framework-provider-aws';
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
    it('initialize Git', async () => {
        await (0, project_initializer_1.initializeGit)(defaultProjectInitializerConfig);
        (0, expect_1.expect)(childProcessPromise.exec).to.have.been.calledWithMatch('git init && git add -A && git commit -m "Initial commit"');
    });
    it('Generate root directory', async () => {
        await (0, project_initializer_1.generateRootDirectory)(defaultProjectInitializerConfig);
        (0, expect_1.expect)(fs.mkdirs).to.have.been.calledWithMatch(`${projectName}/src/commands`);
        (0, expect_1.expect)(fs.mkdirs).to.have.been.calledWithMatch(`${projectName}/src/events`);
        (0, expect_1.expect)(fs.mkdirs).to.have.been.calledWithMatch(`${projectName}/src/entities`);
        (0, expect_1.expect)(fs.mkdirs).to.have.been.calledWithMatch(`${projectName}/src/read-models`);
        (0, expect_1.expect)(fs.mkdirs).to.have.been.calledWithMatch(`${projectName}/src/config`);
        (0, expect_1.expect)(fs.mkdirs).to.have.been.calledWithMatch(`${projectName}/src/common`);
        (0, expect_1.expect)(fs.mkdirs).to.have.been.calledWithMatch(`${projectName}/src/event-handlers`);
        (0, expect_1.expect)(fs.mkdirs).to.have.been.calledWithMatch(`${projectName}/src/scheduled-commands`);
    });
    it('install dependencies', async () => {
        const TestPackageManager = (0, test_impl_1.makeTestPackageManager)();
        (0, sinon_1.replace)(PackageManager, 'LivePackageManager', TestPackageManager.layer);
        await (0, project_initializer_1.installDependencies)(defaultProjectInitializerConfig);
        (0, expect_1.expect)(TestPackageManager.fakes.installAllDependencies).to.have.been.called;
    });
    it('Generate config files', async () => {
        await (0, project_initializer_1.generateConfigFiles)(defaultProjectInitializerConfig);
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
    });
});
