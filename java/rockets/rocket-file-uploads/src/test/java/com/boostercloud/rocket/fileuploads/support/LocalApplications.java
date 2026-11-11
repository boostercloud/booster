package com.boostercloud.rocket.fileuploads.support;

import com.boostercloud.application.tester.ApplicationOutputs;
import com.boostercloud.application.tester.ApplicationTester;
import com.boostercloud.application.tester.Counters;
import com.boostercloud.application.tester.ProviderTestHelper;
import com.boostercloud.application.tester.Queries;
import com.boostercloud.framework.provider.local.infrastructure.testhelper.LocalTestHelper;
import com.boostercloud.rocket.fileuploads.types.UploadedFileEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Helpers of the end-to-end tests against a local server. */
public final class LocalApplications {

  private LocalApplications() {}

  /**
   * @param helper the local test helper of the running server
   * @return an application tester over it (GraphQL client, counters and queries of the local stores)
   */
  public static ApplicationTester tester(LocalTestHelper helper) {
    return new ApplicationTester(ProviderTestHelper.of(
        new ApplicationOutputs(helper.outputs().graphqlURL(), helper.outputs().websocketURL(),
            helper.outputs().healthURL()),
        new Counters() {
          @Override
          public long subscriptions() {
            return helper.counters().subscriptions();
          }

          @Override
          public long connections() {
            return helper.counters().connections();
          }

          @Override
          public long readModels(String readModelName) {
            return helper.counters().readModels(readModelName);
          }

          @Override
          public long events() {
            return helper.counters().events();
          }
        },
        new Queries() {
          @Override
          public List<Map<String, Object>> events(String primaryKey) {
            return helper.queries().events(primaryKey);
          }

          @Override
          public List<Map<String, Object>> readModels(String primaryKey, String readModelName) {
            return helper.queries().readModels(primaryKey, readModelName);
          }
        }));
  }

  /**
   * @param port the port of the server when the file was notified
   * @param storage the storage
   * @param name the path in the container ({@code client1/myfile.txt})
   * @return the metadata the local watcher sends ({@code { BOOSTER_ROCKET_FUNCTION_ID, uri, name }}, in that order)
   */
  public static Map<String, Object> localMetadata(int port, String storage, String name) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("BOOSTER_ROCKET_FUNCTION_ID", "rocket-files");
    metadata.put("uri", "http://localhost:" + port + "/" + storage + "/rocketfiles/" + name);
    metadata.put("name", name);
    return metadata;
  }

  /**
   * @param metadata upload metadata
   * @return the id of its {@code UploadedFileEntity} (and of the README read model): the md5 of its JSON
   */
  public static String entityId(Map<String, Object> metadata) {
    return new UploadedFileEvent(metadata).entityID().value();
  }
}
