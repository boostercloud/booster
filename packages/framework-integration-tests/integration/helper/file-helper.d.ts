/// <reference types="node" />
import { existsSync, readdirSync } from 'fs';
export declare const loadFixture: (fixturePath: string, replacements?: Array<Array<string>>) => string;
export declare const readFileContent: (filePath: string) => string;
export declare const writeFileContent: (filePath: string, data: string) => void;
export declare const removeFiles: (filePaths: Array<string>, ignoreErrors?: boolean) => void;
export declare const createFolder: (folder: string) => void;
export declare const removeFolders: (paths: Array<string>) => Promise<void>;
export declare const fileExists: typeof existsSync;
export declare const dirContents: typeof readdirSync;
export declare const sandboxPathFor: (sandboxName: string) => string;
export declare const pidForSandboxPath: (sandboxPath: string) => string;
export declare const storePIDFor: (sandboxPath: string, pid: number) => void;
export declare const readPIDFor: (sandboxPath: string) => number;
