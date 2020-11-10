/* eslint-disable @typescript-eslint/no-explicit-any */
import { Class } from './typelevel'

export type SearcherFunction<TObject> = (
  className: string,
  filters: Record<string, FilterOld<any>>
) => Promise<Array<any>>

/**
 * This class represents a search intended to be run by any search provider. They way you use it
 * is by setting filters on the properties of the object you want to search and then run it.
 * Check the documentation on the individual methods to know more about how to do so.
 */
export class Searcher<TObject> {
  // private offset?: number
  // private limit?: number
  private filters: FilterFor<TObject> = {}
  /** @deprecated */
  readonly filtersOld: Record<string, FilterOld<any>> = {}

  /**
   * @param objectClass The class of the object you want to run the search for.
   * @param searcherFunction The function that will receive all the filters and run the actual search
   */
  public constructor(
    private readonly objectClass: Class<TObject>,
    private readonly searcherFunction: SearcherFunction<TObject>
  ) {}

  public filter(filters: FilterFor<TObject>): this {
    this.filters = filters
    return this
  }

  /**
   * Adds a filter for the search. For example: If you want to search for people whose age is greater than 30
   * and their height is between 1.80m and 2.00m, you would do:
   * ```
   * searcher.filter('age', 'gt', 30)
   *         .filter('height', 'between', 1.8, 2)
   *         .search()
   * ```
   * @param property The property the filter will act upon
   * @param operation The filter operation.
   * @param values The values for the filter. Depending on the operation, you can specify here one or many values
   * @deprecated Use "filter" instead
   */
  public filterOld<TPropName extends keyof TObject, TPropType extends TObject[TPropName]>(
    property: TPropName,
    operation: OperationOld<TPropType>,
    ...values: Array<TPropType>
  ): this {
    this.filtersOld[property as string] = {
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

  /**
   * Do the actual search by sending all the configured filters to the provided search function
   */
  public async search(): Promise<Array<TObject>> {
    console.log(this.filters)
    const searchResult = await this.searcherFunction(this.objectClass.name, this.filtersOld)
    return searchResult as Array<TObject>
  }
}

// ---------------- DEPRECATED ----------------------
export interface FilterOld<TType> {
  operation: OperationOld<TType>
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
type OperationOld<TType> =
  TType extends number ? EnumToUnion<typeof NumberOperations> :
  TType extends string ? EnumToUnion<typeof StringOperations> :
  TType extends boolean ? EnumToUnion<typeof BooleanOperations> : never

type EnumToUnion<TEnum> = keyof TEnum

// ----------------------------------------------------------------------------------------------------

type FilterFor<TType> = {
  [TProp in keyof TType]?: Operation<TType[TProp]>
} &
  FilterCombinators<TType>

interface FilterCombinators<TType> {
  and?: Array<FilterFor<TType>>
  or?: Array<FilterFor<TType>>
  not?: FilterFor<TType>
}

type Operation<TType> =
  TType extends Array<infer TElementType> ? ArrayOperators<TElementType>
  : TType extends string ? StringOperators<TType>
  : TType extends number ? ScalarOperators<TType>
  : TType extends boolean ? BooleanOperators<TType>
  : TType extends Record<string, any> ? FilterFor<TType>
  : never

interface BooleanOperators<TType> {
  eq?: TType
  ne?: TType
}
interface ScalarOperators<TType> extends BooleanOperators<TType> {
  gt?: TType
  gte?: TType
  lt?: TType
  lte?: TType
  in?: TType
}

interface StringOperators<TType> extends ScalarOperators<TType> {
  beginsWith?: TType
  contains?: TType
}

interface ArrayOperators<TElementType> {
  includes?: TElementType
}

// ----------------------------

class Money {
  constructor(public cents: number, public currency: string) {}
}

class Item {
  constructor(public sku: string, public price: Money) {}
}

class Product {
  constructor(
    readonly id: string,
    readonly stock: number,
    public mainItem: Item,
    public items: Array<Item>,
    public buyers: Array<string>,
    public days: Array<number>,
    public pairs: Array<Array<number>>
  ) {}
}

const filter: FilterFor<Product> = {
  id: { beginsWith: 'pepe' },
  stock: { lte: 90 },
  mainItem: { price: { cents: { eq: 4 } } },
  buyers: { includes: '123' },
  days: { includes: 34 },
  items: { includes: { sku: '2', price: { cents: 8, currency: 'EUR' } } },
  pairs: {
    includes: [8],
  },
}

console.log(filter)
