"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.waitForIt = exports.sleep = void 0;
async function sleep(ms) {
    return new Promise((resolve) => setTimeout(resolve, ms));
}
exports.sleep = sleep;
async function waitForIt(tryFunction, checkResult, trialDelayMs = 1000, timeoutMs = 600000) {
    console.debug('[waitForIt] start');
    const start = Date.now();
    return doWaitFor();
    async function doWaitFor() {
        console.debug('.');
        const res = await tryFunction();
        const checkResultValue = checkResult(res);
        const waitingResult = typeof checkResultValue === 'boolean' ? checkResultValue : false;
        if (waitingResult) {
            console.debug('[waitForIt] match!');
            return res;
        }
        const elapsed = Date.now() - start;
        if (elapsed > timeoutMs) {
            if (typeof checkResultValue === 'boolean') {
                throw new Error('[waitForIt] Timeout reached');
            }
            const message = checkResultValue ? `. ${checkResultValue}` : '';
            throw new Error(`[waitForIt] Timeout reached${message}`);
        }
        await sleep(trialDelayMs);
        return doWaitFor();
    }
}
exports.waitForIt = waitForIt;
