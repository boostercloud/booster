import { BoosterConfig } from '@boostercloud/framework-types'
import { getLogger } from '@boostercloud/framework-common-helpers'
import fetch from 'node-fetch'
import * as redis from 'redis'

export class RedisAdapter {
  public static keySeparator = '_____'

  private _client?: redis.RedisClient
  constructor(readonly daprUrl: string, readonly redisUrl: string) {}

  public get client(): redis.RedisClient {
    if (!this._client) {
      this._client = redis.createClient({ url: this.redisUrl })
    }
    return this._client
  }

  public static build(): RedisAdapter {
    const redisHost: string = process.env['DB_HOST'] || 'host' //TODO: Manage the failing process of the redis inizialization in Kubernetes provider
    const redisPwd: string = process.env['DB_PASSWORD'] || 'password'
    const redisUrl = `redis://${redisHost}?password=${redisPwd}`
    return new RedisAdapter('http://localhost:3500', redisUrl)
  }

  public async set(config: BoosterConfig, key: string, value: unknown): Promise<void> {
    const logger = getLogger(config, 'RedisAdapter#set')
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
      const error = response.text()
      throw error
    }
  }

  public async keys(config: BoosterConfig, keyPattern: string): Promise<Array<string>> {
    const logger = getLogger(config, 'RedisAdapter#keys')
    logger.debug('RedisAdapter keys')
    return new Promise((resolve) => {
      this.client.keys(`booster||${keyPattern}*`, function (error: Error | null, response: Array<string>) {
        if (error) {
          logger.debug(error)
          return resolve([])
        }
        resolve(response)
      })
    })
  }

  public async hget<TResult>(key: string): Promise<TResult | void> {
    return new Promise((resolve) =>
      this.client.hget(key, 'data', (error: Error | null, response: string) => {
        if (error) return resolve()
        resolve(JSON.parse(response))
      })
    )
  }

  public async setViaRedis(config: BoosterConfig, key: string, value: string): Promise<void> {
    const logger = getLogger(config, 'RedisAdapter#setViaRedis')
    const response = this.client.set('booster||' + key, value)
    logger.debug(response)
    logger.debug('END RedisAdapter setViaRedis')
  }
}
