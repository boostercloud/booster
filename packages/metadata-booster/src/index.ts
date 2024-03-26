import * as ts from 'typescript'
import { getClassInfo } from './metadata-extractors'
import { createClassMetadataDecorator, createFilterInterfaceFunction } from './statement-creators'

const transformer: (program: ts.Program) => ts.TransformerFactory<ts.SourceFile> = (program) => {
  const checker = program.getTypeChecker()
  const transformerFactory: ts.TransformerFactory<ts.SourceFile> = (context) => {
    const f = context.factory
    const filterInterfaceFunctionName = f.createUniqueName('filterInterface')
    return (sourceFile) => {
      const importedTypes: Record<string, string> = {}
      function visitor(node: ts.Node): ts.VisitResult<ts.Node | undefined> {
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
          const classInfo = getClassInfo(node, checker)
          if (classInfo) {
            const metadataDecorator = createClassMetadataDecorator(
              f,
              classInfo,
              filterInterfaceFunctionName,
              importedTypes
            )
            const newModifiers = [...(node.modifiers ?? []), metadataDecorator]
            return f.updateClassDeclaration(
              node,
              newModifiers,
              node.name,
              node.typeParameters,
              node.heritageClauses,
              node.members
            )
          }
        }
        return ts.visitEachChild(node, visitor, context)
      }
      const modifiedSourceFile = ts.visitNode(sourceFile, visitor) as ts.SourceFile
      return f.updateSourceFile(modifiedSourceFile, [
        f.createImportDeclaration(undefined, undefined, f.createStringLiteral('reflect-metadata')),
        ...modifiedSourceFile.statements,
        createFilterInterfaceFunction(f, filterInterfaceFunctionName),
      ])
    }
  }

  return transformerFactory
}

export default transformer
export * from './metadata-types'
