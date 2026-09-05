import L from "leaflet";
import { LOD_COUNT, LOD_FACTOR } from "./blueMap";

/**
 * Blocks as the coordinate system, north up, lined up with BlueMap's tile grid.
 *
 * <p>Latitude carries the world's z and longitude its x, so a larger z is further down
 * the screen and north is up -- the way the server's own map reads, and the way the
 * plot outlines have to sit if they are to land on the buildings underneath them.</p>
 *
 * <p>The zoom levels are five apart rather than the two a web map is normally built on,
 * because five is the ratio BlueMap renders its levels at. Leaflet derives the tile grid,
 * the pan limits and every screen position from these two functions, so replacing them
 * is all it takes to move a map onto a different ladder of levels.</p>
 *
 * <p>The scale is pixels per block, fixed so that the finest level draws a block as a
 * pixel: at zoom {@code LOD_COUNT - 1} a 500-block tile is a 500-pixel tile, which is
 * what makes the grid Leaflet asks for the grid BlueMap stored.</p>
 */
export const BLOCK_CRS = L.extend({}, L.CRS.Simple, {
  transformation: new L.Transformation(1, 0, 1, 0),
  scale: (zoom: number) => LOD_FACTOR ** zoom / LOD_FACTOR ** (LOD_COUNT - 1),
  zoom: (scale: number) => Math.log(scale * LOD_FACTOR ** (LOD_COUNT - 1)) / Math.log(LOD_FACTOR),
}) as L.CRS;

/** A block position as Leaflet wants it, which is z before x. */
export function atBlock(x: number, z: number): L.LatLngTuple {
  return [z, x];
}

/** The edge of a Minecraft world: no block lies beyond it, so no coordinate should. */
const WORLD_EDGE = 30_000_000;

/**
 * One typed block coordinate, or undefined for anything that is not one.
 *
 * <p>Whole numbers only, since a block is one; a fraction is rounded down to the block
 * it is in, the way the game does with a position. "1,204" is accepted for the same
 * reason the readout prints it -- it is how the number reads.</p>
 */
export function parseBlockCoordinate(text: string): number | undefined {
  const cleaned = text.trim().replace(/,/g, "");
  if (!/^-?\d+(\.\d+)?$/.test(cleaned)) return undefined;
  const value = Math.floor(Number(cleaned));
  return Math.abs(value) <= WORLD_EDGE ? value : undefined;
}
