import { UserOutlined } from "@ant-design/icons";
import { App, AutoComplete, Avatar, Flex, Typography } from "antd";
import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import type { ApiClient } from "../api/client";
import { playerPath } from "../api/paths";
import { formatCount } from "./format";

const { Text } = Typography;

/** A player the register knows by name: a title holder from the leaderboard. */
type KnownPlayer = { id: string; name: string; plotCount: number };

const LEADERBOARD_PAGE = 100;
const MAX_SUGGESTIONS = 8;

/**
 * Every named title holder, from the leaderboard, fetched once per client on the first
 * focus and kept for the session. The API has no player search; this is the list of
 * players it can name without the module, and the one a visitor is most likely to be
 * looking for -- somebody who holds a plot.
 */
const knownPlayers = new WeakMap<ApiClient, Promise<KnownPlayer[]>>();

function loadKnownPlayers(client: ApiClient): Promise<KnownPlayer[]> {
  let loading = knownPlayers.get(client);
  if (!loading) {
    loading = (async () => {
      const players: KnownPlayer[] = [];
      for (let page = 1, totalPages = 1; page <= totalPages; page++) {
        const { data } = await client.GET("/v1/leaderboard/owners", {
          params: { query: { page, pageSize: LEADERBOARD_PAGE } },
        });
        if (!data) break;
        totalPages = data.totalPages;
        for (const owner of data.owners) {
          // Unnamed players cannot be typed for, so they are not suggested.
          if (owner.player.name) {
            players.push({ id: owner.player.id, name: owner.player.name, plotCount: owner.plotCount });
          }
        }
      }
      return players;
    })();
    knownPlayers.set(client, loading);
  }
  return loading;
}

/** The player's head, from the skin service the name resolvers themselves link to. */
export function headUrl(id: string): string {
  return `https://crafthead.net/avatar/${id.replace(/-/g, "")}/32`;
}

/**
 * The public resolver of last resort, asked only when the game server's module cannot
 * be. Names a player the server has never met too, which is fine: their page then
 * reports, truthfully, that they hold nothing here.
 */
async function lookupOnPlayerDb(name: string): Promise<string | null> {
  const response = await fetch(`https://playerdb.co/api/player/minecraft/${encodeURIComponent(name)}`);
  if (!response.ok) return null;
  const body = (await response.json()) as { data?: { player?: { id?: string } } };
  return body.data?.player?.id ?? null;
}

/**
 * A name, to a player's page -- with the names the register knows offered as you type.
 *
 * Suggestions come from the leaderboard, so they are players who hold something here.
 * A name outside that list is resolved through the game server's module, and when the
 * module is down, through playerdb.co; "no such player" and "could not ask" are kept
 * apart, since telling a visitor the first when the second is true sends them away.
 */
export function PlayerFinder({ client }: { client: ApiClient }) {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const [text, setText] = useState("");
  const [known, setKnown] = useState<KnownPlayer[] | null>(null);
  const [busy, setBusy] = useState(false);

  const suggestions = useMemo(() => {
    const needle = text.trim().toLowerCase();
    if (!known || !needle) return [];
    return known
      .filter((player) => player.name.toLowerCase().includes(needle))
      .slice(0, MAX_SUGGESTIONS);
  }, [known, text]);

  const warmUp = () => {
    if (known !== null) return;
    void loadKnownPlayers(client).then(setKnown);
  };

  async function find(raw: string) {
    const name = raw.trim();
    if (!name) return;
    const exact = known?.find((player) => player.name.toLowerCase() === name.toLowerCase());
    if (exact) {
      navigate(playerPath(exact.id));
      return;
    }
    setBusy(true);
    try {
      const { data, response } = await client.GET("/v1/players/lookup", {
        params: { query: { playerName: name } },
      });
      if (data) {
        navigate(playerPath(data));
      } else if (response?.status === 404) {
        void message.warning(`No player named ${name}.`);
      } else if (response?.status === 502) {
        // The module is the authority; playerdb.co is the fallback, not the first call.
        let id: string | null;
        try {
          id = await lookupOnPlayerDb(name);
        } catch {
          void message.error(
            "Player names can't be looked up right now: the game server's query-service module is not reachable.",
          );
          return;
        }
        if (id) navigate(playerPath(id));
        else void message.warning(`No player named ${name}.`);
      } else {
        void message.error("Could not look that player up.");
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <AutoComplete
      value={text}
      onChange={setText}
      onFocus={warmUp}
      onSelect={(_, option) => navigate(playerPath(String(option.id)))}
      onKeyDown={(event) => {
        // With suggestions showing, Enter picks the highlighted one; only a name the
        // register does not know needs resolving.
        if (event.key === "Enter" && suggestions.length === 0) void find(text);
      }}
      options={suggestions.map((player) => ({
        value: player.name,
        id: player.id,
        label: (
          <Flex align="center" gap={8}>
            <Avatar size={24} shape="square" src={headUrl(player.id)} icon={<UserOutlined />} />
            <span>{player.name}</span>
            <Text type="secondary" style={{ fontSize: 12 }}>
              {formatCount(player.plotCount)} {player.plotCount === 1 ? "plot" : "plots"}
            </Text>
          </Flex>
        ),
      }))}
      placeholder="Find a player"
      allowClear
      disabled={busy}
      style={{ width: 240 }}
    />
  );
}
