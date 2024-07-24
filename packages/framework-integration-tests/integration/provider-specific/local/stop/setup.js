"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const file_helper_1 = require("../../../helper/file-helper");
const constants_1 = require("../constants");
before(async () => {
    const sandboxPath = (0, file_helper_1.sandboxPathFor)(constants_1.sandboxName);
    const pid = (0, file_helper_1.readPIDFor)(sandboxPath);
    console.log(`stopping local server with pid ${pid}...`);
    process.kill(pid);
    console.log('removing sandbox project...');
    await (0, file_helper_1.removeFolders)([sandboxPath]);
});
