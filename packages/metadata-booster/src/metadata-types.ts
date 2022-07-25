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
