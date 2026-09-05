import L from "leaflet";
import "leaflet/dist/leaflet.css";
import { Button, Input, Space, type InputRef } from "antd";
import { useCallback, useEffect, useRef, type FormEvent } from "react";
import { formatDuration, formatPrice } from "../ui/format";
import { atBlock, BLOCK_CRS, parseBlockCoordinate } from "./blockCrs";
import { LOD_COUNT, LOD_FACTOR, TILE_MAP_PIXELS, lodForZoom, tileUrl } from "./blueMap";
import { extentOf, spanOf, type Extent, type Footprint } from "./footprints";
import { plotStyle } from "./plotStyle";
import type { Listing } from "./worldMap";

/** Which BlueMap map to draw underneath, or null to draw the plots on nothing. */
export type TileSource = { baseUrl: string; mapId: string };

type Props = {
  tiles: TileSource | null;
  footprints: readonly Footprint[];
  market: Map<string, Listing>;
  /** Legend labels the visitor has switched off; a plot of that kind is not drawn. */
  hidden: ReadonlySet<string>;
  /** True once the whole world has been read, so the stacking order can be settled. */
  settled: boolean;
  /**
   * A plot to bring into view, by region id; `at` changes each time so the same plot
   * can be asked for twice. Nothing happens for a plot not drawn (yet).
   */
  focus?: { regionId: string; at: number } | null;
  /** What goes in front of a price in a plot's tooltip; "" for none. */
  currency: string;
  /** Called with a region id when a visitor clicks its plot. */
  onSelect: (regionId: string) => void;
};

/** The finest level BlueMap drew, past which tiles are stretched rather than fetched. */
const MAX_NATIVE_ZOOM = LOD_COUNT - 1;

/** Where zooming stops: past a block this size the outlines stop meaning anything. */
const MAX_PIXELS_PER_BLOCK = 8;
const MAX_ZOOM = Math.log(MAX_PIXELS_PER_BLOCK * LOD_FACTOR ** MAX_NATIVE_ZOOM) / Math.log(LOD_FACTOR);

/**
 * How far a wheel notch zooms.
 *
 * <p>Leaflet's default assumes a zoom level is a doubling; here one is a five-fold
 * jump, so the default sends a visitor from a district to a continent in one notch.</p>
 */
const WHEEL_PIXELS_PER_ZOOM_LEVEL = 260;

/**
 * How close a typed coordinate brings the map, as pixels per block: near enough that
 * the plot around it is legible, and no closer if the visitor was already closer.
 */
const PIXELS_PER_BLOCK_AT_A_POINT = 4;
const ZOOM_AT_A_POINT = Math.log(PIXELS_PER_BLOCK_AT_A_POINT * LOD_FACTOR ** MAX_NATIVE_ZOOM) / Math.log(LOD_FACTOR);

const OUTLINE = { weight: 1, opacity: 0.9, fillOpacity: 0.35 };
const OUTLINE_HOVERED = { weight: 2, opacity: 1, fillOpacity: 0.6 };

/** One plot on the map, and the contract its colour and tooltip were drawn from. */
type Plot = { layer: L.Polygon; listing: Listing; footprint: Footprint };

/**
 * Which of two plots is drawn first, and so ends up underneath.
 *
 * <p>Priority first, because that is WorldGuard's own answer to which of two regions covering
 * one block governs it. A map that drew them the other way round would bury the region that
 * actually applies: a plot inside a district would vanish under the district. Between equals
 * the larger goes first, so a plot inside an equally-prioritised one is still visible.</p>
 *
 * <p>Leaflet draws canvas layers in the order they were added and hit-tests them the same way,
 * keeping the last match. Being drawn last is therefore also what makes a plot the one a
 * visitor's pointer finds, so this settles the tooltip and the click as well as the paint.</p>
 */
function stackedUnder(one: Footprint, other: Footprint): number {
  return one.priority !== other.priority
    ? one.priority - other.priority
    : spanOf(other) - spanOf(one);
}

