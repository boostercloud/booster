import * as ts from 'typescript'
import { getClassInfo } from './metadata-extractors'
import { createClassMetadataDecorator, createTypeEvalFunction } from './statement-creators'
import { TypeCache } from './type-cache'

const transformer: (program: ts.Program) => ts.TransformerFactory<ts.SourceFile> = (program) => {
  const checker = program.getTypeChecker()
  const transformerFactory: ts.TransformerFactory<ts.SourceFile> = (context) => {
    const typeCache = TypeCache.getInstance()
    const f = context.factory
    const typeEvalFunctionName = f.createIdentifier('filterInterface')
    return (sourceFile) => {
      const importedTypes: Record<string, string> = {}
      function visitor(node: ts.Node): ts.VisitResult<ts.Node> {
        if (ts.isImportDeclaration(node)) {
          // To ensure we import 'reflect-metadata', delete it from the file in case it is already there.
          // Later we will add it.
          const quotedModuleName = node.moduleSpecifier.getText()
          const moduleName = quotedModuleName.replace(/['']/g, '')
          if (moduleName == 'reflect-metadata') {
            return undefined
          }
          const namedBindings = node.importClause?.namedBindings
          if (namedBindings && 'elements' in namedBindings) {
            const setImportedType = (elem: ts.ImportSpecifier): string =>
              (importedTypes[elem.name.getText()] = moduleName)
            namedBindings.elements.forEach(setImportedType)
          }
        }

        if (ts.isClassDeclaration(node)) {
          const classInfo = getClassInfo(node, checker, typeCache)
          if (classInfo) {
            const metadataDecorator = createClassMetadataDecorator(
              f,
              classInfo,
              typeEvalFunctionName,
              importedTypes,
              typeCache
            )
            const newDecorators = [...(node.decorators ?? []), metadataDecorator]
            return f.updateClassDeclaration(
              node,
              newDecorators,
              node.modifiers,
              node.name,
              node.typeParameters,
              node.heritageClauses,
              node.members
            )
          }
        }
        return ts.visitEachChild(node, visitor, context)
      }
      const modifiedSourceFile = ts.visitNode(sourceFile, visitor)
      const typeEvalFunction = createTypeEvalFunction(f, typeEvalFunctionName)
      const result = f.updateSourceFile(modifiedSourceFile, [
        f.createImportDeclaration(undefined, undefined, undefined, f.createStringLiteral('reflect-metadata')),
        ...modifiedSourceFile.statements,
        typeEvalFunction,
      ])
      return result
    }
  }

  return transformerFactory
}

export default transformer
export * from './metadata-types'
