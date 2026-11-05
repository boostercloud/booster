package io.boostercloud.booster.azure;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/** Small document boundary used to contract-test the transactional algorithm independently of Azure. */
interface CosmosDocuments {
  record Document(String id, JsonNode value, String etag) {
    public Document { value = value.deepCopy(); }
    @Override public JsonNode value() { return value.deepCopy(); }
  }
  record Write(String id, JsonNode value, String expectedEtag) {
    public Write { value = value.deepCopy(); }
  }
  Optional<Document> read(String id);
  List<Document> query(String sql, Map<String, Object> parameters, int limit);
  /** Atomic create (null ETag) or conditional replace. Returns false only for a precondition conflict. */
  boolean transact(List<Write> writes);
}