/**
 * BlueMap's tiles, cropped to the half of each image that is the map.
 *
 * <p>A plain tile layer would stretch all 501 by 1002 pixels into the tile's square,
 * which draws the height data BlueMap packs underneath as a black band across the
 * bottom two thirds of the world. Drawing through a canvas takes the top square
 * instead.</p>
 *
 * <p>The image is deliberately loaded without a cross-origin request. BlueMap sends no
 * CORS headers, so asking for one would fail every tile; the cost is that the canvas is
 * tainted, which matters only to code that reads its pixels back, and none here does.</p>
 */
class BlueMapTiles extends L.GridLayer {
  private readonly source: TileSource;

  constructor(source: TileSource) {
    super({ tileSize: TILE_MAP_PIXELS, minZoom: 0, maxZoom: MAX_ZOOM, maxNativeZoom: MAX_NATIVE_ZOOM });
    this.source = source;
  }

  /**
   * Keeps the tiles it has across a zoom.
   *
   * <p>The map zooms without animating (see the map's options), and Leaflet treats a
   * zoom that did not animate as a view it cannot carry anything across: it fires
   * {@code viewprereset} and the stock tile layer throws every tile away on it, so each
   * wheel tick showed black ground until the new tiles had loaded. Not listening leaves
   * the old tiles scaled in place until the new ones land, which is what the same layer
   * does during an animated zoom anyway.</p>
   */
  getEvents(): { [name: string]: L.LeafletEventHandlerFn } {
    const events = { ...super.getEvents!() };
    delete events.viewprereset;
    return events;
  }

  protected createTile(coords: L.Coords, done: L.DoneCallback): HTMLElement {
    const canvas = document.createElement("canvas");
    canvas.width = TILE_MAP_PIXELS;
    canvas.height = TILE_MAP_PIXELS;
    // Blocks are the unit here, so an upscaled tile should show bigger blocks rather
    // than a blur of them.
    canvas.style.imageRendering = "pixelated";

    const context = canvas.getContext("2d");
    const image = new Image();
    image.onload = () => {
      context?.drawImage(image, 0, 0, TILE_MAP_PIXELS, TILE_MAP_PIXELS,
                         0, 0, TILE_MAP_PIXELS, TILE_MAP_PIXELS);
      done(undefined, canvas);
    };
    // BlueMap answers 204 for ground it has never rendered, which reaches an image as
    // a load failure. That is a blank tile, not a broken one.
    image.onerror = () => done(undefined, canvas);
    image.src = tileUrl(this.source.baseUrl, this.source.mapId, lodForZoom(coords.z), coords.x, coords.y);
    return canvas;
  }
}

/**
 * The canvas the plots are drawn on, guarded against being repainted after the map that
 * owned it has gone.
 *
 * <p>Leaflet books a repaint for the next frame whenever a plot is added, and forgets
 * that it booked one whenever something else makes it repaint early. The booked frame
 * is still coming, and nothing cancels it when the map is torn down, so it arrives at a
 * canvas whose drawing context has been thrown away and throws. Leaving the map soon
 * after its plots land is all it takes -- which is what clicking a plot does.</p>
 *
 * <p>A canvas with no context has nothing to repaint, so declining is the whole fix.</p>
 */
class PlotCanvas extends L.Canvas {
  _redraw(): void {
    if (!(this as unknown as { _ctx?: CanvasRenderingContext2D })._ctx) return;
    (L.Canvas.prototype as unknown as { _redraw: () => void })._redraw.call(this);
  }
}

/**
 * Every registered plot in one world, over the server's own render of it.
 *
 * <p>Mounted per world -- the screen keys it by world -- so nothing here has to cope
 * with the ground changing underneath the outlines. The plots themselves do change,
 * repeatedly: they arrive a page at a time and are added as they land, which is why
 * each one is kept by region id rather than redrawn from scratch.</p>
 */
