import { runBackgroundSync } from "./rayclip";

export default async function Command() {
  await runBackgroundSync();
}

/*
compiled entry points
info  - generated extension's TypeScript definitions
ready  - built extension successfully
*/
