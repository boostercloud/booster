"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.readPIDFor = exports.storePIDFor = exports.pidForSandboxPath = exports.sandboxPathFor = exports.dirContents = exports.fileExists = exports.removeFolders = exports.createFolder = exports.removeFiles = exports.writeFileContent = exports.readFileContent = exports.loadFixture = void 0;
const fs_1 = require("fs");
const path = require("path");
const sleep_1 = require("./sleep");
const loadFixture = (fixturePath, replacements) => {
    var _a;
    const template = (0, exports.readFileContent)(`integration/fixtures/${fixturePath}`);
    return ((_a = replacements === null || replacements === void 0 ? void 0 : replacements.reduce((prevContents, replacement) => prevContents.split(replacement[0]).join(replacement[1]), template)) !== null && _a !== void 0 ? _a : template);
};
exports.loadFixture = loadFixture;
const readFileContent = (filePath) => (0, fs_1.readFileSync)(filePath, 'utf-8');
exports.readFileContent = readFileContent;
const writeFileContent = (filePath, data) => (0, fs_1.writeFileSync)(filePath, data);
exports.writeFileContent = writeFileContent;
const removeFiles = (filePaths, ignoreErrors = false) => {
    filePaths.map((file) => {
        try {
            (0, fs_1.unlinkSync)(file);
        }
        catch (e) {
            if (!ignoreErrors)
                throw e;
        }
    });
};
exports.removeFiles = removeFiles;
const createFolder = (folder) => {
    if (!(0, fs_1.existsSync)(folder)) {
        (0, fs_1.mkdirSync)(folder);
    }
};
exports.createFolder = createFolder;
const removeFolders = async (paths) => {
    for (const path of paths) {
        let retries = 5;
        while (retries > 0) {
            try {
                (0, fs_1.rmSync)(path, { recursive: true, force: true });
                break; // Break out of the while loop if the deletion succeeds
            }
            catch (error) {
                // Although we're using parameters recursive and force, sometimes the deletion fails with ENOTEMPTY
                // because the OS haven't had the time to fully release the files. We retry a few times before giving up.
                if (error.code === 'ENOTEMPTY' && retries > 0) {
                    retries--;
                    console.warn(`Retrying deletion of ${path}, ${retries} retries remaining...`);
                    await (0, sleep_1.sleep)(1000); // Wait for 1 second before retrying
                }
                else {
                    // After the retries are exhausted, we silently desist. It's not worth failing integration tests because of this
                    console.warn(`Failed to delete ${path}, skipping...`);
                    break;
                }
            }
        }
    }
};
exports.removeFolders = removeFolders;
exports.fileExists = fs_1.existsSync;
exports.dirContents = fs_1.readdirSync;
const sandboxPathFor = (sandboxName) => sandboxName + '-integration-sandbox'; // Add the suffix to make sure this folder is gitignored
exports.sandboxPathFor = sandboxPathFor;
const pidForSandboxPath = (sandboxPath) => path.join(sandboxPath, 'local_provider.pid');
exports.pidForSandboxPath = pidForSandboxPath;
const storePIDFor = (sandboxPath, pid) => {
    (0, fs_1.writeFileSync)((0, exports.pidForSandboxPath)(sandboxPath), pid.toString());
};
exports.storePIDFor = storePIDFor;
const readPIDFor = (sandboxPath) => {
    const pidFile = (0, exports.pidForSandboxPath)(sandboxPath);
    return parseInt((0, fs_1.readFileSync)(pidFile).toString(), 10);
};
exports.readPIDFor = readPIDFor;
