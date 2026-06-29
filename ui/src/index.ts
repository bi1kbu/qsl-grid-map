import { definePlugin } from "@halo-dev/ui-shared";
import { QslGridMapEditorExtension } from "./qsl-grid-map-editor-extension";
import "./styles.css";

export default definePlugin({
  extensionPoints: {
    "default:editor:extension:create": () => {
      return [QslGridMapEditorExtension];
    }
  }
});
