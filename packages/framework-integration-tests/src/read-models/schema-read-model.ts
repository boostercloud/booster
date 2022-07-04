import { Projects, ReadModel } from '@boostercloud/framework-core'
import { Cart, MigratedCart } from '../entities/cart'
import { ProjectionResult, UUID } from '@boostercloud/framework-types'

class GenericClass<T> {
  constructor(readonly genericValue: T) {}
}

class BaseClass {
  constructor(readonly base: string) {}
}

@ReadModel({
  authorize: 'all',
})
export class SchemaReadModel {
  constructor(
    readonly id: UUID,
    readonly date: Date,
    readonly array0: string[],
    readonly array1: Array<string>,
    readonly unionArrays: Array<string> | Array<number>,
    readonly unionWithNull: string | null,
    readonly unionWithUndefined: string | undefined,
    readonly unionWithAny: string | any,
    readonly unionWithObject: string | Cart,
    readonly unionWithUnknown: string | unknown,
    readonly func0: (arg0: string) => void,
    readonly any0: any,
    readonly unknown0: unknown,
    readonly record: Record<string, string>,
    readonly generic: GenericClass<string>,
    readonly child: BaseClass,
    readonly optionalString?: string,
    readonly optionalNull?: string,
    readonly optionalUndefined?: undefined,
    readonly optionalUnknown?: unknown,
    readonly optionalAny?: any,
    readonly optionalRecord?: Record<string, string>,
    readonly optionalGeneric?: GenericClass<Cart>,
    readonly optionalChild?: BaseClass,
    readonly readonlyArray?: ReadonlyArray<string>
  ) {}

  @Projects(Cart, 'id')
  public static ignoreChange(
    cart: Cart | MigratedCart,
    oldSchemaReadModel?: SchemaReadModel
  ): ProjectionResult<SchemaReadModel> {
    return new SchemaReadModel(
      UUID.generate(),
      new Date(),
      [],
      [],
      [],
      null,
      'test',
      'test',
      'test',
      'test',
      (arg0) => {},
      'test',
      'test',
      {
        test: 'test',
      },
      new GenericClass<string>('test'),
      new BaseClass('base')
    )
  }
}
