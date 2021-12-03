export type ClassType = { new (...args: unknown[]): unknown }

export enum TypeGroup {
  String = 'String',
  Number = 'Number',
  Boolean = 'Boolean',
  Enum = 'Enum',
  Union = 'Union',
  Intersection = 'Intersection',
  Function = 'Function',
  Class = 'Class',
  Interface = 'Interface',
  Type = 'Type',
  Array = 'Array',
  Object = 'Object',
  Other = 'Other',
}
export interface TypeMetadata {
  name: string
  typeGroup: TypeGroup
  parameters: Array<TypeMetadata>
  isNullable: boolean
  typeName?: string
  importPath?: string
  type?: ClassType
}

export interface PropertyMetadata {
  name: string
  typeInfo: TypeMetadata
}

export interface ClassMetadata {
  name: string
  type: ClassType
  fields: Array<PropertyMetadata>
  methods: Array<PropertyMetadata>
}
