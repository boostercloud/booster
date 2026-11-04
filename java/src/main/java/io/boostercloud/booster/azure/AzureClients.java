package io.boostercloud.booster.azure;

import com.azure.core.credential.TokenCredential;
import com.azure.core.http.HttpClient;
import com.azure.core.http.ProxyOptions;
import com.azure.core.http.jdk.httpclient.JdkHttpClientBuilder;
import com.azure.core.util.Configuration;
import com.azure.identity.DefaultAzureCredentialBuilder;
import java.time.Duration;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** SDK construction preserving inherited proxy settings and the JVM's CA trust. */
public final class AzureClients {
  private AzureClients() {}
  public static ProxyOptions proxy() {
    ProxyOptions inherited = proxy(System.getenv());
    return inherited != null ? inherited : ProxyOptions.fromConfiguration(Configuration.getGlobalConfiguration());
  }
  static ProxyOptions proxy(Map<String, String> environment) {
    String value = null;
    for (String name : List.of("HTTPS_PROXY", "https_proxy", "HTTP_PROXY", "http_proxy")) {
      if (environment.containsKey(name) && !environment.get(name).isBlank()) { value = environment.get(name); break; }
    }
    if (value == null) return null;
    URI uri = URI.create(value);
    if (!"http".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getQuery() != null || uri.getFragment() != null)
      throw new IllegalArgumentException("Azure SDK proxy must be an HTTP CONNECT proxy URL");
    var options = new ProxyOptions(ProxyOptions.Type.HTTP, InetSocketAddress.createUnresolved(uri.getHost(), uri.getPort() < 0 ? 80 : uri.getPort()));
    if (uri.getRawUserInfo() != null) {
      String[] credentials = uri.getRawUserInfo().split(":", 2);
      options.setCredentials(URLDecoder.decode(credentials[0].replace("+", "%2B"), StandardCharsets.UTF_8),
          credentials.length == 2 ? URLDecoder.decode(credentials[1].replace("+", "%2B"), StandardCharsets.UTF_8) : "");
    }
    String excluded = environment.getOrDefault("NO_PROXY", environment.get("no_proxy"));
    if (excluded != null && !excluded.isBlank()) options.setNonProxyHosts(excluded.replace(",", "|"));
    return options;
  }
  public static HttpClient httpClient() {
    return new JdkHttpClientBuilder().proxy(proxy()).connectionTimeout(Duration.ofSeconds(15))
        .responseTimeout(Duration.ofSeconds(60)).readTimeout(Duration.ofSeconds(60)).build();
  }
  public static TokenCredential credential() { return new DefaultAzureCredentialBuilder().httpClient(httpClient()).build(); }
}
