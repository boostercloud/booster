package com.boostercloud.framework.core.services.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The {@code inflected@2.1.0} {@code pluralize} rules the GraphQL names depend on. */
class InflectionTest {

  @ParameterizedTest
  @CsvSource({
      "CartReadModel, CartReadModels",
      "Boolean, Booleans",
      "String, Strings",
      "Number, Numbers",
      "Array, Arrays",
      "Category, Categories",
      "Box, Boxes",
      "Status, Statuses",
      "Alias, Aliases",
      "ProductUpdates, ProductUpdates",
      "Money, Money",
      "Information, Information",
      "UserMoney, UserMoneys",
      "Person, People",
      "Salesperson, Salespeople",
      "OrderMan, OrderMen",
      "Human, Humen",
      "Child, Children",
      "Move, Moves",
      "Zombie, Zombies",
      "Sex, Sexes",
      "Wife, Wives",
      "Half, Halves",
      "Hive, Hives",
      "Query, Queries",
      "Day, Days",
      "Match, Matches",
      "Dish, Dishes",
      "Kiss, Kisses",
      "Matrix, Matrices",
      "Vertex, Vertices",
      "Index, Indices",
      "Mouse, Mice",
      "Ox, Oxen",
      "Quiz, Quizzes",
      "Octopus, Octopi",
      "Virus, Viri",
      "Bus, Buses",
      "Buffalo, Buffaloes",
      "Tomato, Tomatoes",
      "Datum, Data",
      "Medium, Media",
      "Analysis, Analyses",
      "Axis, Axes",
      "Testis, Testes",
      "People, People",
      "Children, Children",
      "Item2, Item2s",
  })
  void pluralizesLikeInflected(String singular, String plural) {
    assertThat(Inflection.pluralize(singular)).isEqualTo(plural);
  }

  @ParameterizedTest
  @CsvSource(value = {"''"})
  void keepsTheEmptyString(String empty) {
    assertThat(Inflection.pluralize(empty)).isEmpty();
  }
}
