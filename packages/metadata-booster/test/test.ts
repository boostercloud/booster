/* eslint-disable @typescript-eslint/no-unused-vars, @typescript-eslint/no-explicit-any */
/**
 * This file is transpiled with metadata when executing "npm run test".
 * The result will be available in dist/test/test.js
 */

class User {
  constructor(public name: string, public friends: Set<User>) {}
}

enum Size {
  Small,
  Medium,
  Big,
}

class Car {
  constructor(public driversMap: Map<string, User>, public size: Size) {}

  public engageAutoPilot(): Promise<boolean> {
    // Assume a long task here
    return Promise.resolve(true)
  }
}

class GenericClass<T> {
  constructor(readonly genericValue: T) {}
}

class Animal {
  constructor(readonly animalProp: string) {}
}

class Dog extends Animal {
  readonly dogProp!: string
}

class Chihuahua extends Dog {}

class Test {
  constructor(
    public array0: string[],
    public array1: Array<string>,
    public unionArrays: Array<string> | Array<number>,
    public unionWithNull: string | null, // typeGroup = String, nullable = true
    public unionWithUndefined: string | undefined, // typeGroup = String, nullable = true
    public unionWithAny: string | any, // typeGroup = Other, nullable = false
    public unionWithObject: string | Car,
    public unionWithUnknown: string | unknown, // typeGroup = Other, nullable = false
    public intersection0: Array<string> & Array<number>,
    public func0: (arg0: string) => void,
    public any0: any,
    public unknown0: unknown, // typeGroup = Other
    public record: Record<string, undefined>, // typeGroup = Type
    public generic: GenericClass<string>, // typeGroup: "Object", typeName = GenericClass, parameter[typeGroup] = string
    public inheritedProps: Chihuahua,
    public optionalString?: string, // typeGroup = String, nullable = true
    public optionalNull?: string, // typeGroup = String, nullable = true
    public optionalUndefined?: undefined, // typeGroup = Other, nullable = true
    public optionalUnknown?: unknown, // typeGroup = Other, nullable = true
    public optionalAny?: any, // typeGroup = Other, nullable = true
    public optionalRecord?: Record<string, undefined>, // typeGroup = Type, nullable = true
    public optionalGeneric?: GenericClass<Car>, // typeGroup: "Object", typeName = GenericClass, nullable: true, parameter[typeGroup] = string
    public readonlyArray?: ReadonlyArray<string> // typeGroup: "ReadonlyArray", typeName = ReadonlyArray, nullable: true, parameter[typeGroup] = string
  ) {}
}
