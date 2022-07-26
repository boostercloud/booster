import * as ts from 'typescript'
import { TypeMetadata } from './metadata-types'

type SaveTypeOpts = { typeInfo: TypeMetadata; forceSave?: boolean }
type CacheExtras = { statement?: ts.ObjectLiteralExpression; typeAccesses: number; statementAccesses: number }

type CachedTypeInfo = TypeMetadata & CacheExtras

export class TypeCache {
  private static cache: TypeCache

  private constructor(readonly cacheRecord: Record<string, CachedTypeInfo>) {}

  public static getInstance(): TypeCache {
    if (!this.cache) {
      console.log('Creating new type cache')
      this.cache = new TypeCache({})
    }
    return this.cache
  }

  public getType(name: string): CachedTypeInfo | undefined {
    const cachedType = this.cacheRecord[name]
    if (cachedType) {
      cachedType.typeAccesses++
    }
    return cachedType
  }

  public saveType({ typeInfo, forceSave }: SaveTypeOpts): void {
    const { name, typeName } = typeInfo
    console.log(`Saving type ${name}`)
    if (!forceSave && this.getType(name)) {
      throw new Error(`Metadata generation error: Type ${name} was already cached`)
    }
    const cachedInfo = { ...typeInfo, typeAccesses: 0, statementAccesses: 0 }
    this.cacheRecord[name] = cachedInfo
    if (typeName) {
      this.cacheRecord[typeName] = cachedInfo
    }
  }

  public getStatement({ name, typeName }: TypeMetadata): ts.ObjectLiteralExpression | undefined {
    let cachedType
    if (typeName) {
      cachedType = this.getType(typeName)
    }
    cachedType = cachedType ?? this.getType(name)
    if (!cachedType) {
      throw new Error(`Attempted get a statement that was not saved for ${name}`)
    }
    cachedType.statementAccesses++
    return cachedType.statement
  }

  public saveStatement({ name, typeName }: TypeMetadata, statement: ts.ObjectLiteralExpression): void {
    const cachedType = typeName ? this.getType(typeName) : this.getType(name)
    if (!cachedType) {
      throw new Error(`Attempted to mark an non-existent type ${name} as created`)
    }
    this.cacheRecord[name] = { ...cachedType, statement }
  }
}
