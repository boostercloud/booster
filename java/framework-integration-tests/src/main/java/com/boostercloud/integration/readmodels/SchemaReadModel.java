package com.boostercloud.integration.readmodels;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.NonExposed;
import com.boostercloud.framework.core.decorators.Projects;
import com.boostercloud.framework.core.decorators.ReadModel;
import com.boostercloud.framework.types.Dates;
import com.boostercloud.framework.types.concepts.ProjectionResult;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.common.Nullable;
import com.boostercloud.integration.entities.Cart;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * A fixture of the GraphQL type mapping ({@code src/read-models/schema-read-model.ts}): its type, filter and sort
 * types are introspected by the schema suites. The components keep the TypeScript property order (the three
 * initialised class fields first, then the constructor properties); each TypeScript type has the Java type that maps
 * to the same GraphQL type:
 *
 * <ul>
 *   <li>{@code string | null}, {@code x?: string}: {@code @Nullable String}; {@code string | undefined}:
 *       {@code Optional<String>};</li>
 *   <li>unions with {@code any}/{@code unknown}/an object, {@code Array<string> | Array<number>}, {@code any},
 *       {@code unknown}, {@code undefined}: {@code Object} ({@code JSON});</li>
 *   <li>{@code (arg0: string) => void}: {@code Consumer<String>} (a function, {@code JSON});</li>
 *   <li>{@code Record<string, string>}: {@code Map<String, String>} ({@code JSON}); {@code GenericClass<T>}: a generic
 *       record ({@code JSON}, filtered as {@code GenericClass_<T>_PropertyFilter});</li>
 *   <li>{@code ReadonlyArray<string>}: {@code @Nullable List<String>}.</li>
 * </ul>
 *
 * <p>The projection is keyed by the cart but returns a read model with a fresh random id, so every cart change stores
 * a new orphan document, as in TypeScript. TypeScript's {@code @NonExposed} constructor parameters that are not
 * properties ({@code nonExposedParameter}, {@code nonExposedGenericeParameter}, {@code nonExposedBaseParameter}) have no
 * Java counterpart: a record has no such parameters, and they are absent from the schema in both.
 *
 * @param readOnlyProperty {@code '1'}
 * @param privateProperty {@code '1'}
 * @param publicProperty {@code '1'}
 * @param nonExposedProperty never set (non-exposed)
 * @param id the read model
 * @param date a date
 * @param array0 {@code string[]}
 * @param array1 {@code Array<string>}
 * @param unionArrays {@code Array<string> | Array<number>}
 * @param unionWithNull {@code string | null}
 * @param unionWithUndefined {@code string | undefined}
 * @param unionWithAny {@code string | any}
 * @param unionWithObject {@code string | Cart}
 * @param unionWithUnknown {@code string | unknown}
 * @param func0 {@code (arg0: string) => void} (never stored, like a JavaScript function)
 * @param any0 {@code any}
 * @param unknown0 {@code unknown}
 * @param record {@code Record<string, string>}
 * @param generic {@code GenericClass<string>}
 * @param child {@code BaseClass}
 * @param optionalString {@code string?}
 * @param optionalNull {@code string?}
 * @param optionalUndefined {@code undefined?}
 * @param optionalUnknown {@code unknown?}
 * @param optionalAny {@code any?}
 * @param optionalRecord {@code Record<string, string>?}
 * @param optionalGeneric {@code GenericClass<Cart>?}
 * @param optionalChild {@code BaseClass?}
 * @param readonlyArray {@code ReadonlyArray<string>?}
 */
@ReadModel(authorize = All.class)
public record SchemaReadModel(
    @Nullable String readOnlyProperty,
    @Nullable String privateProperty,
    @Nullable String publicProperty,
    @NonExposed @Nullable Double nonExposedProperty,
    UUID id,
    Instant date,
    List<String> array0,
    List<String> array1,
    Object unionArrays,
    @Nullable String unionWithNull,
    Optional<String> unionWithUndefined,
    Object unionWithAny,
    Object unionWithObject,
    Object unionWithUnknown,
    Consumer<String> func0,
    Object any0,
    Object unknown0,
    Map<String, String> record,
    GenericClass<String> generic,
    BaseClass child,
    @Nullable String optionalString,
    @Nullable String optionalNull,
    @Nullable Object optionalUndefined,
    @Nullable Object optionalUnknown,
    @Nullable Object optionalAny,
    @Nullable Map<String, String> optionalRecord,
    @Nullable GenericClass<Cart> optionalGeneric,
    @Nullable BaseClass optionalChild,
    @Nullable List<String> readonlyArray) {

  /** The initialised class fields ({@code readonly readOnlyProperty?: string = '1'}...). */
  public SchemaReadModel {
    readOnlyProperty = readOnlyProperty == null ? "1" : readOnlyProperty;
    privateProperty = privateProperty == null ? "1" : privateProperty;
    publicProperty = publicProperty == null ? "1" : publicProperty;
    unionWithUndefined = unionWithUndefined == null ? Optional.empty() : unionWithUndefined;
  }

  /**
   * The TypeScript constructor with its 16 required parameters (the optional ones left undefined).
   *
   * @param id the read model
   * @param date a date
   * @param array0 {@code string[]}
   * @param array1 {@code Array<string>}
   * @param unionArrays {@code Array<string> | Array<number>}
   * @param unionWithNull {@code string | null}
   * @param unionWithUndefined {@code string | undefined}
   * @param unionWithAny {@code string | any}
   * @param unionWithObject {@code string | Cart}
   * @param unionWithUnknown {@code string | unknown}
   * @param func0 a function
   * @param any0 {@code any}
   * @param unknown0 {@code unknown}
   * @param record {@code Record<string, string>}
   * @param generic {@code GenericClass<string>}
   * @param child {@code BaseClass}
   */
  public SchemaReadModel(UUID id, Instant date, List<String> array0, List<String> array1, Object unionArrays,
      String unionWithNull, String unionWithUndefined, Object unionWithAny, Object unionWithObject,
      Object unionWithUnknown, Consumer<String> func0, Object any0, Object unknown0, Map<String, String> record,
      GenericClass<String> generic, BaseClass child) {
    this(null, null, null, null, id, date, array0, array1, unionArrays, unionWithNull,
        Optional.ofNullable(unionWithUndefined), unionWithAny, unionWithObject, unionWithUnknown, func0, any0,
        unknown0, record, generic, child, null, null, null, null, null, null, null, null, null);
  }

  /**
   * Stores a junk read model with a random id for every cart change.
   *
   * @param cart the cart
   * @param oldSchemaReadModel the current read model, or {@code null}
   * @return the read model
   */
  @Projects(entity = Cart.class, joinKey = "id")
  public static ProjectionResult<SchemaReadModel> ignoreChange(Cart cart, SchemaReadModel oldSchemaReadModel) {
    // JSON drops the function TypeScript passes as func0: null stores the same document
    return ProjectionResult.of(new SchemaReadModel(UUID.generate(), Dates.now(), List.of(), List.of(), List.of(),
        null, "test", "test", "test", "test", null, "test", "test", Map.of("test", "test"),
        new GenericClass<>("test"), new BaseClass("base")));
  }

  /**
   * TypeScript's private {@code class GenericClass<T>}.
   *
   * @param genericValue the value
   * @param <T> its type
   */
  public record GenericClass<T>(T genericValue) {}

  /**
   * TypeScript's private {@code class BaseClass}.
   *
   * @param base a value
   */
  public record BaseClass(String base) {}
}
