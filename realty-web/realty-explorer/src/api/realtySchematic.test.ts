// @vitest-environment node
import { deflateSync, gunzipSync } from "node:zlib";
import { describe, expect, it } from "vitest";
import init, { SchematicWrapper } from "nucleation";
import {
  UnreadableSchematicError,
  decodeRealtySchematic,
  isRealtySchematic,
  realtyToRenderable,
  toSpongeSchematic,
} from "./realtySchematic";

/** The fixture from the plan. The plugin's tests decode the same bytes. */
const GOLDEN =
  "UkxUWQF4nFWNQQrCMBBFJ426E72G0BMIOYkUGcOkCbaJZGbTA/ceHUWILv5iHu//ATivAGA13Tc7OM4pk68Y5IqpKjo1" +
  "wFIyKXINFXzeWdTkW0Cf8uhyqRL7iFNwjyJS5p4jvsixVExjlEEHLm3AR2L578ry1vWeaPh9/1G1vTcGbGfgYM0G" +
  "3807YQ==";

const golden = (): ArrayBuffer => {
  const bytes = Buffer.from(GOLDEN, "base64");
  return bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength) as ArrayBuffer;
};

const asBuffer = (bytes: Uint8Array): ArrayBuffer =>
  bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength) as ArrayBuffer;

/** Builds a body by hand, so a test can write one the encoder never would. */
class Body {
  private readonly bytes: number[] = [];

  int(value: number): this {
    this.bytes.push((value >>> 24) & 0xff, (value >>> 16) & 0xff, (value >>> 8) & 0xff, value & 0xff);
    return this;
  }

  text(value: string): this {
    const encoded = new TextEncoder().encode(value);
    this.bytes.push((encoded.length >> 8) & 0xff, encoded.length & 0xff, ...encoded);
    return this;
  }

  varint(value: number): this {
    let remaining = value;
    while (remaining > 0x7f) {
      this.bytes.push((remaining & 0x7f) | 0x80);
      remaining >>>= 7;
    }
    this.bytes.push(remaining);
    return this;
  }

  framed(version = 1): ArrayBuffer {
    const body = deflateSync(Uint8Array.from(this.bytes));
    return asBuffer(Uint8Array.from([0x52, 0x4c, 0x54, 0x59, version, ...body]));
  }
}

/** One air cell and one stone cell, which is the smallest grid worth decoding. */
const twoCells = (): Body =>
  new Body().int(4325).int(2).int(1).int(1)
    .int(2).text("minecraft:air").text("").text("minecraft:stone").text("");

describe("isRealtySchematic", () => {
  it("recognises the header", () => {
    expect(isRealtySchematic(golden())).toBe(true);
  });

  it("does not recognise a WorldEdit schematic", () => {
    expect(isRealtySchematic(asBuffer(Uint8Array.from([0x1f, 0x8b, 8, 0, 0, 0])))).toBe(false);
  });

  it("does not recognise a version it cannot read", () => {
    expect(isRealtySchematic(twoCells().int(1).varint(0).varint(2).framed(2))).toBe(false);
  });

  it("does not recognise an empty response", () => {
    expect(isRealtySchematic(new ArrayBuffer(0))).toBe(false);
  });
});

