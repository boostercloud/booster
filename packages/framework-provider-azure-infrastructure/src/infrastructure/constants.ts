const DEFAULT_MAX_THROUGHPUT = '1000'
export const MAX_CONTAINER_THROUGHPUT = parseInt(process.env.AZURE_MAX_CONTAINER_THROUGHPUT ?? DEFAULT_MAX_THROUGHPUT, 10)
export const MAX_DATABASE_THROUGHPUT = parseInt(process.env.AZURE_MAX_DATABASE_THROUGHPUT ?? DEFAULT_MAX_THROUGHPUT, 10)

export const BASIC_SERVICE_PLAN = process.env.BOOSTER_AZURE_SERVICE_PLAN_BASIC ?? 'false'
export const USE_WAF = process.env.BOOSTER_USE_WAF ?? 'false'
