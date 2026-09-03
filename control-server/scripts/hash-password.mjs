import { createPasswordHash } from '../src/security.mjs';

async function readStdin() {
  const chunks = [];
  for await (const chunk of process.stdin) chunks.push(chunk);
  return Buffer.concat(chunks).toString('utf8').replace(/\r?\n$/, '');
}

const password = process.argv[2] === '--stdin'
  ? await readStdin()
  : process.argv[2];
if (!password) {
  console.error('Usage: npm run hash-password -- "a non-empty password"');
  console.error('   or: printf %s "$PASSWORD" | npm run hash-password -- --stdin');
  process.exit(1);
}

console.log(createPasswordHash(password));
