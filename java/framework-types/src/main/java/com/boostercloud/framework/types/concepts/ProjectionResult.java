package com.boostercloud.framework.types.concepts;

import java.util.Objects;

/**
 * What a projection returns: the new read model ({@link Upsert}), or a {@link ReadModelAction}. Port of the
 * TypeScript union {@code ProjectionResult<TReadModel> = TReadModel | ReadModelAction}.
 *
 * <pre>{@code
 * @Projects(entity = Cart.class, joinKey = "id")
 * public static ProjectionResult<CartReadModel> project(Cart cart, CartReadModel old) {
 *   if (cart.deleted()) return ReadModelAction.DELETE;   // or ProjectionResult.delete()
 *   return ProjectionResult.of(new CartReadModel(cart.id(), cart.items()));
 * }
 * }</pre>
 *
 * <p>{@code ReadModelAction} is a plain enum, so returning {@code ReadModelAction.DELETE} from a method declared as
 * {@code ProjectionResult<CartReadModel>} compiles with an "unchecked conversion" warning;
 * {@link #delete()} and {@link #nothing()} are the warning-free equivalents. Core distinguishes the outcomes with a
 * {@code switch} over this sealed type.
 *
 * @param <T> the read model type
 */
@SuppressWarnings("rawtypes")
public sealed interface ProjectionResult<T> permits ProjectionResult.Upsert, ReadModelAction {

  /**
   * The new version of the read model, to be stored.
   *
   * @param value the read model
   * @param <T> the read model type
   */
  record Upsert<T>(T value) implements ProjectionResult<T> {
    /** Canonical constructor; rejects {@code null} (return {@link ReadModelAction#NOTHING} instead). */
    public Upsert {
      Objects.requireNonNull(value, "A projection result value cannot be null");
    }
  }

  /**
   * @param readModel the new read model
   * @param <T> the read model type
   * @return the upsert result
   */
  static <T> ProjectionResult<T> of(T readModel) {
    return new Upsert<>(readModel);
  }

  /**
   * @param <T> the read model type
   * @return {@link ReadModelAction#DELETE}, typed
   */
  @SuppressWarnings("unchecked")
  static <T> ProjectionResult<T> delete() {
    return ReadModelAction.DELETE;
  }

  /**
   * @param <T> the read model type
   * @return {@link ReadModelAction#NOTHING}, typed
   */
  @SuppressWarnings("unchecked")
  static <T> ProjectionResult<T> nothing() {
    return ReadModelAction.NOTHING;
  }

  /**
   * Interprets the raw return value of a projection method: a {@code ProjectionResult} is returned as is, any other
   * non-null value is an upsert of that value. {@code null} is returned as {@code null} so core can decide (TypeScript
   * would try to store {@code undefined}).
   *
   * @param value the value returned by the projection method
   * @return the result, or {@code null}
   */
  @SuppressWarnings("unchecked")
  static ProjectionResult<Object> fromReturnValue(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof ProjectionResult<?> result) {
      return (ProjectionResult<Object>) result;
    }
    return new Upsert<>(value);
  }
}
