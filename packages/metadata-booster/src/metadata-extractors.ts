/* eslint-disable @typescript-eslint/ban-ts-comment */

import { ClassDeclaration, ClassInstancePropertyTypes, createWrappedNode, Node, SyntaxKind, Type } from 'ts-morph'
import * as ts from 'typescript'
import { ClassMetadata, TypeGroup, TypeMetadata } from './metadata-types'
import { TypeCache } from './type-information'

export function getClassInfo(
  classNode: ts.ClassDeclaration & ts.Node,
  checker: ts.TypeChecker,
  cache: TypeCache
): ClassMetadata | undefined {
  if (!classNode.name) return

  const node = createWrappedNode<ts.Node>(classNode, { typeChecker: checker }).asKindOrThrow(
    SyntaxKind.ClassDeclaration
  )

  return {
    name: node.getNameOrThrow(),
    fields: getInstanceProperties(node).map((p) => ({
      name: p.getName(),
      typeInfo: getTypeInfo(p.getType(), cache, p),
    })),
    methods: node.getMethods().map((m) => ({ name: m.getName(), typeInfo: getTypeInfo(m.getReturnType(), cache, m) })),
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

function getTypeInfo(type: Type, cache: TypeCache, node?: Node): TypeMetadata {
  const typeName = type.getText(node) // node is passed for better name printing: https://github.com/dsherret/ts-morph/issues/907
  const cachedType = cache.getType(typeName)
  if (cachedType) {
    console.log(`Type ${typeName} was cached, returning`)
    return cachedType
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
    [(t) => t.isArray(), 'Array'],
    [(t) => t.getCallSignatures().length > 0, 'Function'],
    [(t) => isReadonlyArray(t), 'ReadonlyArray'],
    [(t) => t.isObject(), 'Object'],
  ]

  const hasQuestionToken = hasQuestionTokenNode(node)
  const isNullable = type.isNullable() || hasQuestionToken
  type = type.getNonNullableType()
  const typeInfo: TypeMetadata = {
    name: typeName,
    typeName: '',
    typeGroup: typeGroupTuples.find(([fn]) => fn(type))?.[1] || 'Other',
    isNullable,
    parameters: [],
  }
  cache.saveType({ typeInfo })
  switch (typeInfo.typeGroup) {
    case 'Enum':
      typeInfo.parameters = type.getUnionTypes().map((t) => getTypeInfo(t, cache))
      break
    case 'Union':
      typeInfo.parameters = type.getUnionTypes().map((t) => getTypeInfo(t, cache, node))
      break
    case 'Intersection':
      typeInfo.parameters = type.getIntersectionTypes().map((t) => getTypeInfo(t, cache, node))
      break
    default:
      typeInfo.parameters = type.getTypeArguments().map((a) => {
        const cachedParam = cache.getType(a.getText())
        return cachedParam ?? getTypeInfo(a, cache, node)
      })
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
      typeInfo.typeName = undefined
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
      typeInfo.typeName = undefined
      break
  }

  if (typeInfo.typeName === '') throw new Error(`Could not extract typeName for type ${JSON.stringify(typeInfo)}`)
  cache.saveType({ typeInfo, forceSave: true })

  return typeInfo
}

function isReadonlyArray(t: Type): boolean {
  return t.isObject() && (t.getSymbol()?.getName() || '') === 'ReadonlyArray'
}
