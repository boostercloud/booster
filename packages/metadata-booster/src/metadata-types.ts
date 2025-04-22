export type ClassType = { new (...args: unknown[]): unknown }

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

export interface TypeMetadata {
  name: string
  typeGroup: TypeGroup
  parameters: Array<TypeMetadata>
  isNullable: boolean
  isGetAccessor: boolean
  typeName?: string
  importPath?: string
  type?: ClassType
}

export interface PropertyMetadata {
  name: string
  typeInfo: TypeMetadata
  dependencies: Array<string>
}

export interface ClassMetadata {
  name: string
  type: ClassType
  fields: Array<PropertyMetadata>
  methods: Array<PropertyMetadata>
}
