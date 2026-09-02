import { createControlServer } from './server.mjs';

const app = createControlServer();

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, async () => {
    await app.close();
    process.exit(0);
  });
}

const address = await app.listen();
console.log(`AIOtv Control listening on ${app.config.host}:${address.port}`);
