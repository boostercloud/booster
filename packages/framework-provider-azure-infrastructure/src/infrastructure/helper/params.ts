/**
 * Azure-specific configurations
 */
export const configuration = {
  appId: process.env['AZURE_APP_ID'] ?? '',
  secret: process.env['AZURE_SECRET'] ?? '',
  tenantId: process.env['AZURE_TENANT_ID'] ?? '',
  subscriptionId: process.env['AZURE_SUBSCRIPTION_ID'] ?? '',
  region: process.env['REGION'] ?? '',
  publisherEmail: 'noreply@booster.cloud',
  publisherName: 'Booster App',
}
