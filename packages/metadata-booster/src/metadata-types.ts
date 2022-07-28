import { Type } from 'ts-morph'
import { match } from 'ts-pattern'

// type instead of enum to be able to install this package as a devDependency and not a production dependency
export type TypeGroup =
  | 'String'
  | 'Number'
  | 'Boolean'
  | 'Enum'
  | 'Union'
  | 'Intersection'
  | 'Function'
  | 'Class'
  | 'Interface'
  | 'Type'
  | 'Array'
  | 'Object'
  | 'ReadonlyArray'
  | 'Other'

const isReadonlyArray = (t: Type): boolean => t.isObject() && (t.getSymbol()?.getName() || '') === 'ReadonlyArray'

export const getTypeGroup = (type: Type): TypeGroup =>
  match<Type, TypeGroup>(type)
    .when(
      (t) => t.isString(),
      () => 'String'
    )
    .when(
      (t) => t.isNumber(),
      () => 'Number'
    )
    .when(
      (t) => t.isBoolean(),
      () => 'Boolean'
    )
    .when(
      (t) => t.isEnum(),
      () => 'Enum'
    )
    .when(
      (t) => t.isUnion(),
      () => 'Union'
    )
    .when(
      (t) => t.isIntersection(),
      () => 'Intersection'
    )
    .when(
      (t) => t.isClass(),
      () => 'Class'
    )
    .when(
      (t) => t.isInterface(),
      () => 'Interface'
    )
    .when(
      (t) => t.getAliasSymbol() != null,
      () => 'Type'
    )
    .when(
      (t) => t.isArray(),
      () => 'Array'
    )
    .when(
      (t) => t.getCallSignatures().length > 0,
      () => 'Function'
    )
    .when(
      (t) => isReadonlyArray(t),
      () => 'ReadonlyArray'
    )
    .when(
      (t) => t.isObject(),
      () => 'Object'
    )
    .otherwise(() => 'Other')

// TODO: Add a variant as a union type so it is compatible with morphic-ts or io-ts instead of using new
export type TypeBuilder = { new (...args: unknown[]): unknown }

export interface HasTypeBuilder {
  type?: TypeBuilder
}

export type TypeMetadata = {
  name: string
  typeName?: string
  parameters: Array<TypeMetadata>
  typeGroup: TypeGroup
  isNullable: boolean
  importPath?: string
} & HasTypeBuilder

export type PropertyMetadata = {
  name: string
  typeInfo: TypeMetadata
} & HasTypeBuilder

export type ClassMetadata = {
  name: string
  fields: Array<PropertyMetadata>
  methods: Array<PropertyMetadata>
} & HasTypeBuilder
