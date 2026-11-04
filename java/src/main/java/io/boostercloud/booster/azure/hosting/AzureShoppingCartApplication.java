package io.boostercloud.booster.azure.hosting;
import io.boostercloud.booster.azure.AzureProvider;
import io.boostercloud.booster.core.Booster;
import io.boostercloud.booster.example.ShoppingCartApplication;
/** Explicitly public sample; production applications define their own policies and authentication. */
public final class AzureShoppingCartApplication implements AzureApplication {
  public AzureShoppingCartApplication() {}
  @Override public Booster create(AzureProvider provider) {
    return ShoppingCartApplication.create(provider.eventStore(), provider.stateStore());
  }
}
