package com.boostercloud.integration.readmodels;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Projects;
import com.boostercloud.framework.core.decorators.ReadModel;
import com.boostercloud.framework.types.concepts.ProjectionResult;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.entities.Movie;

/**
 * The public view of a movie ({@code src/read-models/movie-read-model.ts}).
 *
 * @param id the movie
 * @param title its title
 */
@ReadModel(authorize = All.class)
public record MovieReadModel(UUID id, String title) implements MediaValue {

  /**
   * @param movie the movie
   * @return the read model
   */
  @Projects(entity = Movie.class, joinKey = "id")
  public static ProjectionResult<MovieReadModel> updateMovie(Movie movie) {
    return ProjectionResult.of(new MovieReadModel(movie.id(), movie.title()));
  }
}
