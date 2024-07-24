"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const path = require("path");
const chai_1 = require("chai");
const file_helper_1 = require("../../helper/file-helper");
const child_process_promise_1 = require("child-process-promise");
// Imported from another package to avoid duplication
// It is OK-ish, since integration tests are always run in the context of the whole monorepo
const sandbox_1 = require("../../../../cli/src/common/sandbox");
const fs = require("fs");
describe('Build', () => {
    let buildSandboxDir;
    before(async () => {
        buildSandboxDir = (0, sandbox_1.createSandboxProject)((0, file_helper_1.sandboxPathFor)('build'));
    });
    after(async () => {
        await (0, file_helper_1.removeFolders)([buildSandboxDir]);
    });
    const cliPath = path.join('..', '..', 'cli', 'bin', 'run');
    context('Valid build', () => {
        it('should build the project', async () => {
            const expectedOutputRegex = new RegExp(['boost build', 'Checking project structure', 'Building project', 'Build complete'].join('(.|\n)*'));
            const { stdout } = await (0, child_process_promise_1.exec)(`${cliPath} build`, { cwd: buildSandboxDir });
            (0, chai_1.expect)(stdout).to.match(expectedOutputRegex);
            (0, chai_1.expect)((0, file_helper_1.fileExists)(path.join(buildSandboxDir, 'dist', 'index.js'))).to.be.true;
            (0, chai_1.expect)((0, file_helper_1.fileExists)(path.join(buildSandboxDir, 'dist', 'index.d.ts'))).to.be.true;
            (0, chai_1.expect)((0, file_helper_1.fileExists)(path.join(buildSandboxDir, 'dist', 'roles.js'))).to.be.true;
            (0, chai_1.expect)((0, file_helper_1.fileExists)(path.join(buildSandboxDir, 'dist', 'roles.d.ts'))).to.be.true;
            (0, chai_1.expect)((0, file_helper_1.fileExists)(path.join(buildSandboxDir, 'dist', 'config', 'config.js'))).to.be.true;
            (0, chai_1.expect)((0, file_helper_1.fileExists)(path.join(buildSandboxDir, 'dist', 'config', 'config.d.ts'))).to.be.true;
        });
    });
});
describe('Compile fallback', () => {
    let compileSandboxDir;
    before(async () => {
        compileSandboxDir = (0, sandbox_1.createSandboxProject)((0, file_helper_1.sandboxPathFor)('compile'));
        // Add a 'compile' script to the package.json
        const packageJsonPath = path.join(compileSandboxDir, 'package.json');
        const packageJson = JSON.parse(fs.readFileSync(packageJsonPath, 'utf8'));
        packageJson.scripts.compile = 'echo "eureka" > eureka';
        fs.writeFileSync(packageJsonPath, JSON.stringify(packageJson, null, 2));
    });
    after(async () => {
        await (0, file_helper_1.removeFolders)([compileSandboxDir]);
    });
    const cliPath = path.join('..', '..', 'cli', 'bin', 'run');
    context('when a compile script is present', () => {
        it('should call the compile script instead', async () => {
            const expectedOutputRegex = new RegExp(['boost build', 'Checking project structure', 'Building project', 'Build complete'].join('(.|\n)*'));
            const { stdout } = await (0, child_process_promise_1.exec)(`${cliPath} build`, { cwd: compileSandboxDir });
            (0, chai_1.expect)(stdout).to.match(expectedOutputRegex);
            (0, chai_1.expect)((0, file_helper_1.fileExists)(path.join(compileSandboxDir, 'eureka'))).to.be.true;
        });
    });
});
