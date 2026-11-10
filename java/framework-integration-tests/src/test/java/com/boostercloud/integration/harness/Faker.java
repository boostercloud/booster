package com.boostercloud.integration.harness;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Random test data with the API of the {@code faker} 5 calls the TypeScript suites make, so that ported code reads the
 * same: {@code import static com.boostercloud.integration.harness.Faker.random;} then {@code random.uuid()},
 * {@code random.number(1)} ({@code random.number({ min: 1 })}), {@code commerce.productName()},
 * {@code internet.email()}...
 *
 * <p>The parent build allows no extra libraries, so this is a small self-contained generator: the values have the
 * formats the suites rely on (UUID v4 strings, integers in faker's default range {@code [0, 99999]}, alphanumeric
 * strings, e-mail addresses, ISO currency codes), not faker's locale data. Suites may add the generators they need.
 */
public final class Faker {

  /** {@code faker.random}. */
  public static final Random random = new Random();
  /** {@code faker.commerce}. */
  public static final Commerce commerce = new Commerce();
  /** {@code faker.finance}. */
  public static final Finance finance = new Finance();
  /** {@code faker.lorem}. */
  public static final Lorem lorem = new Lorem();
  /** {@code faker.internet}. */
  public static final Internet internet = new Internet();

  private static final String ALPHA_NUMERIC = "abcdefghijklmnopqrstuvwxyz0123456789";
  private static final List<String> WORDS = List.of("lorem", "ipsum", "dolor", "sit", "amet", "consectetur",
      "adipiscing", "elit", "sed", "do", "eiusmod", "tempor", "incididunt", "ut", "labore", "et", "dolore", "magna",
      "aliqua", "enim", "ad", "minim", "veniam", "quis", "nostrud", "exercitation", "ullamco", "laboris", "nisi",
      "aliquip", "ex", "ea", "commodo", "consequat");
  private static final List<String> ADJECTIVES = List.of("Small", "Ergonomic", "Rustic", "Intelligent", "Gorgeous",
      "Incredible", "Fantastic", "Practical", "Sleek", "Awesome", "Generic", "Handcrafted", "Handmade", "Licensed",
      "Refined", "Unbranded", "Tasty");
  private static final List<String> MATERIALS = List.of("Steel", "Wooden", "Concrete", "Plastic", "Cotton",
      "Granite", "Rubber", "Metal", "Soft", "Fresh", "Frozen");
  private static final List<String> PRODUCTS = List.of("Chair", "Car", "Computer", "Keyboard", "Mouse", "Bike",
      "Ball", "Gloves", "Pants", "Shirt", "Table", "Shoes", "Hat", "Towels", "Soap", "Tuna", "Chicken", "Fish",
      "Cheese", "Bacon", "Pizza", "Salad", "Sausages", "Chips");
  private static final List<String> COLORS = List.of("red", "green", "blue", "yellow", "orange", "purple", "white",
      "black", "silver", "teal", "salmon", "magenta", "turquoise", "violet", "lime", "olive");
  private static final List<List<String>> CURRENCIES = List.of(List.of("EUR", "Euro"), List.of("USD", "US Dollar"),
      List.of("GBP", "Pound Sterling"), List.of("JPY", "Yen"), List.of("CHF", "Swiss Franc"),
      List.of("INR", "Indian Rupee"), List.of("CAD", "Canadian Dollar"), List.of("AUD", "Australian Dollar"));
  private static final List<String> FIRST_NAMES = List.of("john", "jane", "maria", "pedro", "alice", "bob", "carol",
      "dave", "erin", "frank", "grace", "heidi", "ivan", "judy");
  private static final List<String> DOMAINS = List.of("gmail.com", "yahoo.com", "hotmail.com", "example.com");

  private Faker() {}

  private static ThreadLocalRandom rnd() {
    return ThreadLocalRandom.current();
  }

  private static <T> T pick(List<T> values) {
    return values.get(rnd().nextInt(values.size()));
  }

