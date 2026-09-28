/**
 * Reads a capture in Realty's own format and rebuilds something the renderer can load.
 *
 * The plugin does not store WorldEdit schematics. It stores a hollowed copy of the
 * region, without its world position, in a layout nothing else reads. This module is
 * the other half of that layout.
 *
 * It is an obstacle, not a lock: this code ships to every visitor. What it guarantees
 * is that a saved response loads in no existing tool, and that what can be recovered
 * with effort is a shell.
 */

const MAGIC = [0x52, 0x4c, 0x54, 0x59];
const VERSION = 1;
const HEADER_LENGTH = MAGIC.length + 1;

/**
 * The most cells a capture may claim. The plugin's default cap is a million; this
 * leaves room for an operator who raised it, and stops a response that claims a
 * billion from being believed.
 */
const MAX_CELLS = 16_777_216;

/** The longest any one axis may be and still be drawn. */
const MAX_DIMENSION = 0xffff;

/**
 * The most a body may inflate to. A full-size capture of nothing but distinct blocks
 * comes to a few megabytes; this is many times that, and far short of what a few
 * kilobytes of compressed zeros can be made to claim.
 */
const MAX_BODY_BYTES = 64 * 1024 * 1024;

/**
 * The most block entities a capture may hold. A real build has chests and signs in the
 * hundreds. Each one costs the rebuilt schematic some sixty bytes, so a capture that
 * calls every cell a chest asks for a gigabyte.
 */
const MAX_BLOCK_ENTITIES = 100_000;

/**
 * The most distinct blocks a capture may name. The game has some thirty thousand block
 * states in all and a build uses a few hundred of them. Sixteen million empty names fit
 * in a body of zeros, and reading them took four seconds and a gigabyte.
 */
const MAX_PALETTE = 65_536;

/** The response is not a capture this build can read. The region page shows "no preview" for it. */
export class UnreadableSchematicError extends Error {
  constructor(reason: string) {
    super(`Unreadable schematic: ${reason}`);
    this.name = "UnreadableSchematicError";
  }
}

export type PaletteEntry = {
  /** The block and the properties that shape it, such as `minecraft:oak_stairs[facing=north]`. */
  state: string;
  /** The block entity's id, or the empty string for none. */
  blockEntityId: string;
};

export type DecodedSchematic = {
  dataVersion: number;
  width: number;
  height: number;
  length: number;
  palette: PaletteEntry[];
  /** One palette index per cell: x outermost, then z, then y innermost. */
  cells: Int32Array;
};

/** Whether the bytes open with the header of a version this build reads. Says nothing of the body. */
export function isRealtySchematic(buffer: ArrayBuffer): boolean {
  const bytes = new Uint8Array(buffer);
  return bytes.length >= HEADER_LENGTH
    && MAGIC.every((byte, index) => bytes[index] === byte)
    && bytes[MAGIC.length] === VERSION;
}

/**
 * Runs bytes through a compression or decompression stream and collects what comes out,
 * giving up as soon as more than {@code limit} bytes have.
 */
async function through(
  bytes: Uint8Array,
  stream: CompressionStream | DecompressionStream,
  limit: number,
): Promise<Uint8Array> {
  const writer = stream.writable.getWriter();
  // A failure here also fails the read below, which is where it is reported. Left
  // uncaught, the same failure would surface a second time as an unhandled rejection.
  writer.write(bytes as BufferSource).catch(() => undefined);
  writer.close().catch(() => undefined);

  const reader = stream.readable.getReader();
  const chunks: Uint8Array[] = [];
  let total = 0;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    chunks.push(value as Uint8Array);
    total += (value as Uint8Array).length;
    if (total > limit) {
      // Stopped here, not after the stream ends: what is being guarded against is
      // holding the rest of it.
      void reader.cancel().catch(() => undefined);
      throw new UnreadableSchematicError("larger than any capture");
    }
  }

  const joined = new Uint8Array(total);
  let at = 0;
  for (const chunk of chunks) {
    joined.set(chunk, at);
    at += chunk.length;
  }
  return joined;
}

class BodyReader {
  private readonly view: DataView;
  private readonly decoder = new TextDecoder();
  private at = 0;

  constructor(private readonly bytes: Uint8Array) {
    this.view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  }

  private need(count: number): void {
    if (this.at + count > this.bytes.length) throw new UnreadableSchematicError("ends early");
  }

  int(): number {
    this.need(4);
    const value = this.view.getInt32(this.at);
    this.at += 4;
    return value;
  }

  text(): string {
    this.need(2);
    const length = this.view.getUint16(this.at);
    this.at += 2;
    this.need(length);
    const value = this.decoder.decode(this.bytes.subarray(this.at, this.at + length));
    this.at += length;
    return value;
  }

