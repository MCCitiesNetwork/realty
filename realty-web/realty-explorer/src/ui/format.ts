/**
 * Prices come back as doubles and are shown abbreviated: "12.5k", "1.23m".
 * A listing card and a fact row are a fixed width, and a 67-digit grouped
 * string -- a valid double -- simply does not fit; an abbreviation is the same
 * width for a thousand as for a septillion. Below a thousand the value is shown
 * in full, since there is nothing to abbreviate.
 *
 * Suffixes follow the short scale, one per thousand, up to a vigintillion
 * (1e63). Past that scientific notation takes over rather than the list
 * growing forever: nothing on a server plausibly prices there, and "1E66" still
 * says what the number is.
 */
const SUFFIXES = [
  "", "k", "m", "b", "t", "quad", "quint", "sext", "sept", "oct", "non",
  "dec", "undec", "duodec", "tredec", "quattuordec", "quindec", "sexdec",
  "septendec", "octodec", "novemdec", "vig",
];

const mantissa = new Intl.NumberFormat("en", {
  minimumFractionDigits: 0,
  maximumFractionDigits: 2,
});

const scientific = new Intl.NumberFormat("en", {
  notation: "scientific",
  maximumFractionDigits: 1,
});

export function formatPrice(value: number): string {
  const magnitude = Math.abs(value);
  if (magnitude < 1000) {
    return mantissa.format(value);
  }
  let tier = Math.floor(Math.log10(magnitude) / 3);
  if (tier >= SUFFIXES.length) {
    return scientific.format(value);
  }
  let scaled = value / 10 ** (tier * 3);
  // 999,999 scales to 999.999, which rounds to "1,000k"; that is the next tier.
  if (Math.abs(Number(mantissa.format(scaled).replace(/,/g, ""))) >= 1000) {
    tier += 1;
    if (tier >= SUFFIXES.length) {
      return scientific.format(value);
    }
    scaled = value / 10 ** (tier * 3);
  }
  return mantissa.format(scaled) + SUFFIXES[tier];
}
