/* eslint-disable @typescript-eslint/ban-ts-comment */

import { ClassDeclaration, ClassInstancePropertyTypes, createWrappedNode, Node, SyntaxKind, Type } from 'ts-morph'
import * as ts from 'typescript'
import { makeModuleLogger } from './logging'
import { ClassMetadata, getTypeGroup, TypeMetadata } from './metadata-types'
import { TypeCache } from './type-cache'

const makeLogger = makeModuleLogger(module.filename)

export function getClassInfo(
  classNode: ts.ClassDeclaration & ts.Node,
  checker: ts.TypeChecker,
  cache: TypeCache
): ClassMetadata | undefined {
  const logger = makeLogger(getClassInfo.name)
  if (!classNode.name) return

  const node = createWrappedNode<ts.Node>(classNode, { typeChecker: checker }).asKindOrThrow(
    SyntaxKind.ClassDeclaration
  )

  return {
    name: node.getNameOrThrow(),
    fields: getInstanceProperties(node).map((p) => {
      logger.debug(
        ' %s - Found field %s with type %s',
        p.getSourceFile().getFilePath(),
        p.getName(),
        p.getType().getText(p)
      )
      return {
        name: p.getName(),
        typeInfo: getTypeInfo(p.getType(), cache, p),
      }
    }),
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
  const logger = makeLogger(getTypeInfo.name)
  const typeInfo: TypeMetadata = makeTypeInfo(type, node)
  const cachedType = cache.getType(typeInfo, true)
  if (cachedType) {
    return cachedType
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
        const cachedParam = cache.getType(makeTypeInfo(a, node), true)
        return cachedParam ?? getTypeInfo(a, cache, node)
      })
  }

  logger.debug('Assigning type name for %s, with type group of %s', typeInfo.name, typeInfo.typeGroup)
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
    case 'Array': {
      const symbolName = type.getSymbol()?.getName()
      logger.debug('Got symbol name %s', symbolName)
      // getSymbol() is used for complex types, in which cases getText() returns too much information (e.g. Map<User> instead of just Map)
      typeInfo.typeName = symbolName || 'Array'
      break
    }
    case 'Object':
      typeInfo.typeName = type.getSymbol()?.getName() || 'Object'
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
    default:
      typeInfo.typeName = ''
      break
  }

  if (typeInfo.typeName === '') throw new Error(`Could not extract typeName for type ${JSON.stringify(typeInfo)}`)
  cache.saveType({ typeInfo, forceSave: true })

  return typeInfo
}

function makeTypeInfo(type: Type, node?: Node): TypeMetadata {
  const typeName = type.getText(node) // node is passed for better name printing: https://github.com/dsherret/ts-morph/issues/907
  const hasQuestionToken = hasQuestionTokenNode(node)
  const isNullable = type.isNullable() || hasQuestionToken
  type = type.getNonNullableType()

  return {
    name: typeName,
    typeName: undefined,
    typeGroup: getTypeGroup(type),
    isNullable,
    parameters: [],
  }
}
