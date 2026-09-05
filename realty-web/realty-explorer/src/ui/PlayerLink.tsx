import { Tooltip, Typography } from "antd";
import { Link } from "react-router-dom";
import { playerPath } from "../api/paths";
import type { components } from "../api/schema";
import { shortId } from "./format";

type PlayerRef = components["schemas"]["PlayerRef"];

/**
 * A player, named where the API could name them.
 *
 * A null name means the query-service module was unreachable, not that the player is
 * anonymous, so the UUID stands in -- its first block, with the whole id on hover.
 * Nothing is made up to fill the gap.
 */
export function PlayerLink({ player }: { player: PlayerRef }) {
  return (
    <Tooltip title={player.id}>
      <Link to={playerPath(player)}>
        {player.name ?? <Typography.Text code>{shortId(player.id)}</Typography.Text>}
      </Link>
    </Tooltip>
  );
}

/** The same naming rule, as plain text, for a heading. */
export function playerLabel(player: PlayerRef): string {
  return player.name ?? shortId(player.id);
}
