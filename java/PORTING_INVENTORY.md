# Inventario del port de Booster a Java

Actualizado el 5 de noviembre de 2026 para el [PR #1607](https://github.com/boostercloud/booster/pull/1607).

Este inventario responde a qué componentes tienen implementación Java, dónde
están sus archivos principales y qué queda. Es una adaptación por funcionalidad:
varios archivos TypeScript se agrupan en una clase Java y otros se sustituyen por
registros, interfaces y bibliotecas Java. No existe una correspondencia de un
archivo a otro ni una demostración de equivalencia total con todo el monorepo.

**Corrección del estado anterior:** no queda únicamente validación de Azure.
La comparación de archivos identifica las diferencias funcionales pendientes
que se enumeran más abajo. Pasar las pruebas actuales no cubre esas ausencias.

## Componentes implementados y archivos principales

| Componente | Funcionalidad y origen | Archivos Java principales |
| --- | --- | --- |
| Núcleo de Booster | `framework-core` y conceptos de `framework-types`: comandos, eventos, entidades, reductores, handlers, contexto, autorización, errores y hooks. | [Booster.java](src/main/java/io/boostercloud/booster/core/Booster.java), [Register.java](src/main/java/io/boostercloud/booster/core/Register.java), [AccessPolicy.java](src/main/java/io/boostercloud/booster/core/AccessPolicy.java), [FrameworkError.java](src/main/java/io/boostercloud/booster/core/FrameworkError.java) |
| Persistencia y concurrencia | Contratos de eventos/estado, recibos idempotentes, transacciones, snapshots y recuperación; adaptadores de memoria y H2. | [EventStore.java](src/main/java/io/boostercloud/booster/core/EventStore.java), [StateStore.java](src/main/java/io/boostercloud/booster/core/StateStore.java), [JdbcEventStore.java](src/main/java/io/boostercloud/booster/store/JdbcEventStore.java), [JdbcStateStore.java](src/main/java/io/boostercloud/booster/store/JdbcStateStore.java) |
| Proyecciones y búsquedas | Read models, joins, fan-out, secuencias, borrado, filtros, ordenación y paginación. | [Join.java](src/main/java/io/boostercloud/booster/core/Join.java), [ProjectionContext.java](src/main/java/io/boostercloud/booster/core/ProjectionContext.java), [ProjectionResult.java](src/main/java/io/boostercloud/booster/core/ProjectionResult.java), [Filters.java](src/main/java/io/boostercloud/booster/core/Filters.java), [EcmaRegex.java](src/main/java/io/boostercloud/booster/core/regex/EcmaRegex.java) |
| Migraciones de esquema | `schema-migrator.ts` y `read-model-schema-migrator.ts`: versiones y upcasters para comandos, eventos, entidades y read models. La lógica reside en el núcleo Java. | [Booster.java](src/main/java/io/boostercloud/booster/core/Booster.java), [PendingEvent.java](src/main/java/io/boostercloud/booster/core/PendingEvent.java) |
| Migraciones de datos | `booster-data-migrations.ts` y sus conceptos: trabajos, checkpoints, leases, estados, finalización explícita, rename/ID y touch. | [DataMigrations.java](src/main/java/io/boostercloud/booster/migration/DataMigrations.java), [MigrationContext.java](src/main/java/io/boostercloud/booster/migration/MigrationContext.java), [BoosterDataMigrationEntity.java](src/main/java/io/boostercloud/booster/migration/BoosterDataMigrationEntity.java), [BoosterDataMigrationFinished.java](src/main/java/io/boostercloud/booster/migration/BoosterDataMigrationFinished.java) |
| API HTTP y GraphQL | Generación de esquemas, operaciones, tipos, filtros, selección de campos, cursores, historial y errores. | [BoosterHttpServer.java](src/main/java/io/boostercloud/booster/transport/BoosterHttpServer.java), [BoosterGraphQL.java](src/main/java/io/boostercloud/booster/transport/BoosterGraphQL.java), [GraphQLTypes.java](src/main/java/io/boostercloud/booster/transport/GraphQLTypes.java), [GraphQLId.java](src/main/java/io/boostercloud/booster/transport/GraphQLId.java) |
| Autenticación | Verificadores de clave pública y JWKS: JWT RS256, roles, políticas, expiración y rotación de claves. | [AuthenticationProvider.java](src/main/java/io/boostercloud/booster/transport/AuthenticationProvider.java), [JwtVerifier.java](src/main/java/io/boostercloud/booster/transport/auth/JwtVerifier.java), [JwksVerifier.java](src/main/java/io/boostercloud/booster/transport/auth/JwksVerifier.java) |
| Suscripciones | Protocolo legacy graphql-ws, ciclo de conexión, filtrado, autorización y entrega por Web PubSub. | [GraphQLWebSocketSession.java](src/main/java/io/boostercloud/booster/transport/GraphQLWebSocketSession.java), [AzureWebPubSubHandler.java](src/main/java/io/boostercloud/booster/azure/AzureWebPubSubHandler.java), [AzureSubscriptionStore.java](src/main/java/io/boostercloud/booster/azure/AzureSubscriptionStore.java), [AzureSubscriptionNotifier.java](src/main/java/io/boostercloud/booster/azure/AzureSubscriptionNotifier.java) |
| Proveedor e infraestructura local | `framework-provider-local` y `framework-provider-local-infrastructure`: almacenamiento persistente, host, configuración, arranque/parada, estado, rebuild y nuke. | [LocalProvider.java](src/main/java/io/boostercloud/booster/local/LocalProvider.java), [LocalApplication.java](src/main/java/io/boostercloud/booster/local/LocalApplication.java), [LocalHost.java](src/main/java/io/boostercloud/booster/local/LocalHost.java), [LocalInfrastructure.java](src/main/java/io/boostercloud/booster/local/LocalInfrastructure.java), [LocalConfiguration.java](src/main/java/io/boostercloud/booster/local/LocalConfiguration.java) |
| Adaptadores Azure | `framework-provider-azure`: Cosmos, Event Hubs, change feed, checkpoints y clientes SDK. Implementados con pruebas de contratos; integración cloud pendiente. | [AzureProvider.java](src/main/java/io/boostercloud/booster/azure/AzureProvider.java), [CosmosEventStore.java](src/main/java/io/boostercloud/booster/azure/CosmosEventStore.java), [CosmosStateStore.java](src/main/java/io/boostercloud/booster/azure/CosmosStateStore.java), [AzureEventPipeline.java](src/main/java/io/boostercloud/booster/azure/AzureEventPipeline.java), [AzurePartitionCheckpoint.java](src/main/java/io/boostercloud/booster/azure/AzurePartitionCheckpoint.java) |
| Infraestructura y host Azure | ARM y Container Apps para Java 27: síntesis, deploy/update/status/nuke, identidad y roles. Sustituye el hosting Functions/CDKTF; no reproduce toda la red original. | [AzureInfrastructure.java](src/main/java/io/boostercloud/booster/azure/infrastructure/AzureInfrastructure.java), [AzureArmClient.java](src/main/java/io/boostercloud/booster/azure/infrastructure/AzureArmClient.java), [AzureDeploymentConfig.java](src/main/java/io/boostercloud/booster/azure/infrastructure/AzureDeploymentConfig.java), [AzureHost.java](src/main/java/io/boostercloud/booster/azure/hosting/AzureHost.java) |
| Tareas programadas | Calendarios UTC, ejecución persistente, leases, deduplicación, reintentos y recuperación. | [CalendarSchedule.java](src/main/java/io/boostercloud/booster/scheduling/CalendarSchedule.java), [PersistentScheduler.java](src/main/java/io/boostercloud/booster/scheduling/PersistentScheduler.java), [AzureScheduleDispatcher.java](src/main/java/io/boostercloud/booster/azure/AzureScheduleDispatcher.java) |
| Health checks | Árbol de componentes, sensores, autorización, límites y comprobaciones de proveedores. | [HealthService.java](src/main/java/io/boostercloud/booster/health/HealthService.java), [ProviderHealth.java](src/main/java/io/boostercloud/booster/health/ProviderHealth.java), [HealthHttpHandler.java](src/main/java/io/boostercloud/booster/health/HealthHttpHandler.java) |
| Rocket webhooks | `boostercloud/rocket-webhook`: rutas, GET/POST, autorización, JSON/texto/binario/multipart, eventos y respuestas. | [WebhookRocket.java](src/main/java/io/boostercloud/booster/rockets/webhook/WebhookRocket.java), [WebhookRequest.java](src/main/java/io/boostercloud/booster/rockets/webhook/WebhookRequest.java), [WebhookResponse.java](src/main/java/io/boostercloud/booster/rockets/webhook/WebhookResponse.java), [MultipartParser.java](src/main/java/io/boostercloud/booster/rockets/webhook/MultipartParser.java) |
| Rocket subida de ficheros | `boostercloud/rocket-file-uploads`: URLs firmadas, listado, almacenamiento local y Blob/SAS, notificaciones y proyecciones. | [FileUploadRocket.java](src/main/java/io/boostercloud/booster/rockets/uploads/FileUploadRocket.java), [FileHandler.java](src/main/java/io/boostercloud/booster/rockets/uploads/FileHandler.java), [LocalFileStorage.java](src/main/java/io/boostercloud/booster/rockets/uploads/LocalFileStorage.java), [AzureBlobStorage.java](src/main/java/io/boostercloud/booster/rockets/uploads/AzureBlobStorage.java), [AzureFileUploadHttpHandler.java](src/main/java/io/boostercloud/booster/rockets/uploads/AzureFileUploadHttpHandler.java) |
| CLI y generadores | `packages/cli`: proyecto, tipos de dominio, build/clean y ciclo de vida local/Azure; API de extensión injectable pendiente. | [BoosterCli.java](src/main/java/io/boostercloud/booster/cli/BoosterCli.java), [ProjectGenerator.java](src/main/java/io/boostercloud/booster/cli/ProjectGenerator.java), [DomainGenerator.java](src/main/java/io/boostercloud/booster/cli/DomainGenerator.java) |
| Ejemplos | Aplicación de carrito Java y entrada para Azure; referencia ejecutable, no conversión de todos los ejemplos TypeScript. | [ShoppingCartApplication.java](src/main/java/io/boostercloud/booster/example/ShoppingCartApplication.java), [AzureShoppingCartApplication.java](src/main/java/io/boostercloud/booster/azure/hosting/AzureShoppingCartApplication.java) |

Hay **133 archivos Java de producción** y **46 archivos Java de pruebas y
soporte de pruebas**. El build usa [pom.xml](pom.xml), [mvnw](mvnw),
[install-jdk.sh](scripts/install-jdk.sh) y [Dockerfile](Dockerfile).
La validación registrada es de **281 pruebas**, más una prueba en una aplicación
generada y comprobaciones HTTP/Docker. Los 46 archivos de test no equivalen a
46 casos ni a la traducción íntegra de la suite TypeScript.

Los detalles y límites de cada implementación están en
[COMPATIBILITY.md](COMPATIBILITY.md); los resultados ejecutados, en
[VALIDATION.md](VALIDATION.md).

## Funcionalidad sin equivalente completo identificado

| Componente pendiente | Evidencia TypeScript | Qué falta en Java |
| --- | --- | --- |
| Trazas configurables | [Trace](../packages/framework-core/src/instrumentation/decorator/trace.ts), [trace-notifier](../packages/framework-core/src/instrumentation/trace-notifier.ts), [tipos de trazas](../packages/framework-types/src/instrumentation/trace-types.ts) | Configuración por acción, callbacks onStart/onEnd, identificador, argumentos y duración, tanto para acciones internas como personalizadas. Los hooks y errores Java no equivalen a este contrato. |
| Extensión de comandos CLI | [injectable](../packages/framework-core/src/injectable/index.ts) y [Booster.run](../packages/framework-core/src/booster.ts) | Registro público de subcomandos de aplicación, contexto y ejecución equivalentes. La CLI Java actual tiene comandos incorporados, sin este mecanismo de extensión. |
| Configuración de Azure y red | [application-synth](../packages/framework-provider-azure-infrastructure/src/infrastructure/synth/application-synth.ts), [gateway](../packages/framework-provider-azure-infrastructure/src/infrastructure/synth/gateway), [AzureConfiguration](../packages/framework-types/src/provider/azure-configuration.ts) | La plantilla Java no crea el Application Gateway/VNet/subred/NSG del proveedor original. Tampoco expone todos los ajustes originales de batching y opciones Cosmos por petición. Se necesita implementar equivalencia apropiada para Container Apps o acordar explícitamente las diferencias; las pruebas de despliegue por sí solas no las implementan. |
| Biblioteca pública de pruebas de aplicaciones | [application-tester](../packages/application-tester/src/application-tester.ts), [provider-test-helper](../packages/application-tester/src/provider-test-helper.ts) | No hay un paquete Java equivalente con helpers públicos GraphQL/HTTP/JWT y consultas/contadores de proveedor. Existen pruebas y fixtures Java internos, que no sustituyen esa biblioteca para usuarios. |

Estos puntos quedan abiertos en [GOAL.md](GOAL.md). Este inventario general no
certifica que cada opción de cada archivo TypeScript tenga ya equivalencia;
los contratos de las áreas pendientes requieren comparación al implementarlos.

## Validaciones pendientes, separadas de la implementación

- **Cosmos en emulador:** no ejecutado. La descarga oficial fue bloqueada por el
  proxy en `centralus.data.mcr.microsoft.com`. Permitir la descarga posibilitaría
  probar capacidades del adaptador Cosmos sin cuenta Azure; el emulador no
  reproduce toda la plataforma.
- **Azure real:** despliegue/update, permisos y managed identity, integración
  Cosmos/Event Hubs/Web PubSub, transferencias SAS, recuperación y limpieza.
  Sigue aplazado por instrucción del usuario; no desplegar sin nueva autorización.
- **Java en CI:** las 281 pruebas Maven se ejecutaron en el entorno de trabajo.
  Los workflows actuales del repositorio no ejecutan esa suite. Su automatización
  sigue pendiente y no debe confundirse con los checks TypeScript ya aprobados.

Los seis checks de GitHub del commit `09ceb7307007d6809fc13416fdd3efad19f60ed4`
pasaron; la espera de runners anterior está resuelta. El PR requiere revisión.
Eso no elimina las diferencias funcionales identificadas en este inventario.

## Componentes excluidos o sustituidos

- **AWS y su infraestructura:** excluidos expresamente por el usuario.
- **Docker rocket y static-sites AWS rocket:** no solicitados. El Dockerfile Java
  sirve para ejecutar la aplicación; no es un port del docker-rocket.
- **metadata-booster y decoradores TypeScript:** su función de descripción del
  dominio se cubre con registros, tipos/reflexión y registro explícito Java. No se
  ha portado el compilador de metadatos TypeScript como producto independiente.
- **framework-common-helpers y framework-types:** sus responsabilidades usadas
  se distribuyen entre las clases Java y dependencias; no se copian como paquetes
  npm ni se garantiza equivalencia con cada utilidad pública aislada.
- **Todos los ejemplos y suites TypeScript:** se incorporaron escenarios y
  fixtures representativos, no una conversión individual de todos sus archivos.
- **Conversor automático de aplicaciones o bases TypeScript:** no implementado
  ni solicitado. Las migraciones Java trabajan sobre el formato Java; importar
  datos existentes requiere una conversión deliberada.