  varint(): number {
    let value = 0;
    for (let shift = 0; shift <= 28; shift += 7) {
      this.need(1);
      const next = this.bytes[this.at++];
      value |= (next & 0x7f) << shift;
      if ((next & 0x80) === 0) return value >>> 0;
    }
    throw new UnreadableSchematicError("a number runs on too long");
  }

  get remaining(): number {
    return this.bytes.length - this.at;
  }
}

/**
 * Decodes a served capture.
 *
 * The bytes come from the network, and a decoder that believes what they say of
 * themselves can be made to allocate gigabytes. So the body is inflated only up to a
 * fixed size, and every count in it is held to a fixed ceiling and to what the body
 * could actually hold, before anything is allocated for it. The worst a response can
 * cost is bounded; it is not small. A full-size capture is some sixty megabytes of
 * working memory, and that is what a hostile one can ask for too.
 *
 * @throws UnreadableSchematicError for anything that is not a well-formed capture
 */
export async function decodeRealtySchematic(buffer: ArrayBuffer): Promise<DecodedSchematic> {
  if (!isRealtySchematic(buffer)) {
    throw new UnreadableSchematicError("not a Realty capture");
  }

  let body: Uint8Array;
  try {
    body = await through(
      new Uint8Array(buffer).subarray(HEADER_LENGTH),
      new DecompressionStream("deflate"),
      MAX_BODY_BYTES,
    );
  } catch (failure) {
    if (failure instanceof UnreadableSchematicError) throw failure;
    throw new UnreadableSchematicError("the body is not compressed data");
  }

  const reader = new BodyReader(body);
  const dataVersion = reader.int();
  const width = reader.int();
  const height = reader.int();
  const length = reader.int();
  if (width <= 0 || height <= 0 || length <= 0 || width * height * length > MAX_CELLS) {
    throw new UnreadableSchematicError(`impossible size ${width}x${height}x${length}`);
  }
  const cellCount = width * height * length;

  const paletteSize = reader.int();
  // Each entry is at least two length prefixes, so the bytes left bound the count. So
  // does the number of cells: the plugin names no block that no cell holds, bar air.
  if (paletteSize <= 0 || paletteSize > MAX_PALETTE
      || paletteSize > cellCount + 1 || paletteSize > reader.remaining / 4) {
    throw new UnreadableSchematicError("impossible palette size");
  }
  const palette: PaletteEntry[] = [];
  for (let index = 0; index < paletteSize; index++) {
    palette.push({ state: reader.text(), blockEntityId: reader.text() });
  }

  const runCount = reader.int();
  // Each run is at least two bytes and covers at least one cell.
  if (runCount < 0 || runCount > reader.remaining / 2 || runCount > cellCount) {
    throw new UnreadableSchematicError("impossible run count");
  }
  const cells = new Int32Array(cellCount);
  let filled = 0;
  for (let run = 0; run < runCount; run++) {
    const block = reader.varint();
    const count = reader.varint();
    if (block >= paletteSize) throw new UnreadableSchematicError("a block outside the palette");
    if (count === 0) throw new UnreadableSchematicError("a run of no cells");
    if (filled + count > cellCount) throw new UnreadableSchematicError("more cells than the grid holds");
    cells.fill(block, filled, filled + count);
    filled += count;
  }
  if (filled !== cellCount) {
    throw new UnreadableSchematicError("fewer cells than the grid holds");
  }

  return { dataVersion, width, height, length, palette, cells };
}

/** Just enough of an NBT writer to describe a schematic. */
class NbtWriter {
  private chunk = new Uint8Array(1 << 16);
  private used = 0;

  private room(count: number): void {
    if (this.used + count <= this.chunk.length) return;
    const grown = new Uint8Array(Math.max(this.chunk.length * 2, this.used + count));
    grown.set(this.chunk.subarray(0, this.used));
    this.chunk = grown;
  }

  byte(value: number): void {
    this.room(1);
    this.chunk[this.used++] = value & 0xff;
  }

  private short(value: number): void {
    this.byte(value >> 8);
    this.byte(value);
  }

  int(value: number): void {
    this.byte(value >> 24);
    this.byte(value >> 16);
    this.byte(value >> 8);
    this.byte(value);
  }

  private text(value: string): void {
    const encoded = new TextEncoder().encode(value);
    // The length is written in sixteen bits. A longer name would be written with a
    // length that is not its own, and everything after it would be read as nonsense.
    if (encoded.length > 0xffff) throw new UnreadableSchematicError("a name too long to write");
    this.short(encoded.length);
    this.room(encoded.length);
    this.chunk.set(encoded, this.used);
    this.used += encoded.length;
  }

  private tag(type: number, name: string): void {
    this.byte(type);
    this.text(name);
  }

  namedInt(name: string, value: number): void {
    this.tag(3, name);
    this.int(value);
  }

