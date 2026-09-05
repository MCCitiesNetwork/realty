/**
 * Prices come back as doubles and are shown abbreviated: "12.5k", "1.23m".
 * A listing card and a fact row are a fixed width, and a 67-digit grouped
 * string -- a valid double -- simply does not fit; an abbreviation is the same
 * width for a thousand as for a septillion. Below a thousand the value is shown
 * in full, since there is nothing to abbreviate.
 *
 * No currency symbol is attached. The API reports a number and nothing about
 * the economy behind it, and a "$" the server never said would be a fiction.
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

const grouped = new Intl.NumberFormat("en", { maximumFractionDigits: 2 });

/** The unabbreviated figure, for a tooltip beside the abbreviated one. */
export function formatPriceInFull(value: number): string {
  return grouped.format(value);
}

/** Whole counts, grouped: 7,782. */
export function formatCount(value: number): string {
  return grouped.format(value);
}

const DURATION_UNITS: ReadonlyArray<[label: string, seconds: number]> = [
  ["day", 86_400],
  ["hour", 3_600],
  ["minute", 60],
  ["second", 1],
];

/**
 * A lease term or a bidding window in its largest whole-ish unit: 2,592,000 seconds
 * is "30 days", not a figure nobody can read. One decimal at most, so "1.5 days"
 * rather than a second unit.
 */
export function formatDuration(seconds: number): string {
  for (const [unit, size] of DURATION_UNITS) {
    if (seconds >= size) {
      const count = seconds / size;
      const shown = Number.isInteger(count) ? count : Math.round(count * 10) / 10;
      return `${grouped.format(shown)} ${unit}${shown === 1 ? "" : "s"}`;
    }
  }
  return "0 seconds";
}

const dateTime = new Intl.DateTimeFormat("en", { dateStyle: "medium", timeStyle: "short" });

export function formatDate(iso: string): string {
  return dateTime.format(new Date(iso));
}

const dayOnly = new Intl.DateTimeFormat("en", { dateStyle: "full" });
const timeOnly = new Intl.DateTimeFormat("en", { timeStyle: "short" });

/** "Thursday, September 3, 2026" -- the heading a feed groups its events under. */
export function formatDay(iso: string): string {
  return dayOnly.format(new Date(iso));
}

/** "9:37 PM" -- the time alone, for an event already under its day's heading. */
export function formatTime(iso: string): string {
  return timeOnly.format(new Date(iso));
}

const relative = new Intl.RelativeTimeFormat("en", { numeric: "auto" });

const RELATIVE_UNITS: ReadonlyArray<[Intl.RelativeTimeFormatUnit, number]> = [
  ["year", 31_536_000],
  ["month", 2_592_000],
  ["week", 604_800],
  ["day", 86_400],
  ["hour", 3_600],
  ["minute", 60],
];

/** "3 days ago", "in 2 hours". `now` is a parameter so a test can pin it. */
export function formatRelative(iso: string, now: number = Date.now()): string {
  const delta = (new Date(iso).getTime() - now) / 1000;
  const magnitude = Math.abs(delta);
  for (const [unit, size] of RELATIVE_UNITS) {
    if (magnitude >= size) return relative.format(Math.round(delta / size), unit);
  }
  return relative.format(Math.round(delta), "second");
}

/** The first block of a UUID, which is what a player is called when the module cannot name them. */
export function shortId(uuid: string): string {
  return uuid.slice(0, 8);
}

/** `FOR_SALE` reads "For sale". For an enum value no label below has claimed. */
export function humanise(value: string): string {
  const words = value.toLowerCase().replace(/_/g, " ");
  return words.charAt(0).toUpperCase() + words.slice(1);
}

/**
 * Plain-English names for the history event types. These are labels for the API's own
 * enum, not data: an event the list does not know still renders, via `humanise`.
 */
const EVENT_LABELS: Readonly<Record<string, string>> = {
  BUY: "Bought",
  AUCTION_BUY: "Won at auction",
  OFFER_BUY: "Bought by offer",
  RENT: "Rented",
  UNRENT: "Rental ended",
  RENEW: "Lease renewed",
  LEASEHOLD_EXPIRY: "Lease expired",
  SET_PRICE: "Price set",
  UNSET_PRICE: "Price removed",
  SET_TITLEHOLDER: "Title holder set",
  UNSET_TITLEHOLDER: "Title holder removed",
  SET_DURATION: "Term set",
  SET_LANDLORD: "Landlord set",
  SET_TENANT: "Tenant set",
  UNSET_TENANT: "Tenant removed",
  SET_MAX_EXTENSIONS: "Extension limit set",
  MODIFY_PROPOSE: "Change proposed",
  MODIFY_ACCEPT: "Change accepted",
  MODIFY_REJECT: "Change rejected",
  MODIFY_WITHDRAW: "Change withdrawn",
  MODIFY_APPLY: "Change applied",
  TERMINATE: "Notice given",
  TERMINATION_CANCEL: "Notice withdrawn",
  AGENT_ADD: "Agent added",
  AGENT_REMOVE: "Agent removed",
};

export function eventLabel(eventType: string): string {
  return EVENT_LABELS[eventType] ?? humanise(eventType);
}

/** Every event type the API accepts, in the order the spec lists them. */
export const EVENT_TYPES: ReadonlyArray<string> = [
  "BUY", "AUCTION_BUY", "OFFER_BUY", "AGENT_ADD", "AGENT_REMOVE", "RENT", "UNRENT",
  "RENEW", "LEASEHOLD_EXPIRY", "SET_PRICE", "UNSET_PRICE", "SET_TITLEHOLDER",
  "UNSET_TITLEHOLDER", "SET_DURATION", "SET_LANDLORD", "SET_TENANT", "UNSET_TENANT",
  "SET_MAX_EXTENSIONS", "MODIFY_PROPOSE", "MODIFY_ACCEPT", "MODIFY_REJECT",
  "MODIFY_WITHDRAW", "MODIFY_APPLY", "TERMINATE", "TERMINATION_CANCEL",
];