  /** {@code faker.random}. */
  public static final class Random {
    private Random() {}

    /**
     * @return a random UUID v4 string
     */
    public String uuid() {
      return java.util.UUID.randomUUID().toString();
    }

    /**
     * @return {@code random.number()}: an integer in {@code [0, 99999]}
     */
    public int number() {
      return number(0, 99999);
    }

    /**
     * @param min the minimum
     * @return {@code random.number({ min })}: an integer in {@code [min, 99999]}
     */
    public int number(int min) {
      return number(min, 99999);
    }

    /**
     * @param min the minimum
     * @param max the maximum (inclusive)
     * @return {@code random.number({ min, max })}
     */
    public int number(int min, int max) {
      return min + rnd().nextInt(max - min + 1);
    }

    /**
     * @param count how many characters
     * @return {@code random.alphaNumeric(count)}: lower-case letters and digits
     */
    public String alphaNumeric(int count) {
      return IntStream.range(0, count)
          .mapToObj(i -> String.valueOf(ALPHA_NUMERIC.charAt(rnd().nextInt(ALPHA_NUMERIC.length()))))
          .collect(Collectors.joining());
    }

    /**
     * @return a random word
     */
    public String word() {
      return pick(WORDS);
    }
  }

  /** {@code faker.commerce}. */
  public static final class Commerce {
    private Commerce() {}

    /**
     * @return a product, e.g. {@code Chair}
     */
    public String product() {
      return pick(PRODUCTS);
    }

    /**
     * @return a product name, e.g. {@code Ergonomic Steel Chair}
     */
    public String productName() {
      return productAdjective() + " " + pick(MATERIALS) + " " + product();
    }

    /**
     * @return a product adjective, e.g. {@code Rustic}
     */
    public String productAdjective() {
      return pick(ADJECTIVES);
    }

    /**
     * @return a product description sentence
     */
    public String productDescription() {
      return "The " + productName() + " is " + lorem.sentence().toLowerCase(Locale.ROOT);
    }

    /**
     * @return a colour name
     */
    public String color() {
      return pick(COLORS);
    }
  }

  /** {@code faker.finance}. */
  public static final class Finance {
    private Finance() {}

    /**
     * @return an ISO 4217 currency code, e.g. {@code EUR}
     */
    public String currencyCode() {
      return pick(CURRENCIES).get(0);
    }

    /**
     * @return a currency name, e.g. {@code Euro}
     */
    public String currencyName() {
      return pick(CURRENCIES).get(1);
    }
  }

  /** {@code faker.lorem}. */
  public static final class Lorem {
    private Lorem() {}

    /**
     * @param count how many words
     * @return {@code lorem.words(count)}
     */
    public String words(int count) {
      return IntStream.range(0, count).mapToObj(i -> pick(WORDS)).collect(Collectors.joining(" "));
    }

    /**
     * @return a sentence of 3 to 10 words with a capital letter and a final dot
     */
    public String sentence() {
      String words = words(random.number(3, 10));
      return Character.toUpperCase(words.charAt(0)) + words.substring(1) + ".";
    }

    /**
     * @return a paragraph of 3 to 6 sentences
     */
    public String paragraph() {
      return IntStream.range(0, random.number(3, 6)).mapToObj(i -> sentence()).collect(Collectors.joining(" "));
    }
  }

  /** {@code faker.internet}. */
  public static final class Internet {
    private Internet() {}

    /**
     * @return a unique-enough e-mail address
     */
    public String email() {
      return pick(FIRST_NAMES) + "." + random.alphaNumeric(8) + "@" + pick(DOMAINS);
    }

    /**
     * @param length how many characters
     * @return {@code internet.password(length)}: letters and digits
     */
    public String password(int length) {
      String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
      return IntStream.range(0, length)
          .mapToObj(i -> String.valueOf(alphabet.charAt(rnd().nextInt(alphabet.length()))))
          .collect(Collectors.joining());
    }
  }
}
