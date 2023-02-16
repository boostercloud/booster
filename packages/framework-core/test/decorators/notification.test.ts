/* eslint-disable @typescript-eslint/no-unused-vars */
/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
import { expect } from '../expect'
import { Notification, partitionKey } from '../../src/decorators'
import { Booster } from '../../src'

describe('the `Notification` decorator', () => {
  afterEach(() => {
    Booster.configureCurrentEnv((config: any) => {
      config.notifications = {}
      config.topicToEvent = {}
    })
  })

  it('add the event class as an event', () => {
    @Notification()
    class ANotification {
      public constructor() {}
    }
    expect(Booster.config.notifications[ANotification.name]).to.deep.equal({
      class: ANotification,
    })
  })

  it('sets the topic in the config, if specified', () => {
    @Notification({ topic: 'my-topic' })
    class ANotification {
      public constructor() {}
    }

    expect(Booster.config.notifications[ANotification.name]).to.deep.equal({
      class: ANotification,
    })

    expect(Booster.config.topicToEvent['my-topic']).to.deep.equal(ANotification.name)
  })

  it('sets the partitionKey in the config, if specified', () => {
    @Notification()
    class ANotification {
      public constructor(@partitionKey readonly key: string) {}
    }

    expect(Booster.config.notifications[ANotification.name]).to.deep.equal({
      class: ANotification,
    })

    expect(Booster.config.partitionKeys[ANotification.name]).to.equal('key')
  })
})
