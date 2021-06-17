/* eslint-disable @typescript-eslint/no-explicit-any, @typescript-eslint/indent */
import { UUID } from './concepts'
import { Class } from './typelevel'

export type SearcherFunction<TObject> = (className: string, filters: FilterFor<TObject>) => Promise<Array<any>>

/**
 * This class represents a search intended to be run by any search provider. They way you use it
 * is by setting filters on the properties of the object you want to search and then run it.
 * Check the documentation on the individual methods to know more about how to do so.
 */
export class Searcher<TObject> {
  // private offset?: number
  // private limit?: number
  private filters: FilterFor<TObject> = {}

  /**
   * @param objectClass The class of the object you want to run the search for.
   * @param searcherFunction The function that will receive all the filters and run the actual search
   */
  public constructor(
    private readonly objectClass: Class<TObject>,
    private readonly searcherFunction: SearcherFunction<TObject>
  ) {}

  /**
   * Adds a filter for the search. For example: If you want to search for people whose age is greater than 30
   * and their height is between 1.80m and 2.00m, you would do:
   * ```
   * searcher.filter({
   *  age: { gt: 30 },
   *  height: { gte: 1.8, lte: 2 }
   * }).search()
   * ```
   * @param filters An object with the property filters
   */
  public filter(filters: FilterFor<TObject>): this {
    this.filters = filters
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
    const searchResult = await this.searcherFunction(this.objectClass.name, this.filters)
    return searchResult as Array<TObject>
  }
}

export type FilterFor<TType> = {
  [TProp in keyof TType]?: Operation<TType[TProp]>
} &
  FilterCombinators<TType>

interface FilterCombinators<TType> {
  and?: Array<FilterFor<TType>>
  or?: Array<FilterFor<TType>>
  not?: FilterFor<TType>
}

export type Operation<TType> = TType extends Array<infer TElementType>
  ? ArrayOperators<TElementType>
  : TType extends string | UUID
  ? StringOperators<TType>
  : TType extends number
  ? ScalarOperators<TType>
  : TType extends boolean
  ? BooleanOperators<TType>
  : TType extends Record<string, any>
  ? FilterFor<TType>
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
  in?: Array<TType>
}

interface StringOperators<TType> extends ScalarOperators<TType> {
  beginsWith?: TType
  contains?: TType
}

interface ArrayOperators<TElementType> {
  includes?: TElementType
}
