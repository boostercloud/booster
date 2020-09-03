export interface Money {
  cents: number
  currency: string
}

export const emptyPrice = {
  cents: 0,
  currency: '',
}
