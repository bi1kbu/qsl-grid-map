import { defineConfig } from "vite";
import { viteConfig } from "@halo-dev/ui-plugin-bundler-kit";

export default viteConfig({
  vite: defineConfig({
    build: {
      outDir: "../src/main/resources/ui"
    }
  })
});
