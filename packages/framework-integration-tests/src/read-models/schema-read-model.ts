import { NonExposed, Projects, ReadModel } from '@boostercloud/framework-core'
import { Cart, MigratedCart } from '../entities/cart'
import { ProjectionResult, UUID } from '@boostercloud/framework-types'

class GenericClass<T> {
  constructor(readonly genericValue: T, @NonExposed nonExposedGenericeParameter?: number) {}
}

class BaseClass {
  constructor(readonly base: string, @NonExposed nonExposedBaseParameter?: number) {}
}

@ReadModel({
  authorize: 'all',
})
export class SchemaReadModel {
  readonly readOnlyProperty?: string = '1'
  private privateProperty?: string = '1'
  public publicProperty?: string = '1'
  @NonExposed nonExposedProperty?: number

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
    readonly readonlyArray?: ReadonlyArray<string>,
    @NonExposed nonExposedParameter?: number
  ) {
    console.log(this.readOnlyProperty)
    console.log(this.privateProperty)
    console.log(this.publicProperty)
  }

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
