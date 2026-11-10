package com.boostercloud.framework.provider.local.store;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * The writer process of {@link KillDuringWriteStressTest}: loads a data file, then inserts, replaces and removes
 * documents and compacts the file in a loop until it is killed. Before each operation it prints
 * {@code BEGIN <operation> ...} and after it returns {@code ACK}, so the parent knows which operations were
 * acknowledged and which single one may have been interrupted.
 *
 * <p>Arguments: the data file, a random seed and the maximum number of operations.
 */
public final class StoreWriterMain {

  private StoreWriterMain() {}

  /**
   * @param document a document written by this class
   * @return whether its fields are consistent with each other (a torn or mixed version would not be)
   */
  static boolean consistent(Map<String, Object> document) {
    Object counter = document.get("counter");
    return counter instanceof Number number
        && document.get("check").equals(check(String.valueOf(document.get("key")), number.intValue()));
  }

  static String check(String key, int counter) {
    return key + "/" + counter + "/" + "ñ€中".repeat(1 + counter % 5);
  }

  static Map<String, Object> document(String id, String key, int counter) {
    Map<String, Object> document = new LinkedHashMap<>();
    if (id != null) {
      document.put("_id", id);
    }
    document.put("key", key);
    document.put("counter", counter);
    document.put("check", check(key, counter));
    document.put("payload", List.of(counter, Map.of("padding", "x".repeat(counter % 200))));
    return document;
  }

  /**
   * @param args the data file, the seed and the maximum number of operations
   */
  @SuppressWarnings("unchecked")
  public static void main(String[] args) {
    Path file = Path.of(args[0]);
    long seed = Long.parseLong(args[1]);
    int maxOperations = Integer.parseInt(args[2]);
    Datastore datastore = new Datastore(DatastoreOptions.of(file).withAutocompactionLineThreshold(150)
        .withWarnings(message -> System.out.println("WARN " + message)));
    datastore.loadDatabase();
    datastore.ensureIndex(IndexOptions.unique("key", true));
    Map<String, Map<String, Object>> documents = new TreeMap<>();
    for (Map<String, Object> document : datastore.getAllData()) {
      documents.put((String) document.get("_id"), document);
    }
    System.out.println("READY " + documents.size());
    Random random = new Random(seed);
    for (int i = 0; i < maxOperations; i++) {
      int choice = random.nextInt(20);
      if (choice == 0) {
        System.out.println("BEGIN compact");
        datastore.compactDatafile();
      } else if (documents.isEmpty() || choice < 10) {
        String id = datastore.createNewId();
        String key = seed + "-" + i;
        System.out.println("BEGIN insert " + id + " " + key + " 0");
        documents.put(id, datastore.insert(document(id, key, 0)));
      } else if (choice < 17) {
        String id = documents.keySet().stream().skip(random.nextInt(documents.size())).findFirst().orElseThrow();
        Map<String, Object> current = documents.get(id);
        int counter = ((Number) current.get("counter")).intValue() + 1;
        System.out.println("BEGIN update " + id + " " + current.get("key") + " " + counter);
        UpdateResult result = datastore.update(Map.of("_id", id),
            document(null, (String) current.get("key"), counter), new UpdateOptions(false, false, true));
        documents.put(id, (Map<String, Object>) result.affectedDocuments());
      } else {
        String id = documents.keySet().stream().skip(random.nextInt(documents.size())).findFirst().orElseThrow();
        System.out.println("BEGIN remove " + id);
        datastore.remove(Map.of("_id", id), false);
        documents.remove(id);
      }
      System.out.println("ACK");
    }
    datastore.close();
    System.out.println("DONE");
  }
}