export function RegionMap({ tiles, footprints, market, hidden, settled, currency, focus, onSelect }: Props) {
  const containerRef = useRef<HTMLDivElement>(null);
  const xRef = useRef<InputRef>(null);
  const zRef = useRef<InputRef>(null);
  const pinRef = useRef<L.CircleMarker | null>(null);
  const mapRef = useRef<L.Map | null>(null);
  const outlinesRef = useRef<L.LayerGroup | null>(null);
  const plotsRef = useRef(new Map<string, Plot>());
  const framedTo = useRef<Extent | null>(null);
  const visitorHasMoved = useRef(false);

  // Held in a ref so the drawing below depends on the plots alone. The screen rebuilds
  // this callback on every render, and a dependency on it would redraw the map each
  // time a page of regions landed.
  const select = useRef(onSelect);
  select.current = onSelect;
  // Likewise: a new page of plots consults the current switches without redrawing on
  // every flick of one; flicking one has its own effect, below.
  const restacked = useRef(false);
  const hiddenRef = useRef(hidden);
  hiddenRef.current = hidden;
  // Read when a tooltip is built, never a reason to rebuild one.
  const currencyRef = useRef(currency);
  currencyRef.current = currency;

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    const map = L.map(container, {
      crs: BLOCK_CRS,
      // Thousands of plots as SVG elements is thousands of DOM nodes; on a canvas it is
      // one, and Leaflet still hit-tests them for hover and clicks.
      renderer: new PlotCanvas(),
      minZoom: 0,
      maxZoom: MAX_ZOOM,
      zoomSnap: 0,
      zoomDelta: 0.4,
      wheelPxPerZoomLevel: WHEEL_PIXELS_PER_ZOOM_LEVEL,
      // Leaflet drops every wheel tick that lands while a zoom animation is playing,
      // and one plays for a quarter of a second per tick, so a continuous scroll
      // advanced in lurches with most of its input thrown away. Without the
      // animation each tick applies as it comes, and the zoom follows the wheel.
      zoomAnimation: false,
      attributionControl: false,
    });
    // Spawn, until the first regions arrive and say where the world actually is.
    map.setView(atBlock(0, 0), MAX_NATIVE_ZOOM - 1);
    if (tiles) new BlueMapTiles(tiles).addTo(map);

    const outlines = L.layerGroup().addTo(map);
    mapRef.current = map;
    outlinesRef.current = outlines;
    plotsRef.current = new Map();
    framedTo.current = null;
    visitorHasMoved.current = false;

    // The map keeps framing the plots as they arrive, but stops the moment the visitor
    // takes over -- being dragged back to the whole world mid-look is worse than
    // missing the last page of plots.
    const surrender = () => {
      visitorHasMoved.current = true;
    };
    map.on("mousedown", surrender);
    container.addEventListener("wheel", surrender, { passive: true });

    // The block under the pointer shows through the coordinate boxes as their
    // placeholders, so the same boxes read the map and take a destination. Written
    // straight to the elements: this fires on every mouse move, and a state update per
    // move would re-render the screen a hundred times a second.
    const showCoordinates = (event: L.LeafletMouseEvent) => {
      const x = xRef.current?.input;
      const z = zRef.current?.input;
      if (x) x.placeholder = String(Math.floor(event.latlng.lng));
      if (z) z.placeholder = String(Math.floor(event.latlng.lat));
    };
    map.on("mousemove", showCoordinates);
    map.on("mouseout", () => {
      const x = xRef.current?.input;
      const z = zRef.current?.input;
      if (x) x.placeholder = "";
      if (z) z.placeholder = "";
    });

    // The map is sized by its container, which the screen makes fill the space left
    // over; Leaflet only measures it once unless it is told.
    const resize = new ResizeObserver(() => map.invalidateSize());
    resize.observe(container);

    return () => {
      resize.disconnect();
      container.removeEventListener("wheel", surrender);
      // The outlines come off first, on purpose. Tearing down the map removes its
      // layers in an order of its own, and a plot removed after the canvas it was
      // drawn on asks that canvas to repaint itself after it has been thrown away.
      map.removeLayer(outlines);
      map.remove();
      mapRef.current = null;
      outlinesRef.current = null;
      pinRef.current = null;
      plotsRef.current = new Map();
    };
  }, [tiles]);

  // Typed coordinates: the map goes there, close enough to read the plot, and a pin
  // says which block was asked for. Going somewhere on purpose is the visitor taking
  // over, so the map stops re-framing the world as further plots land.
  const goToTyped = useCallback((event: FormEvent) => {
    event.preventDefault();
    const map = mapRef.current;
    const x = parseBlockCoordinate(xRef.current?.input?.value ?? "");
    const z = parseBlockCoordinate(zRef.current?.input?.value ?? "");
    if (!map || x === undefined || z === undefined) return;

    visitorHasMoved.current = true;
    const here = atBlock(x + 0.5, z + 0.5);
    map.setView(here, Math.max(map.getZoom(), ZOOM_AT_A_POINT), { animate: false });
    if (pinRef.current) pinRef.current.setLatLng(here);
    else {
      pinRef.current = L.circleMarker(here, {
        radius: 6, weight: 2, color: "#fff", fillColor: "#ff4d4f", fillOpacity: 1, interactive: false,
      }).addTo(map);
    }
  }, []);

  useEffect(() => {
    const map = mapRef.current;
    const outlines = outlinesRef.current;
    if (!map || !outlines) return;

    const plots = plotsRef.current;
    // Added in stacking order, so a page is right the moment it lands. A page arriving later
    // can still slide a low-priority region over a high-priority one already drawn, which is
    // what the pass below puts right once there are no more pages to come.
    const arriving = footprints
      .filter((footprint) => !plots.has(footprint.regionId) && market.has(footprint.regionId))
      .slice()
      .sort(stackedUnder);

    for (const footprint of arriving) {
      const listing = market.get(footprint.regionId)!;
      const layer = plotFor(footprint, select);
      const plot = { layer, listing, footprint };
      dress(layer, footprint.regionId, listing, currencyRef.current);
      plots.set(footprint.regionId, plot);
      place(outlines, plot, hiddenRef.current);
    }

    for (const footprint of footprints) {
      const listing = market.get(footprint.regionId);
      // The contracts arrive beside the outlines, page by page, and a plot is what its
      // contract makes it: one whose contract has not landed yet is not drawn yet, and
      // one the register holds no contract for is not drawn at all.
      if (!listing) continue;
      const drawn = plots.get(footprint.regionId);
      if (drawn && drawn.listing !== listing) {
        // Only the ones that changed are touched.
        drawn.listing = listing;
        dress(drawn.layer, footprint.regionId, listing, currencyRef.current);
        place(outlines, drawn, hiddenRef.current);
      }
    }

    // Once, when the last page has landed: bringing each plot to the front in stacking order
    // leaves the whole map in that order, however the pages happened to arrive.
    if (settled && !restacked.current && plots.size > 0) {
      restacked.current = true;
      restack(outlines, plots);
    }

    const extent = extentOf(footprints);
    if (extent && !visitorHasMoved.current && !sameExtent(extent, framedTo.current)) {
      framedTo.current = extent;
      map.fitBounds([atBlock(extent.minX, extent.minZ), atBlock(extent.maxX, extent.maxZ)],
                    // Not animated: the plots arrive in pages, and a flight across the
                    // world per page is motion sickness rather than feedback.
                    { padding: [24, 24], animate: false });
    }
  }, [footprints, market, settled]);

  useEffect(() => {
    const outlines = outlinesRef.current;
    if (!outlines) return;
    for (const plot of plotsRef.current.values()) place(outlines, plot, hidden);
    // Putting a plot back puts it on the end of the draw order, so switching a colour off
    // and on again would otherwise leave it sitting over everything it belongs under.
    restack(outlines, plotsRef.current);
  }, [hidden]);

  // Found by name: the map frames the plot, close enough to read it, and lights it up
  // for a moment so the eye lands on the right one among its neighbours. Going to a plot
  // on purpose is the visitor taking over, as typing coordinates is.
  useEffect(() => {
    const map = mapRef.current;
    const plot = focus ? plotsRef.current.get(focus.regionId) : undefined;
    if (!map || !plot) return;
    visitorHasMoved.current = true;
    map.fitBounds(plot.layer.getBounds(), { padding: [80, 80], maxZoom: ZOOM_AT_A_POINT, animate: false });
    plot.layer.setStyle(OUTLINE_HOVERED);
    plot.layer.openTooltip(plot.layer.getBounds().getCenter());
    const timer = setTimeout(() => {
      plot.layer.setStyle(OUTLINE);
      plot.layer.closeTooltip();
    }, 2500);
    return () => clearTimeout(timer);
  }, [focus]);

  return (
    <div style={{ position: "relative", height: "100%", width: "100%" }}>
      <div ref={containerRef} style={{ height: "100%", width: "100%", background: "#0b0f0b" }} />
      <form
        onSubmit={goToTyped}
        style={{
          position: "absolute", bottom: 8, left: 8, zIndex: 400,
          padding: 4, borderRadius: 6, background: "rgba(0, 0, 0, 0.55)",
        }}
      >
        <Space.Compact size="small">
          <Input ref={xRef} prefix="x" aria-label="x" inputMode="numeric" allowClear={false} style={{ width: 96, fontVariantNumeric: "tabular-nums" }} />
          <Input ref={zRef} prefix="z" aria-label="z" inputMode="numeric" allowClear={false} style={{ width: 96, fontVariantNumeric: "tabular-nums" }} />
          <Button htmlType="submit">Go</Button>
        </Space.Compact>
      </form>
    </div>
  );
}

