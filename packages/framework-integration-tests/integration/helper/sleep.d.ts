export declare function sleep(ms: number): Promise<void>;
export declare function waitForIt<TResult>(tryFunction: () => Promise<TResult>, checkResult: (result: TResult) => boolean | string, trialDelayMs?: number, timeoutMs?: number): Promise<TResult>;
