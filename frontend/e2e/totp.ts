import { createHmac } from 'node:crypto';

const BASE32_ALPHABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
const PERIOD_SECONDS = 30;
const DIGITS = 6;

function decodeBase32(value: string): Buffer {
  let bits = 0;
  let accumulator = 0;
  const bytes: number[] = [];
  for (const character of value.replace(/[\s=]/g, '').toUpperCase()) {
    const index = BASE32_ALPHABET.indexOf(character);
    if (index < 0) throw new Error(`Not a Base32 character: ${character}`);
    accumulator = (accumulator << 5) | index;
    bits += 5;
    if (bits >= 8) {
      bits -= 8;
      bytes.push((accumulator >> bits) & 0xff);
    }
  }
  return Buffer.from(bytes);
}

/** RFC 6238, SHA-1, matching the parameters the server issues. */
function generate(secret: string, step: number): string {
  const counter = Buffer.alloc(8);
  counter.writeBigUInt64BE(BigInt(step));
  const digest = createHmac('sha1', decodeBase32(secret)).update(counter).digest();
  const offset = digest[digest.length - 1] & 0x0f;
  const truncated = digest.readUInt32BE(offset) & 0x7fffffff;
  return String(truncated % 10 ** DIGITS).padStart(DIGITS, '0');
}

function currentStep(): number {
  return Math.floor(Date.now() / 1000 / PERIOD_SECONDS);
}

/**
 * The server refuses any step it has already accepted, so each code has to come from a strictly
 * later window than the previous one. Codes close to a boundary are avoided as well: the browser
 * still has to type and submit them.
 */
export async function nextCode(
  secret: string,
  afterStep = -1,
): Promise<{ code: string; step: number }> {
  for (;;) {
    const step = currentStep();
    const secondsIntoStep = Math.floor(Date.now() / 1000) % PERIOD_SECONDS;
    if (step > afterStep && secondsIntoStep < PERIOD_SECONDS - 5) {
      return { code: generate(secret, step), step };
    }
    await new Promise((resolve) => setTimeout(resolve, 1000));
  }
}
