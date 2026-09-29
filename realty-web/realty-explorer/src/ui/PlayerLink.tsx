import { Tooltip, Typography } from "antd";
import { Link } from "react-router-dom";
import { playerPath } from "../api/paths";
import type { components } from "../api/schema";
import { shortId } from "./format";

type PartyRef = components["schemas"]["PartyRef"];

/**
 * A party, named where the API could name them.
 *
 * A player is a link to their page. Any other party -- a Treasury account or a permission
 * group -- has no page, so it is text with its kind: `GovSecurity (government)`. Its name
 * may be one a player chose, so it is only ever rendered as text.
 *
 * A null name means the query-service module was unreachable, not that the party is
 * anonymous, so the id stands in -- a player's first UUID block, with the whole id on
 * hover. Nothing is made up to fill the gap.
 */
export function PlayerLink({ player }: { player: PartyRef }) {
  if (player.kind !== "personal") {
    return <span>{playerLabel(player)}</span>;
  }
  return (
    <Tooltip title={player.id}>
      <Link to={playerPath(player)}>
        {player.name ?? <Typography.Text code>{shortId(player.id)}</Typography.Text>}
      </Link>
    </Tooltip>
  );
}

/** The same naming rule, as plain text, for a heading. */
export function playerLabel(party: PartyRef): string {
  if (party.kind === "personal") return party.name ?? shortId(party.id);
  return `${party.name ?? `#${party.id}`} (${party.kind})`;
}
