package io.boostercloud.booster.core;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

public final class Json {
  private Json() {}
  public static final ObjectMapper MAPPER = JsonMapper.builder().findAndAddModules()
      .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
}
