import { ProviderTestHelper } from '@boostercloud/application-tester';
export declare function applicationName(): string;
export declare function getProviderTestHelper(): Promise<ProviderTestHelper>;
export declare function setEnv(): Promise<void>;
export declare function checkAndGetCurrentEnv(): string;
