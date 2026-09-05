import type { MapConfig } from "../config";

/**
 * Addressing for BlueMap's low-resolution tiles, which is all a web page can draw.
 *
 * <p>BlueMap renders two things per map. The high-resolution level is 3D geometry --
 * meshes a browser has to light and project -- and is what the BlueMap site itself
 * shows. The low-resolution levels are ordinary PNGs, one pixel per block at the finest
 * level, and they are what make a flat plan of a world cheap to draw underneath
 * something else. This module addresses those.</p>
 *
 * <p>The numbers below are BlueMap's defaults, which it also publishes per map at
 * {@code /maps/<id>/settings.json}. That file cannot be read from here -- BlueMap sends
 * no CORS headers -- so a server that has changed them will draw a map whose tiles do
 * not line up, rather than one that fails.</p>
 */

/** How many blocks one tile covers on a side at the finest level. */
export const BLOCKS_PER_TILE = 500;

/** How much more ground each coarser level covers per tile. */
export const LOD_FACTOR = 5;

/** How many low-resolution levels exist. Level 1 is the finest, and level 0 is the 3D one. */
export const LOD_COUNT = 3;

/**
 * The width in pixels of the part of a tile image that is the map.
 *
 * <p>A tile arrives 501 by 1002. The map is the top square, and the height and light
 * data BlueMap shades its own 3D view with is stacked underneath -- drawing the image
 * whole puts a band of near-black under every tile. The odd 501 is a sample per corner
 * rather than per block, so the last row and column repeat the neighbouring tile's
 * first: taking 500 of them tiles the plane exactly once.</p>
 */
export const TILE_MAP_PIXELS = BLOCKS_PER_TILE;

/** How many blocks a tile covers at one level. Level 1 is {@link BLOCKS_PER_TILE}. */
export function blocksPerTile(lod: number): number {
  return BLOCKS_PER_TILE * LOD_FACTOR ** (lod - 1);
}

/**
 * The level to draw at one Leaflet zoom, coarsest first.
 *
 * <p>Zoom 0 is the whole world at the coarsest level and each zoom in steps one level
 * finer, which is why the map's zoom levels are five times apart rather than the two a
 * web map usually is.</p>
 */
export function lodForZoom(zoom: number): number {
  return Math.min(LOD_COUNT, Math.max(1, LOD_COUNT - Math.round(zoom)));
}

/** The address of one tile image. */
export function tileUrl(baseUrl: string, mapId: string, lod: number, x: number, z: number): string {
  const map = encodeURIComponent(mapId);
  return `${baseUrl}/maps/${map}/tiles/${lod}/x${splitDigits(x)}/z${splitDigits(z)}.png`;
}

/**
 * A tile coordinate as BlueMap stores it: one directory per digit, the sign kept with
 * the first. Tile -12 is {@code -1/2} and tile 0 is {@code 0}.
 *
 * <p>This is the layout BlueMap writes to disk, so it is the only form a plain static
 * host can serve. BlueMap's own web server answers the undivided {@code -12} as well,
 * which is why the difference stays invisible until someone puts the map behind nginx.</p>
 */
function splitDigits(value: number): string {
  const whole = Math.trunc(value);
  const sign = whole < 0 ? "-" : "";
  return sign + String(Math.abs(whole)).split("").join("/");
}

/**
 * What BlueMap calls a world, which is not always what Realty calls it.
 *
 * <p>The configured id wins. Otherwise the lower-cased world name is the guess, since
 * that is the id BlueMap generates for a world the operator has not renamed.</p>
 */
export function mapIdFor(config: MapConfig, world: string): string {
  return config.ids[world] ?? world.toLowerCase();
}
