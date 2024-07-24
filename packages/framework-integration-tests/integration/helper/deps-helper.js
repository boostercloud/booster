"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.forceRepoRebuild = exports.overrideWithBoosterLocalDependencies = void 0;
const child_process_promise_1 = require("child-process-promise");
const path = require("path");
const fs = require("fs");
async function overrideWithBoosterLocalDependencies(projectPath) {
    const projectRelativePath = path.relative(__dirname, projectPath);
    const packageJSON = require(path.join(projectRelativePath, 'package.json'));
    overrideWithLocalDeps(packageJSON.dependencies);
    overrideWithLocalDeps(packageJSON.devDependencies);
    fs.writeFileSync(path.join(projectPath, 'package.json'), JSON.stringify(packageJSON, undefined, 2));
}
exports.overrideWithBoosterLocalDependencies = overrideWithBoosterLocalDependencies;
function overrideWithLocalDeps(dependencies) {
    for (const packageName in dependencies) {
        if (/@boostercloud/.test(packageName)) {
            const sanitizedPackageName = packageName.replace('@', '').replace('/', '-');
            const sanitizedPackageVersion = dependencies[packageName]
                .replace('workspace:', '')
                .replace(/\*/g, '')
                .replace('^', '');
            const packedDependencyFileName = `${sanitizedPackageName}-${sanitizedPackageVersion}.tgz`;
            const dotBooster = '.booster';
            if (!fs.existsSync(dotBooster)) {
                fs.mkdirSync(dotBooster);
            }
            const dotBoosterAbsolutePath = path.resolve(dotBooster);
            // Now override the packageJSON dependencies with the path to the packed dependency
            dependencies[packageName] = `file:${path.join(dotBoosterAbsolutePath, packedDependencyFileName)}`;
        }
    }
}
async function forceRepoRebuild() {
    await (0, child_process_promise_1.exec)('rush update && rush rebuild');
}
exports.forceRepoRebuild = forceRepoRebuild;
