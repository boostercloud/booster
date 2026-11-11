# booster-metadata (`com.boostercloud.metadata`)

Java port of `packages/metadata-booster`. It describes classes — their properties, their methods and
the types of both — in the same shape the TypeScript transformer produces, so that core (GraphQL
schema generation, filters, sorting, read model registration) can port its rules unchanged.

## Contents

| Java type | TypeScript counterpart | Purpose |
| --- | --- | --- |
| `ClassMetadata` | `ClassMetadata` (`metadata-types.ts`) | `{ name, type, fields, methods }` of a class; `ClassMetadata.of(cls)` replaces `Reflect.getMetadata('booster:typeinfo', cls)` (and core's `getClassMetadata`). |
| `PropertyMetadata` | `PropertyMetadata` | `{ name, typeInfo, dependencies }` plus `kind` and the reflective `element`; `readValue(instance)`, `getAnnotation(...)`. |
| `MethodMetadata` | entries of `ClassMetadata.methods` | name, return type, `isStatic`, parameters, `Method`. |
| `MemberMetadata` | — | common view of properties and methods. |
| `TypeMetadata` | `TypeMetadata` | `{ name, typeGroup, typeName, parameters, isNullable, isGetAccessor, importPath, type }` + `javaType`, `isExternal()`. |
| `TypeGroup` | `TypeGroup` union | the 14 groups; `tsName()` gives the TypeScript literal. |
| `MetadataExtractors` | `metadata-extractors.ts` (`getClassInfo`, `getTypeInfo`) | the reflection reader, `tsName(Type)`. |
| `ExternalTypes` | `isExternalType` (core) + `importPath` | which classes are not part of the application. |
| `Types` | — | generic type helpers (raw class, supertype arguments, bindings, substitution) for binding code. |

Not ported, with reason: the transformer itself (`index.ts`), `statement-creators.ts`
(`createClassMetadataDecorator`, `createFilterInterfaceFunction`) and the compile-time intermediates
`TypeInfo`, `PropertyInfo`, `ClassInfo`: Java keeps types at run time, so there is no code to
generate and the runtime shapes are computed directly. `ClassType` is `java.lang.Class<?>`.

## How metadata is computed

* **Fields**: record components in order; for classes the declared non-static, non-synthetic,
  non-`transient` fields of every superclass (root first) and then the class, each in source
  declaration order (read from the class file with `java.lang.classfile`, because reflection does not
  guarantee an order); then calculated fields.
* **Calculated fields** (TypeScript getters): no-argument instance methods annotated with an
  annotation whose simple name is `CalculatedField` (core's `@CalculatedField`). They get
  `isGetAccessor = true` (also on their type arguments, as in TypeScript) and `dependencies` from the
  annotation's `dependsOn`.
* **Methods**: own declared methods, static and instance, in declaration order; synthetic/bridge
  methods, record accessors and generated `equals`/`hashCode`/`toString`, enum `values`/`valueOf` and
  calculated fields are excluded. Parameters are described too (names need `-parameters`).
* **Types**: see the table in `TypeMetadata`'s Javadoc. Names are TypeScript-compatible text
  (`string`, `number[]`, `Record<string, Cart>`, `Page<Cart>`, `Promise<boolean>`). Type variables
  bound by a subclass are resolved (`class Box extends Generic<String>` → `string`); unbound ones are
  `Other` named after the variable. Type arguments are described to depth 8, like TypeScript.
* **Nullability**: `Optional<T>` is unwrapped into a nullable `T` (TypeScript `T | undefined`); any
  annotation named `Nullable` on the declaration (any `@Target`) makes it nullable and, like the
  TypeScript `?` token, its type arguments too; a type-use `@Nullable` on a type argument makes only
  that argument nullable.
* **Caching**: `ClassMetadata.of` caches per class (`ClassValue`); results are immutable.

## Differences from TypeScript

* Metadata exists for every class on demand; TypeScript only has it for classes compiled with the
  transformer (and fails with `Couldn't get proper metadata information of X` otherwise).
* `type` is always the real class when `typeName` is present (TypeScript sometimes has `undefined`:
  `ReadonlyArray`, forward references, cycles). `javaType` (the declared reflective type) is a Java
  addition.
* Java collections: `List`, `Set` and every `Iterable`/array are `Array` (TypeScript `Set` is
  `Object`); `ReadonlyArray` and `Intersection` are never produced; `byte[]` is a `string` (it is
  base64 text in JSON).
* `Map<K, V>` is described as the TypeScript alias `Record<K, V>` (group `Type`), which is how
  TypeScript applications write dictionaries; a TypeScript `Map<K, V>` would be `Object`.
* Sealed interfaces and sealed classes are unions (TypeScript has aliased unions of classes); other
  interfaces are `Interface`; JDK functional interfaces are `Function`.
* `importPath` is the Java package of class-like types (TypeScript: the module specifier of a named
  import, only when imported). External types are JDK classes and the `ExternalTypes` prefixes
  instead of "imported from a package".
* Method parameters are described (TypeScript lists that as a missing feature) and methods keep the
  reflective `Method`.
* `dependencies` is always a list (empty unless `@CalculatedField(dependsOn = ...)`); TypeScript only
  sets it for read models.
