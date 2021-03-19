import { Logger } from 'framework-types/dist'
import fetch from 'node-fetch'
const redis = require('redis')

export class RedisAdapter {
  constructor(readonly daprUrl: string, readonly redisUrl: string) {}

  public static build(): RedisAdapter {
    const redisHost: string = process.env['DB_HOST'] || 'host' //TODO: Manage the failing process of the redis inizialization in Kubernetes provider
    const redisPwd: string = process.env['DB_PASSWORD'] || 'password'
    const redisUrl = `redis://${redisHost}?password=${redisPwd}`
    return new RedisAdapter('http://localhost:3500', redisUrl)
  }

  public async set(key: string, value: any, logger: Logger): Promise<void> {
    const stateUrl = `${this.daprUrl}/v1.0/state/statestore`
    logger.debug('About to post', value)
    const data = [{ key: key, value: value }]
    const response = await fetch(stateUrl, {
      method: 'POST',
      body: JSON.stringify(data),
      headers: {
        'Content-Type': 'application/json',
      },
    })
    if (!response.ok) {
      logger.error("Couldn't store object")
      const err = response.text()
      throw err
    }
  }

  public async keys(keyPattern: string, logger: Logger): Promise<string[] | void> {
    logger.debug('RedisAdapter keys')
    const client = redis.createClient({ url: this.redisUrl })
    const keys = client.keys('booster||' + keyPattern, function(err: any, res: any) {
      if (err) {
        logger.debug(err)
      }
      return keys
    })
  }

  public async setViaRedis(key: string, value: string, logger: Logger): Promise<void> {
    const client = redis.createClient({ url: this.redisUrl })
    const response = client.set('booster||' + key, value)
    logger.debug(response)
    logger.debug('END RedisAdapter setViaRedis')
  }
}