function sameExtent(one: Extent, other: Extent | null): boolean {
  return other !== null
    && one.minX === other.minX && one.minZ === other.minZ
    && one.maxX === other.maxX && one.maxZ === other.maxZ;
}

/**
 * Redraws every plot on the map in stacking order.
 *
 * <p>Leaflet has no way to insert a layer at a depth, only to move one to the front, so the
 * order is rebuilt by moving each plot forward in turn. A plot not currently on the map is
 * left alone; it will be placed at the front and restacked when it comes back.</p>
 */
function restack(outlines: L.LayerGroup, plots: Map<string, Plot>): void {
  [...plots.values()]
    .sort((one, other) => stackedUnder(one.footprint, other.footprint))
    .filter((plot) => outlines.hasLayer(plot.layer))
    .forEach((plot) => plot.layer.bringToFront());
}

/** Puts a plot on the map or takes it off, as the visitor's switches say. */
function place(outlines: L.LayerGroup, plot: Plot, hidden: ReadonlySet<string>): void {
  const shown = !hidden.has(plotStyle(plot.listing).label);
  if (shown && !outlines.hasLayer(plot.layer)) outlines.addLayer(plot.layer);
  else if (!shown && outlines.hasLayer(plot.layer)) outlines.removeLayer(plot.layer);
}

