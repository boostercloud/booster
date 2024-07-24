/// <reference types="node" />
import { ChildProcessWithoutNullStreams } from 'child_process';
export declare function deploy(projectPath: string, environmentName?: string): Promise<void>;
export declare function nuke(projectPath: string, environmentName?: string): Promise<void>;
export declare function start(path: string, environmentName?: string): ChildProcessWithoutNullStreams;
