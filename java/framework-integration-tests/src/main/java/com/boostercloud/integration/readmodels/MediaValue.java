package com.boostercloud.integration.readmodels;

/**
 * {@code export type MediaValue = BookReadModel | MovieReadModel} ({@code src/queries/search-media.ts}): the GraphQL
 * union {@code MediaValue} returned by the {@code SearchMedia} query.
 *
 * <p>Java models the union as a sealed interface; in the unnamed module the permitted classes must live in the
 * interface's package, hence this type is here instead of next to the query.
 */
public sealed interface MediaValue permits BookReadModel, MovieReadModel {}
