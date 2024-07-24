import { ProviderTestHelper } from '@boostercloud/application-tester';
interface Phase {
    duration: number;
    arrivalRate: number;
}
interface OverrideOptions {
    phases?: Array<Phase>;
    variables?: Record<string, any>;
}
export declare class ArtilleryExecutor {
    private scriptsFolder;
    private readonly providerTestHelper;
    private readonly stage;
    private readonly serverlessArtilleryStackPrefix;
    constructor(scriptsFolder: string, providerTestHelper: ProviderTestHelper, stage?: string);
    ensureDeployed(): Promise<void>;
    executeScript(scriptName: string, overrideOptions?: OverrideOptions): Promise<void>;
    private getScriptContent;
}
export {};