function plotFor(footprint: Footprint, select: { current: (regionId: string) => void }): L.Polygon {
  const layer = L.polygon(footprint.outline.map((corner) => atBlock(corner.x, corner.z)));
  layer.on("mouseover", () => layer.setStyle(OUTLINE_HOVERED));
  layer.on("mouseout", () => layer.setStyle(OUTLINE));
  layer.on("click", () => select.current(footprint.regionId));
  return layer;
}

/** Gives a plot the colour and the tooltip its listing calls for. */
function dress(layer: L.Polygon, regionId: string, listing: Listing, currency: string): void {
  const style = plotStyle(listing);
  layer.setStyle({ ...OUTLINE, color: style.colour, fillColor: style.colour });

  const tooltip = tooltipFor(regionId, listing, style.label, currency);
  if (layer.getTooltip()) layer.setTooltipContent(tooltip);
  else layer.bindTooltip(tooltip, { direction: "top", sticky: true });
}

/**
 * Built as elements rather than a string of HTML.
 *
 * <p>A region id is whatever whoever made the region typed, and Leaflet renders a
 * string tooltip as markup -- so a region named with a tag would be running it here.
 * Text nodes cannot be anything but text.</p>
 */
function tooltipFor(regionId: string, listing: Listing, label: string, currency: string): HTMLElement {
  const tooltip = document.createElement("div");

  const name = document.createElement("strong");
  name.textContent = regionId;
  tooltip.appendChild(name);

  const state = document.createElement("div");
  state.textContent = label;
  state.style.opacity = "0.75";
  tooltip.appendChild(state);

  if (listing.price !== null && listing.price !== undefined) {
    const price = document.createElement("div");
    const term = listing.contractType === "leasehold" && listing.durationSeconds
      ? ` / ${formatDuration(listing.durationSeconds)}`
      : "";
    price.textContent = formatPrice(listing.price, currency) + term;
    tooltip.appendChild(price);
  }

  return tooltip;
}
