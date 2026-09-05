import { Alert, Flex, Skeleton, Space, Tag, Typography } from "antd";
import type { ApiClient } from "../../api/client";
import type { components } from "../../api/schema";
import { useQuery } from "../../api/useQuery";
import { PlayerLink } from "../../ui/PlayerLink";

const { Text } = Typography;

type Party = components["schemas"]["RegionMembersResponse_Party"];

type Props = { client: ApiClient; world: string; region: string };

/**
 * Who WorldGuard lets in, which is a different question from who Realty says holds
 * the title. Answered by the game server's module, so its absence is reported as
 * exactly that rather than as an empty list -- "nobody" and "could not ask" differ.
 */
export function RegionAccess({ client, world, region }: Props) {
  const members = useQuery(
    () => client.GET("/v1/region/members", { params: { query: { world, region } } }),
    [client, world, region],
  );

  if (members.status === "loading") return <Skeleton active paragraph={{ rows: 3 }} title={false} />;
  if (members.status === "error") {
    if (members.error.httpStatus === 502) {
      return (
        <Alert
          type="info"
          showIcon
          message="Not available right now"
          description="WorldGuard's owner and member lists come through the game server's query-service module, which is not reachable."
        />
      );
    }
    if (members.error.httpStatus === 404) {
      return <Alert type="warning" showIcon message="WorldGuard no longer holds this region." />;
    }
    return <Alert type="error" showIcon message="Could not load this region's access lists." />;
  }

  return (
    <Flex vertical gap={16}>
      <Domain title="Owners" hint="Full control in WorldGuard" party={members.data.owners} />
      <Domain title="Members" hint="May build, but not manage" party={members.data.members} />
    </Flex>
  );
}

/** WorldGuard's three kinds of entry, kept apart as the API keeps them. */
function Domain({ title, hint, party }: { title: string; hint: string; party: Party }) {
  const empty = party.players.length + party.playerNames.length + party.groups.length === 0;
  return (
    <div>
      <Flex vertical style={{ marginBottom: 6 }}>
        <Text strong>{title}</Text>
        <Text type="secondary" style={{ fontSize: 12 }}>{hint}</Text>
      </Flex>
      {empty
        ? <Text type="secondary">Nobody</Text>
        : (
          <Space orientation="vertical" size={6}>
            {party.players.length > 0 && (
              <Space size={[8, 4]} wrap>
                {party.players.map((player) => <PlayerLink key={player.id} player={player} />)}
              </Space>
            )}
            {party.playerNames.length > 0 && (
              <div>
                <Text type="secondary" style={{ fontSize: 12 }}>By name only </Text>
                <Space size={[6, 4]} wrap>
                  {party.playerNames.map((name) => <Text key={name} code>{name}</Text>)}
                </Space>
              </div>
            )}
            {party.groups.length > 0 && (
              <div>
                <Text type="secondary" style={{ fontSize: 12 }}>Groups </Text>
                <Space size={[4, 4]} wrap>
                  {party.groups.map((group) => <Tag key={group}>{group}</Tag>)}
                </Space>
              </div>
            )}
          </Space>
        )}
    </div>
  );
}