describe("decodeRealtySchematic", () => {
  it("decodes the golden fixture", async () => {
    const decoded = await decodeRealtySchematic(golden());

    expect(decoded.dataVersion).toBe(4325);
    expect([decoded.width, decoded.height, decoded.length]).toEqual([3, 2, 2]);
    expect(decoded.palette).toEqual([
      { state: "minecraft:air", blockEntityId: "" },
      { state: "minecraft:stone", blockEntityId: "" },
      { state: "minecraft:oak_stairs[facing=north,half=bottom,shape=straight]", blockEntityId: "" },
      { state: "minecraft:chest[facing=north,type=single]", blockEntityId: "minecraft:chest" },
    ]);
    expect(Array.from(decoded.cells)).toEqual([1, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 3]);
  });

  it("refuses a WorldEdit schematic", async () => {
    await expect(decodeRealtySchematic(asBuffer(Uint8Array.from([0x1f, 0x8b, 8, 0, 0, 0]))))
      .rejects.toBeInstanceOf(UnreadableSchematicError);
  });

  it("refuses a body that is not compressed data", async () => {
    await expect(decodeRealtySchematic(asBuffer(Uint8Array.from([0x52, 0x4c, 0x54, 0x59, 1, 9, 9, 9]))))
      .rejects.toBeInstanceOf(UnreadableSchematicError);
  });

  it("refuses a body that ends early", async () => {
    await expect(decodeRealtySchematic(new Body().int(4325).int(2).framed()))
      .rejects.toBeInstanceOf(UnreadableSchematicError);
  });

  it("refuses dimensions that would allocate without limit", async () => {
    const huge = new Body().int(4325).int(100000).int(100000).int(100000)
      .int(1).text("minecraft:air").text("").int(1).varint(0).varint(1);
    await expect(decodeRealtySchematic(huge.framed())).rejects.toBeInstanceOf(UnreadableSchematicError);
  });

  it("refuses a dimension of zero or less", async () => {
    const flat = new Body().int(4325).int(0).int(1).int(1)
      .int(1).text("minecraft:air").text("").int(0);
    await expect(decodeRealtySchematic(flat.framed())).rejects.toBeInstanceOf(UnreadableSchematicError);
  });

  it("refuses a palette size it could never fill", async () => {
    const lying = new Body().int(4325).int(2).int(1).int(1).int(2000000000);
    await expect(decodeRealtySchematic(lying.framed())).rejects.toBeInstanceOf(UnreadableSchematicError);
  });

  it("refuses a run that names a block outside the palette", async () => {
    await expect(decodeRealtySchematic(twoCells().int(1).varint(7).varint(2).framed()))
      .rejects.toBeInstanceOf(UnreadableSchematicError);
  });

  it("refuses runs that overfill the grid", async () => {
    await expect(decodeRealtySchematic(twoCells().int(1).varint(1).varint(3).framed()))
      .rejects.toBeInstanceOf(UnreadableSchematicError);
  });

  it("refuses runs that leave the grid short", async () => {
    await expect(decodeRealtySchematic(twoCells().int(1).varint(1).varint(1).framed()))
      .rejects.toBeInstanceOf(UnreadableSchematicError);
  });

  it("refuses a run of no cells, which could otherwise loop for ever", async () => {
    await expect(decodeRealtySchematic(twoCells().int(2).varint(0).varint(0).varint(1).varint(2).framed()))
      .rejects.toBeInstanceOf(UnreadableSchematicError);
  });
});

describe("toSpongeSchematic", () => {
  it("is gzipped, which the renderer's parser requires", async () => {
    const sponge = new Uint8Array(await toSpongeSchematic(await decodeRealtySchematic(golden())));
    expect([sponge[0], sponge[1]]).toEqual([0x1f, 0x8b]);
    expect(() => gunzipSync(sponge)).not.toThrow();
  });

  it("places no block at a world position", async () => {
    const sponge = gunzipSync(new Uint8Array(await toSpongeSchematic(await decodeRealtySchematic(golden()))));
    const offset = sponge.indexOf(Buffer.from("Offset"));
    expect(offset).toBeGreaterThan(0);
    // Tag name, then an int-array length of 3, then three zero ints.
    expect(Array.from(sponge.subarray(offset + 6, offset + 6 + 16)))
      .toEqual([0, 0, 0, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]);
  });

  it("refuses a capture too long on one axis to be drawn", async () => {
    const row = new Body().int(4325).int(70000).int(1).int(1)
      .int(1).text("minecraft:air").text("").int(1).varint(0).varint(70000);
    const decoded = await decodeRealtySchematic(row.framed());
    await expect(toSpongeSchematic(decoded)).rejects.toBeInstanceOf(UnreadableSchematicError);
  });

  it("is read by the renderer's own parser, block for block", async () => {
    const decoded = await decodeRealtySchematic(golden());
    await init();
    const parsed = new SchematicWrapper();
    parsed.from_schematic(new Uint8Array(await toSpongeSchematic(decoded)));

    for (let x = 0; x < decoded.width; x++) {
      for (let y = 0; y < decoded.height; y++) {
        for (let z = 0; z < decoded.length; z++) {
          const expected = decoded.palette[decoded.cells[(x * decoded.length + z) * decoded.height + y]].state;
          expect(parsed.get_block_string(x, y, z) ?? "minecraft:air", `at ${x},${y},${z}`).toBe(expected);
        }
      }
    }
    expect(parsed.get_block_entity(2, 1, 1)?.id).toBe("minecraft:chest");
  });
});

describe("realtyToRenderable", () => {
  it("goes from served bytes to something the renderer loads", async () => {
    const renderable = new Uint8Array(await realtyToRenderable(golden()));
    await init();
    const parsed = new SchematicWrapper();
    parsed.from_schematic(renderable);
    expect(parsed.get_block_count()).toBe(3);
  });
});
