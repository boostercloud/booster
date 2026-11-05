package io.boostercloud.booster.azure;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class AzureClientsTest {
  @Test void inheritsHttpsProxyAndDoesNotDecodePlusAsSpace() {
    var options = AzureClients.proxy(Map.of("HTTPS_PROXY", "http://user:p%40ss+word@proxy.example:8080", "NO_PROXY", "localhost,127.0.0.1"));
    assertEquals("proxy.example", options.getAddress().getHostString()); assertEquals(8080, options.getAddress().getPort());
    assertEquals("user", options.getUsername()); assertEquals("p@ss+word", options.getPassword());
    assertTrue("localhost".matches(options.getNonProxyHosts()));
    assertTrue("127.0.0.1".matches(options.getNonProxyHosts()));
    assertFalse("127x0x0x1".matches(options.getNonProxyHosts()));
  }
  @Test void proxyOptionalAndInvalidProtocolsFailExplicitly() {
    assertNull(AzureClients.proxy(Map.of()));
    assertThrows(IllegalArgumentException.class, () -> AzureClients.proxy(Map.of("HTTPS_PROXY", "socks5://example:8080")));
    assertEquals(80, AzureClients.proxy(Map.of("http_proxy", "http://example")).getAddress().getPort());
  }
}
