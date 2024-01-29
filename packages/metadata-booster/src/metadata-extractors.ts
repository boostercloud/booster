/* eslint-disable @typescript-eslint/ban-ts-comment */

import { ClassDeclaration, ClassInstancePropertyTypes, createWrappedNode, Node, SyntaxKind, Type } from 'ts-morph'
// TODO: Import properly
import * as ts from 'typescript'
import { TypeGroup } from './metadata-types'

export interface TypeInfo {
  name: string // e.g. Array<string>
  typeName: string | null // e.g. Array
  parameters: Array<TypeInfo>
  typeGroup: TypeGroup
  isNullable: boolean
  isGetAccessor: boolean
}

export interface PropertyInfo {
  name: string
  typeInfo: TypeInfo
}

export interface ClassInfo {
  name: string
  fields: Array<PropertyInfo>
  methods: Array<PropertyInfo>
}

export function getClassInfo(classNode: ts.ClassDeclaration & ts.Node, checker: ts.TypeChecker): ClassInfo | undefined {
  if (!classNode.name) return

  const node = createWrappedNode<ts.Node>(classNode, { typeChecker: checker }).asKindOrThrow(
    SyntaxKind.ClassDeclaration
  )

  return {
    name: node.getNameOrThrow(),
    fields: getInstanceProperties(node).map((p) => ({ name: p.getName(), typeInfo: getTypeInfo(p.getType(), p) })),
    methods: node.getMethods().map((m) => ({ name: m.getName(), typeInfo: getTypeInfo(m.getReturnType(), m) })),
  }
}

function getInstanceProperties(classDeclaration?: ClassDeclaration): Array<ClassInstancePropertyTypes> {
  if (classDeclaration == undefined) {
    return []
  }
  // Ensure to get the properties of the base classes too
  return [...getInstanceProperties(classDeclaration.getBaseClass()), ...classDeclaration.getInstanceProperties()]
}

function hasQuestionTokenNode(node: Node<ts.Node> | undefined): boolean {
  // @ts-ignore
  if (node && typeof node['hasQuestionToken'] === 'function') {
    // @ts-ignore
    return node?.hasQuestionToken()
  }
  return false
}

function getTypeInfo(tp: Type, nd?: Node): TypeInfo {
  return go(tp, 0, nd)

  function go(type: Type, depth: number, node?: Node): TypeInfo {
    const { name, isNullable } = getTypeInfoNameSafe(type, node)
    const isGetAccessor = Node.isGetAccessorDeclaration(node)

    /*
      This metadata is used for DTOs, since some of the types
      introduced in newer versions of packages are recursive,
      without a depth limit this will go into an infinite loop.
      Eight levels should be enough for any DTO.
    */
    if (8 < depth) {
      return {
        name,
        typeName: null,
        typeGroup: 'Other',
        isNullable,
        parameters: [],
        isGetAccessor,
      }
    }

    const typeGroupTuples: [(t: Type) => boolean, TypeGroup][] = [
      [(t) => t.isString(), 'String'],
      [(t) => t.isNumber(), 'Number'],
      [(t) => t.isBoolean(), 'Boolean'],
      [(t) => t.isEnum(), 'Enum'],
      [(t) => t.isUnion(), 'Union'],
      [(t) => t.isIntersection(), 'Intersection'],
      [(t) => t.isClass(), 'Class'],
      [(t) => t.isInterface(), 'Interface'],
      [(t) => t.getAliasSymbol() != null, 'Type'],
      [isReadonlyArray, 'ReadonlyArray'],
      [(t) => t.isArray(), 'Array'],
      [(t) => t.getCallSignatures().length > 0, 'Function'],
      [(t) => t.isObject(), 'Object'],
    ]

    type = type.getNonNullableType()
    const typeInfo: TypeInfo = {
      name,
      typeName: '',
      typeGroup: typeGroupTuples.find(([fn]) => fn(type))?.[1] || 'Other',
      isNullable,
      parameters: [],
      isGetAccessor,
    }
    switch (typeInfo.typeGroup) {
      case 'Enum':
        typeInfo.parameters = type.getUnionTypes().map((t) => go(t, depth + 1))
        break
      case 'Union':
        typeInfo.parameters = type.getUnionTypes().map((t) => go(t, depth + 1, node))
        break
      case 'Intersection':
        typeInfo.parameters = type.getIntersectionTypes().map((t) => go(t, depth + 1, node))
        break
      default:
        typeInfo.parameters = type.getTypeArguments().map((a) => go(a, depth + 1, node))
    }

    // typeName is used for referencing the type in the metadata
    switch (typeInfo.typeGroup) {
      case 'String':
      case 'Number':
      case 'Boolean':
        typeInfo.typeName = typeInfo.typeGroup
        break
      case 'Union':
      case 'Intersection':
        typeInfo.typeName = null
        break
      case 'Enum':
      case 'Class':
      case 'ReadonlyArray':
      case 'Array':
        // getSymbol() is used for complex types, in which cases getText() returns too much information (e.g. Map<User> instead of just Map)
        typeInfo.typeName = type.getSymbol()?.getName() || ''
        break
      case 'Object':
        typeInfo.typeName = type.getSymbol()?.getName() || ''
        if (typeInfo.typeName === '__type') {
          // This happens for literal objects like `{ a: string, b: { c: string } }`
          typeInfo.typeName = 'Object'
        }
        break
      case 'Interface':
      case 'Type':
      case 'Function':
      case 'Other':
        if (type.isEnumLiteral()) {
          typeInfo.name = type.getSymbol()?.getName() || '' // e.g. "Small"
        }
        typeInfo.typeName = null
        break
    }

    if (typeInfo.typeName === '') {
      typeInfo.typeName = typeInfo.name
    }

    if (typeInfo.typeName === '')
      throw new Error(`
    Could not extract typeName for type ${JSON.stringify(typeInfo)}

    This is probably a bug in the metadata extractor.

    More information
    ----------------

    typeInfo: ${JSON.stringify(typeInfo)}
    type: ${JSON.stringify(type.getText())}
    node: ${JSON.stringify(node?.getText())}
    depth: ${depth}
    `)

    return typeInfo
  }

  function getTypeInfoNameSafe(type: Type, node?: Node): { name: string; isNullable: boolean } {
    const isNullable = type.isNullable() || hasQuestionTokenNode(node)
    // node is passed for better name printing: https://github.com/dsherret/ts-morph/issues/907
    const name = isNullable
      ? // Since the update of packages of May, 4th 2023, this is adding "undefined" and/or "null" to nullables.
        type.getText(node).replace(' | undefined', '').replace(' | null', '')
      : type.getText(node)

    return { name, isNullable }
  }

  function isReadonlyArray(t: Type): boolean {
    return t.isObject() && (t.getSymbol()?.getName() || '') === 'ReadonlyArray'
  }
}
