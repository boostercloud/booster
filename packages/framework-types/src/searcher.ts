import { Class } from './typelevel'

export type SearcherFunction<TObject> = (className: string, filters: Record<string, Filter<any>>) => Promise<Array<any>>

export class Searcher<TObject> {
  // private offset?: number
  // private limit?: number
  readonly filters: Record<string, Filter<any>> = {}

  public constructor(
    private readonly objectClass: Class<TObject>,
    private readonly searcherFunction: SearcherFunction<TObject>
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
    const searchResult = await this.searcherFunction(this.objectClass.name, this.filters)
    return searchResult as Array<TObject>
  }
}

export interface Filter<TType> {
  operation: Operation<TType>
  values: Array<TType>
}

export enum NumberOperations {
  '=' = 'eq',
  '!=' = 'not_eq',
  '<' = 'less',
  '>' = 'greater',
  '<=' = 'less_eq',
  '>=' = 'greater_eq',
  'in' = 'in',
  'between' = 'between',
}

export enum StringOperations {
  '=' = 'eq',
  '!=' = 'notEq',
  '<' = 'less',
  '>' = 'greater',
  '<=' = 'less_eq',
  '>=' = 'greater_eq',
  'in' = 'in',
  'between' = 'between',
  'contains' = 'contains',
  'not-contains' = 'not_contains',
  'begins-with' = 'begins_with',
}

export enum BooleanOperations {
  '=' = 'equal',
  '!=' = 'not_equal',
}

// eslint-disable-next-line prettier/prettier
type Operation<TType> =
  TType extends number ? EnumToUnion<typeof NumberOperations> :
  TType extends string ? EnumToUnion<typeof StringOperations> :
  TType extends boolean ? EnumToUnion<typeof BooleanOperations> : never

type EnumToUnion<TEnum> = keyof TEnum
