export enum Currency {
  EUR = 'EUR',
  USD = 'USD',
}

export interface Money {
  cents: number
  currency: Currency
}
