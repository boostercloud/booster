import { EntityInterface } from './concepts'
import { BoosterConfig } from './config'
import { Logger } from './logger'
import { Class } from "./typelevel";

export class Searcher<TObject extends EntityInterface> {
  // private offset?: number
  // private limit?: number
  readonly filters: Record<string, Filter<any>> = {}

  public constructor(
    private readonly config: BoosterConfig,
    private readonly logger: Logger,
    private readonly objectClass: Class<TObject>
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

  public async searchOne(): Promise<TObject> {
    // Optimize if there is only an ID filter with one value
    // this.provider.fetchEntitySnapshot(this.entityClass.name, id)
    return (await this.search())[0]
  }

  public async search(): Promise<Array<TObject>> {
    this.logger.debug('Sending a search operation to provider with filters: ', this.filters)
    const searchResult = await this.config.provider.searchEntity(
      this.config,
      this.logger,
      this.objectClass.name,
      this.filters
    )
    return searchResult as Array<TObject>
  }
}

export interface Filter<TType> {
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
  'in' = 'in',
  'between' = 'between',
}

export enum StringOperations {
  '=' = '=',
  '!=' = '!=',
  '<' = '<',
  '>' = '>',
  '>=' = '>=',
  '<=' = '<=',
  'in' = 'in',
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
