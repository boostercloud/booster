import * as ts from 'typescript'
import { makeModuleLogger } from './logging'
import { TypeMetadata } from './metadata-types'

type CachedStatement = { statement?: ts.ObjectLiteralExpression }
type CacheExtras = { typeAccesses: number; statementAccesses: number }
type CachedTypeInfo = TypeMetadata & CachedStatement & CacheExtras
type SaveTypeOpts = { typeInfo: TypeMetadata & CachedStatement; forceSave?: boolean }

const makeLogger = makeModuleLogger(module.filename)

export class TypeCache {
  private static cache: TypeCache

  private constructor(readonly cacheRecord: Record<string, CachedTypeInfo>) {}

  public static getInstance(): TypeCache {
    if (!this.cache) {
      this.cache = new TypeCache({})
    }
    return this.cache
  }

  public getType(
    info: Pick<TypeMetadata, 'name' | 'isNullable' | 'typeName'>,
    skipTypeName = false
  ): CachedTypeInfo | undefined {
    const logger = makeLogger(this.getType.name)
    const typeKey =
      !skipTypeName && info.typeName ? buildKey(flipName(info as TypeMetadataWithTypeName)) : buildKey(info)
    logger.debug('Getting type for key %s', typeKey)
    const cachedType = this.cacheRecord[typeKey]
    if (cachedType) {
      cachedType.typeAccesses++
    }
    return cachedType
  }

  public saveType({ typeInfo: info, forceSave }: SaveTypeOpts): void {
    const typeKey = buildKey(info)
    if (!forceSave && this.getType(info)) {
      throw new Error(`Metadata generation error: Type ${info.name} was already cached`)
    }
    const cachedInfo = { ...info, typeAccesses: 0, statementAccesses: 0 }
    this.cacheRecord[typeKey] = cachedInfo
    if (info.typeName) {
      const typeKey2 = buildKey(flipName(info as TypeMetadataWithTypeName))
      this.cacheRecord[typeKey2] = cachedInfo
    }
  }

  public getStatement(info: TypeMetadata): ts.ObjectLiteralExpression | undefined {
    const cachedType = this.getType(info)
    if (!cachedType) {
      throw new Error(`Attempted get a statement that was not saved for ${info.name}`)
    }
    cachedType.statementAccesses++
    return cachedType.statement
  }

  public saveStatement(info: TypeMetadata, statement: ts.ObjectLiteralExpression): void {
    const cachedType = this.getType(info)
    if (!cachedType) {
      throw new Error(`Attempted to mark an non-existent type ${info.name} as created`)
    }

    this.saveType({ typeInfo: { ...cachedType, statement }, forceSave: true })
  }
}

const buildKey = ({ name, isNullable }: Pick<TypeMetadata, 'name' | 'isNullable'>): string =>
  `${name}${isNullable ? '?' : ''}`

type TypeMetadataWithTypeName = TypeMetadata & {
  typeName: string
}

const flipName = (typeInfo: TypeMetadataWithTypeName): TypeMetadataWithTypeName => ({
  ...typeInfo,
  name: typeInfo.typeName,
})
