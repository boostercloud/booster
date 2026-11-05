package io.boostercloud.booster.azure;

import com.azure.cosmos.*;
import com.azure.cosmos.models.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;

/** Cosmos SDK gateway; container must use /pk and Session or stronger consistency. */
final class SdkCosmosDocuments implements CosmosDocuments {
  private final CosmosContainer container;
  private final String application;
  private final PartitionKey partition;
  SdkCosmosDocuments(CosmosContainer container, String application) {
    this.container = Objects.requireNonNull(container);
    if (application == null || application.isBlank() || application.length() > 200)
      throw new IllegalArgumentException("Application partition must contain 1..200 characters");
    this.application = application; partition = new PartitionKey(application);
  }
  @Override public Optional<Document> read(String id) {
    try {
      var response = container.readItem(id, partition, JsonNode.class);
      return Optional.of(new Document(id, response.getItem(), response.getETag()));
    } catch (CosmosException error) { if (error.getStatusCode() == 404) return Optional.empty(); throw error; }
  }
  @Override public List<Document> query(String sql, Map<String, Object> parameters, int limit) {
    var specification = new SqlQuerySpec(sql, parameters.entrySet().stream().map(e -> new SqlParameter(e.getKey(), e.getValue())).toList());
    var options = new CosmosQueryRequestOptions().setPartitionKey(partition).setMaxDegreeOfParallelism(1);
    var result = new ArrayList<Document>();
    for (var page : container.queryItems(specification, options, JsonNode.class).iterableByPage(Math.min(limit, 256))) {
      for (var item : page.getResults()) {
        result.add(new Document(item.path("id").asText(), item, item.path("_etag").asText()));
        if (result.size() == limit) return List.copyOf(result);
      }
    }
    return List.copyOf(result);
  }
  @Override public boolean transact(List<Write> writes) {
    if (writes.isEmpty() || writes.size() > 100) throw new IllegalArgumentException("Cosmos batch requires 1..100 operations");
    var batch = CosmosBatch.createCosmosBatch(partition);
    for (var write : writes) {
      ObjectNode item = (ObjectNode) write.value().deepCopy();
      item.remove(List.of("_rid", "_self", "_etag", "_attachments", "_ts"));
      item.put("id", write.id()).put("pk", application);
      if (write.expectedEtag() == null) batch.createItemOperation(item);
      else batch.replaceItemOperation(write.id(), item, new CosmosBatchItemRequestOptions().setIfMatchETag(write.expectedEtag()));
    }
    try {
      var response = container.executeCosmosBatch(batch);
      if (response.isSuccessStatusCode()) return true;
      if (response.getStatusCode() == 409 || response.getStatusCode() == 412 || response.getResults().stream().anyMatch(r -> r.getStatusCode() == 409 || r.getStatusCode() == 412)) return false;
      throw new IllegalStateException("Cosmos transaction failed with HTTP " + response.getStatusCode());
    } catch (CosmosException failure) {
      if (failure.getStatusCode() == 409 || failure.getStatusCode() == 412) return false;
      throw failure;
    }
  }
}
