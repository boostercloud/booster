import * as Fathom from 'fathom-client'

export class AnalyticsClient {
  static start() {
    Fathom.load('LHRTIPFZ', { url: 'https://tl1.boosterframework.com/script.js' })
  }

  static trackEvent(event: string) {
    Fathom.trackGoal(event, 0)
  }
}
