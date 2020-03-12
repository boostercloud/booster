import { BoosterConfig, Class, Logger } from '@boostercloud/framework-types'

export class Searcher<TObject> {
  // private offset?: number
  // private limit?: number
  readonly filters: Record<string, Filter<any>> = {}

  public constructor(
    private readonly config: BoosterConfig,
    private readonly logger: Logger,
    private readonly entityClass: Class<TObject>
  ) {}

  public filter<TPropName extends keyof TObject, TPropType extends TObject[TPropName]>(
    property: TPropName,
    filter: Filter<TPropType>
  ): this {
    this.filters[property as string] = filter
    return this
  }

  public searchOne(): TObject {
    this.logger.info(this.config)
    console.log(this.entityClass)
    throw new Error('Not implemented')
  }

  public search(): Array<TObject> {
    throw new Error('Not implemented')
  }
}

export class Filter<TPropType> {
  private constructor(readonly operation: Operation, readonly values: Array<TPropType>) {}

  public static Equal<TPropType>(value: TPropType): Filter<TPropType> {
    return new Filter('=', [value])
  }
}

type Operation = '=' | '!=' | '<' | '>' | '>=' | '<=' | 'between'
