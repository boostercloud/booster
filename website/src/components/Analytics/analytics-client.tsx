import * as Fathom from 'fathom-client';

export class AnalyticsClient {
    static start() {
        Fathom.load('LHRTIPFZ')
    }

    static trackEvent(event: string) {
        Fathom.trackGoal(event, 0)
    }
}