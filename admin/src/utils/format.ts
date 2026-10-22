export function formatMoneyCent(value: number | null | undefined): string {
  if (value === null || value === undefined) return '-'
  return '¥' + (value / 100).toFixed(2)
}

export function formatQuantity(value: number | string | null | undefined): string {
  if (value === null || value === undefined) return '-'
  return Number(value).toFixed(3).replace(/\.0+$/, '').replace(/(\.\d*?)0+$/, '$1')
}

export function formatDateTime(value: string | null | undefined): string {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
}