  namedShort(name: string, value: number): void {
    this.tag(2, name);
    this.short(value);
  }

  namedString(name: string, value: string): void {
    this.tag(8, name);
    this.text(value);
  }

  namedBytes(name: string, value: Uint8Array): void {
    this.tag(7, name);
    this.int(value.length);
    this.room(value.length);
    this.chunk.set(value, this.used);
    this.used += value.length;
  }

  namedInts(name: string, values: number[]): void {
    this.tag(11, name);
    this.int(values.length);
    for (const value of values) this.int(value);
  }

  /** A list of compounds. An empty list is typed as "end", as the format requires. */
  namedCompoundList(name: string, count: number): void {
    this.tag(9, name);
    this.byte(count === 0 ? 0 : 10);
    this.int(count);
  }

  open(name: string): void {
    this.tag(10, name);
  }

  close(): void {
    this.byte(0);
  }

  finish(): Uint8Array {
    return this.chunk.subarray(0, this.used);
  }
}

/**
 * Rebuilds a schematic the renderer can parse, in memory.
 *
 * The renderer takes bytes in a format it knows, so the capture is translated into one
 * for it. The result is handed straight to the renderer. It is never requested from
 * anywhere, stored, or offered as a download, and it holds only what the capture held:
 * a shell, placed at the origin.
 */
export async function toSpongeSchematic(decoded: DecodedSchematic): Promise<ArrayBuffer> {
  const { width, height, length, palette, cells } = decoded;
  // The target format stores each dimension in sixteen bits.
  if (width > MAX_DIMENSION || height > MAX_DIMENSION || length > MAX_DIMENSION) {
    throw new UnreadableSchematicError(`too long on one axis: ${width}x${height}x${length}`);
  }

  // Two entries can share a state and differ by block entity. The target format keys
  // its palette on state alone, so they fold together there.
  const spongeIndex = new Map<string, number>();
  for (const entry of palette) {
    if (!spongeIndex.has(entry.state)) spongeIndex.set(entry.state, spongeIndex.size);
  }

  const data = new NbtWriter();
  const blockEntities: { x: number; y: number; z: number; id: string }[] = [];
  // The target format runs y outermost, then z, then x: the reverse of the capture.
  for (let y = 0; y < height; y++) {
    for (let z = 0; z < length; z++) {
      for (let x = 0; x < width; x++) {
        const entry = palette[cells[(x * length + z) * height + y]];
        let index = spongeIndex.get(entry.state) as number;
        while (index > 0x7f) {
          data.byte((index & 0x7f) | 0x80);
          index >>>= 7;
        }
        data.byte(index);
        if (entry.blockEntityId !== "") {
          if (blockEntities.length === MAX_BLOCK_ENTITIES) {
            throw new UnreadableSchematicError("more block entities than any build holds");
          }
          blockEntities.push({ x, y, z, id: entry.blockEntityId });
        }
      }
    }
  }

  const nbt = new NbtWriter();
  nbt.open("");
  nbt.open("Schematic");
  nbt.namedInt("Version", 3);
  nbt.namedInt("DataVersion", decoded.dataVersion);
  nbt.namedShort("Width", width);
  nbt.namedShort("Height", height);
  nbt.namedShort("Length", length);
  nbt.namedInts("Offset", [0, 0, 0]);
  nbt.open("Blocks");
  nbt.open("Palette");
  for (const [state, index] of spongeIndex) nbt.namedInt(state, index);
  nbt.close();
  nbt.namedBytes("Data", data.finish());
  nbt.namedCompoundList("BlockEntities", blockEntities.length);
  for (const entity of blockEntities) {
    nbt.namedInts("Pos", [entity.x, entity.y, entity.z]);
    nbt.namedString("Id", entity.id);
    nbt.open("Data");
    nbt.namedString("id", entity.id);
    nbt.close();
    nbt.close();
  }
  nbt.close();
  nbt.close();
  nbt.close();

  // Gzipped because the renderer's parser refuses NBT that is not.
  const zipped = await through(nbt.finish(), new CompressionStream("gzip"), Number.MAX_SAFE_INTEGER);
  return zipped.buffer.slice(zipped.byteOffset, zipped.byteOffset + zipped.byteLength) as ArrayBuffer;
}

/**
 * From the bytes the API served to bytes the renderer loads.
 *
 * @throws UnreadableSchematicError and nothing else, so a caller has one thing to catch
 */
export async function realtyToRenderable(buffer: ArrayBuffer): Promise<ArrayBuffer> {
  try {
    return await toSpongeSchematic(await decodeRealtySchematic(buffer));
  } catch (failure) {
    if (failure instanceof UnreadableSchematicError) throw failure;
    // Running out of memory part-way, most likely. Still a capture that cannot be drawn.
    throw new UnreadableSchematicError(failure instanceof Error ? failure.message : String(failure));
  }
}
