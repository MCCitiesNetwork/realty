/**
 * Prices come back as doubles. Grouping separators make four- and five-figure
 * amounts comparable down a column at a glance; trailing ".00" on whole numbers
 * is noise, so it is dropped.
 */
export function formatPrice(value: number): string {
  return new Intl.NumberFormat(undefined, {
    minimumFractionDigits: 0,
    maximumFractionDigits: Number.isInteger(value) ? 0 : 2,
  }).format(value);
}
