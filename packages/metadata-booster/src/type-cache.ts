import * as ts from 'typescript'
import { TypeMetadata } from './metadata-types'

type SaveOptions = { typeInfo: TypeMetadata; forceSave?: boolean }
type StatementStatus = { statement?: ts.ObjectLiteralExpression }

export class TypeCache {
  private static cache: TypeCache

  private constructor(readonly cacheRecord: Record<string, TypeMetadata & StatementStatus>) {}

  public static getInstance(): TypeCache {
    if (!this.cache) {
      console.log('Creating new type cache')
      this.cache = new TypeCache({})
    }
    return this.cache
  }

  public getType = (name: string): TypeMetadata | undefined => this.cacheRecord[name]

  public saveType({ typeInfo, forceSave }: SaveOptions): void {
    const { name } = typeInfo
    console.log(`Saving type ${name}`)
    if (!forceSave && this.getType(name)) {
      throw new Error(`Metadata generation error: Type ${name} was already cached`)
    }
    this.cacheRecord[name] = typeInfo
  }

  public hasStatementCreated = (name: string): boolean => Boolean(this.cacheRecord[name].statement)

  public getStatement(name: string): ts.ObjectLiteralExpression {
    const cachedStatement = this.cacheRecord[name].statement
    if (!cachedStatement) {
      throw new Error(`Attempted get a statement that was not saved for ${name}`)
    }
    return cachedStatement
  }

  public saveStatement(name: string, statement: ts.ObjectLiteralExpression): void {
    const cachedType = this.getType(name)
    if (!cachedType) {
      throw new Error(`Attempted to mark an non-existent type ${name} as created`)
    }
    this.cacheRecord[name] = { ...cachedType, statement }
  }
}
