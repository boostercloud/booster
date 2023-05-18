/* eslint-disable @typescript-eslint/no-explicit-any, @typescript-eslint/indent */
import { SequenceKey, UUID } from './concepts'
import { ReadModelListResult } from './envelope'
import { AnyClass, Class, ReadOnlyNonEmptyArray } from './typelevel'

export type SearcherFunction<TObject> = (
  objectClass: AnyClass,
  filters: FilterFor<TObject>,
  sortBy: SortFor<TObject>,
  limit?: number,
  afterCursor?: any,
  paginatedVersion?: boolean
) => Promise<Array<TObject> | ReadModelListResult<TObject>>

export type FinderByKeyFunction<TObject> = (
  objectClass: AnyClass,
  id: UUID,
  sequenceKey?: SequenceKey
) => Promise<TObject | ReadOnlyNonEmptyArray<TObject>>

export type SequenceFinderByKeyFunction<TObject> = (
  className: string,
  id: UUID,
  sequenceKey?: SequenceKey
) => Promise<TObject>

/**
 * This class represents a search intended to be run by any search provider. They way you use it
 * is by setting filters on the properties of the object you want to search and then run it.
 * Check the documentation on the individual methods to know more about how to do so.
 */
export class Searcher<TObject> {
  // private offset?: number
  private _limit?: number
  private _afterCursor?: any
  private filters: FilterFor<TObject> = {}
  private _sortByList: SortFor<TObject> = {}
  private _paginatedVersion = false

  /**
   * @param objectClass The class of the object you want to run the search for.
   * @param searcherFunction The function that will receive all the filters and run the actual search
   * @param finderByKeyFunction Function that performs a find by Key operation (Either simple or compound keys)
   */
  public constructor(
    private readonly objectClass: Class<TObject>,
    private readonly searcherFunction: SearcherFunction<TObject>,
    private readonly finderByKeyFunction: FinderByKeyFunction<TObject>
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

  public sortBy(sortBy?: SortFor<TObject>): this {
    if (sortBy) this._sortByList = sortBy
    return this
  }

  public limit(limit?: number): this {
    if (limit) this._limit = limit
    return this
  }

  public afterCursor(afterCursor?: unknown): this {
    if (afterCursor) this._afterCursor = afterCursor
    return this
  }

  public paginatedVersion(paginatedVersion?: boolean): this {
    if (paginatedVersion) this._paginatedVersion = paginatedVersion
    return this
  }

  /**
   * @deprecated Use searchOnce instead
   */
  public async findById(id: UUID, sequenceKey?: SequenceKey): Promise<TObject | ReadOnlyNonEmptyArray<TObject>> {
    return this.finderByKeyFunction(this.objectClass, id, sequenceKey)
  }

  public async searchOne(): Promise<TObject> {
    // TODO: If there is only an ID filter with one value, this should call to `findById`
    const searchResult = await this.searcherFunction(
      this.objectClass,
      this.filters,
      this._sortByList,
      1, // Forces limit 1
      this._afterCursor,
      false // It doesn't make sense to paginate a single result, as pagination metadata would be discarded
    )
    return (searchResult as TObject[])[0]
  }

  /**
   * Do the actual search by sending all the configured filters to the provided search function
   */
  public async search(): Promise<Array<TObject> | ReadModelListResult<TObject>> {
    return this.searcherFunction(
      this.objectClass,
      this.filters,
      this._sortByList,
      this._limit,
      this._afterCursor,
      this._paginatedVersion
    )
  }
}

export type SortFor<TType> = {
  [TProp in keyof TType]?: SortFor<TType[TProp]> | 'ASC' | 'DESC'
}

export type FilterFor<TType> = {
  [TProp in keyof TType]?: Operation<TType[TProp]>
} &
  FilterCombinators<TType> &
  IsDefinedOperator

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

interface IsDefinedOperator {
  isDefined?: boolean
}

interface BooleanOperators<TType> extends IsDefinedOperator {
  eq?: TType | null
  ne?: TType | null
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
  regex?: TType
  iRegex?: TType
}

interface ArrayOperators<TElementType> {
  includes?: TElementType
  isDefined?: boolean
}
