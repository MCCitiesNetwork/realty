import { Space, Typography } from "antd";
import type { components } from "../api/schema";
import { formatDuration } from "./format";
import { PlayerLink } from "./PlayerLink";
import { Price } from "./Price";

type PlayerRef = components["schemas"]["PlayerRef"];

/** The fields a history entry and an activity event share; either type fits. */
export type EventLike = components["schemas"]["HistoryResponse_Entry"];

type Party = { role: string; player: PlayerRef };

const SALES = new Set(["BUY", "AUCTION_BUY", "OFFER_BUY"]);
const LETTINGS = new Set(["RENT", "RENEW"]);
const ENDINGS = new Set(["UNRENT", "LEASEHOLD_EXPIRY", "TERMINATE"]);

/**
 * A timeline dot colour by what the event did: money changed hands, a lease began, or
 * something ended. Everything administrative stays grey. Labels, not data -- an event
 * type outside these sets still renders, uncoloured.
 */
export function eventColor(eventType: string): string {
  if (SALES.has(eventType)) return "green";
  if (LETTINGS.has(eventType)) return "blue";
  if (ENDINGS.has(eventType)) return "red";
  return "gray";
}

/**
 * Whoever the event names, under the API's own field names. `kind` decides which
 * fields are present, and the others are absent rather than null, so this simply
 * lists the ones that arrived.
 */
export function partiesOf(event: EventLike): Party[] {
  const parties: Party[] = [];
  const add = (role: string, player?: PlayerRef) => {
    if (player) parties.push({ role, player });
  };
  add("Buyer", event.buyer);
  add("Authority", event.authority);
  add("Tenant", event.tenant);
  add("Landlord", event.landlord);
  add("Agent", event.agent);
  add("Actor", event.actor);
  return parties;
}

export function EventParties({ event }: { event: EventLike }) {
  const parties = partiesOf(event);
  if (parties.length === 0) return null;
  return (
    <Space size={[12, 0]} wrap>
      {parties.map((party) => (
        <span key={party.role}>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>{party.role} </Typography.Text>
          <PlayerLink player={party.player} />
        </span>
      ))}
    </Space>
  );
}

/** The money and the terms, where the event carried them. */
export function EventTerms({ event }: { event: EventLike }) {
  const parts: React.ReactNode[] = [];
  if (event.price !== undefined) {
    parts.push(<Price key="price" value={event.price} />);
  }
  if (event.durationSeconds !== undefined) {
    parts.push(<Typography.Text key="term" type="secondary">{formatDuration(event.durationSeconds)}</Typography.Text>);
  }
  if (event.extensionsRemaining !== undefined) {
    parts.push(
      <Typography.Text key="ext" type="secondary">
        {event.extensionsRemaining} extension{event.extensionsRemaining === 1 ? "" : "s"} left
      </Typography.Text>,
    );
  }
  if (parts.length === 0) return null;
  return <Space size={[10, 0]} wrap>{parts}</Space>;
}
