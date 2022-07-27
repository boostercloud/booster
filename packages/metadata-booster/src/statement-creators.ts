import * as ts from 'typescript'
import { ClassMetadata, PropertyMetadata, TypeMetadata } from './metadata-types'
import { TypeCache } from './type-cache'

export function createClassMetadataDecorator(
  f: ts.NodeFactory,
  classInfo: ClassMetadata,
  filterInterfaceFunctionName: ts.Identifier,
  typesByModule: Record<string, string>,
  cache: TypeCache
): ts.Decorator {
  return f.createDecorator(
    f.createCallExpression(f.createPropertyAccessExpression(f.createIdentifier('Reflect'), 'metadata'), undefined, [
      f.createStringLiteral('booster:typeinfo'),
      f.createObjectLiteralExpression(
        [
          f.createPropertyAssignment('name', f.createStringLiteral(classInfo.name)),
          f.createPropertyAssignment('type', f.createIdentifier(classInfo.name)),
          f.createPropertyAssignment(
            'fields',
            createPropertiesMetadata(f, classInfo.fields, filterInterfaceFunctionName, typesByModule, cache)
          ),
          f.createPropertyAssignment(
            'methods',
            createPropertiesMetadata(f, classInfo.methods, filterInterfaceFunctionName, typesByModule, cache)
          ),
        ],
        true
      ),
    ])
  )
}

function createPropertiesMetadata(
  f: ts.NodeFactory,
  properties: Array<PropertyMetadata>,
  filterInterfaceFunctionName: ts.Identifier,
  typesByModule: Record<string, string>,
  cache: TypeCache
): ts.ArrayLiteralExpression {
  return f.createArrayLiteralExpression(
    properties.map((prop) => {
      return f.createObjectLiteralExpression(
        [
          f.createPropertyAssignment('name', f.createStringLiteral(prop.name)),
          f.createPropertyAssignment(
            'typeInfo',
            createMetadataForTypeInfo(f, prop.typeInfo, filterInterfaceFunctionName, typesByModule, cache)
          ),
        ],
        true
      )
    }, true)
  )
}

let counter = 0

function createMetadataForTypeInfo(
  f: ts.NodeFactory,
  typeInfo: TypeMetadata,
  filterInterfaceFunctionName: ts.Identifier,
  typesByModule: Record<string, string>,
  cache: TypeCache
): ts.ObjectLiteralExpression {
  const cachedType = typeInfo.typeName ? cache.getType(typeInfo.typeName) : cache.getType(typeInfo.name)

  if (cachedType?.statement) {
    cachedType.statementAccesses++
    if (cachedType.statementAccesses > 1) {
      return cachedType.statement
    }
  }
  let shouldCache = true
  const currentLevel = counter
  console.log(new Array(counter).map(() => '  ').join(), 'Calling createMetadataForTypeInfo for type', typeInfo.name)
  counter++
  const typeModule = typeInfo.typeName && typesByModule[typeInfo.typeName]
  const properties: ts.ObjectLiteralElementLike[] = [
    f.createPropertyAssignment('name', f.createStringLiteral(typeInfo.name)),
    f.createPropertyAssignment('typeGroup', f.createStringLiteral(typeInfo.typeGroup)),
    f.createPropertyAssignment('isNullable', typeInfo.isNullable ? f.createTrue() : f.createFalse()),
    f.createPropertyAssignment(
      'parameters',
      f.createArrayLiteralExpression(
        typeInfo.parameters.map((param) =>
          createMetadataForTypeInfo(f, param, filterInterfaceFunctionName, typesByModule, cache)
        )
      )
    ),
  ]
  if (typeModule) properties.push(f.createPropertyAssignment('importPath', f.createStringLiteral(typeModule)))
  if (typeInfo.typeName) {
    properties.push(f.createPropertyAssignment('typeName', f.createStringLiteral(typeInfo.typeName)))
    if (typeModule) {
      shouldCache = false // We don't cache types that use imports because it could happen that the cached reference uses a path that is not reachable from the current file
      console.log('Creating import', typeModule)
      properties.push(
        f.createPropertyAssignment(
          'type',
          f.createPropertyAccessExpression(
            f.createCallExpression(f.createIdentifier('require'), undefined, [f.createStringLiteral(typeModule || '')]),
            f.createIdentifier(typeInfo.typeName)
          )
        )
      )
    } else {
      properties.push(
        f.createPropertyAssignment(
          'type',
          f.createCallExpression(filterInterfaceFunctionName, undefined, [f.createStringLiteral(typeInfo.typeName)])
        )
      )
    }
  }
  counter = currentLevel
  const result = f.createObjectLiteralExpression(properties, true)
  if (shouldCache) {
    cache.saveStatement(typeInfo, result)
  }
  return result
}

export function createFilterInterfaceFunction(
  f: ts.NodeFactory,
  filterInterfaceFunctionName: ts.Identifier
): ts.FunctionDeclaration {
  console.log('Creating function called', filterInterfaceFunctionName)
  return f.createFunctionDeclaration(
    undefined,
    undefined,
    undefined,
    filterInterfaceFunctionName,
    undefined,
    [f.createParameterDeclaration(undefined, undefined, undefined, 'typeName', undefined, undefined, undefined)],
    undefined,
    f.createBlock(
      [
        f.createTryStatement(
          f.createBlock(
            [
              f.createReturnStatement(
                f.createCallExpression(f.createIdentifier('eval'), undefined, [f.createIdentifier('typeName')])
              ),
            ],
            false
          ),
          f.createCatchClause(
            undefined,
            f.createBlock([f.createReturnStatement(f.createIdentifier('undefined'))], false)
          ),
          undefined
        ),
      ],
      false
    )
  )
}
