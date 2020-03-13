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
    operation: Operation<TPropType>,
    ...values: Array<TPropType>
  ): this {
    this.filters[property as string] = {
      operation,
      values,
    }
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

interface Filter<TType> {
  operation: Operation<TType>
  values: Array<TType>
}

export enum NumberOperations {
  '=' = '=',
  '!=' = '!=',
  '<' = '<',
  '>' = '>',
  '>=' = '>=',
  '<=' = '<=',
  'between' = 'between',
}

export enum StringOperations {
  '=' = '=',
  '!=' = '!=',
  '<' = '<',
  '>' = '>',
  '>=' = '>=',
  '<=' = '<=',
  'between' = 'between',
  'contains' = 'contains',
  'not-contains' = 'not-contains',
  'begins-with' = 'begins-with',
}

export enum BooleanOperations {
  '=' = '=',
  '!=' = '!=',
}

// eslint-disable-next-line prettier/prettier
type Operation<TType> =
  TType extends number ? EnumToUnion<typeof NumberOperations> :
  TType extends string ? EnumToUnion<typeof StringOperations> :
  TType extends boolean ? EnumToUnion<typeof BooleanOperations> : never

type EnumToUnion<TEnum> = keyof TEnum
