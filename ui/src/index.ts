import { Extension } from "@tiptap/core";
import { definePlugin } from "@halo-dev/ui-shared";

const QSL_GRID_MAP_SHORTCODE = "[qsl-grid-map]";

const QslGridMapShortcodeExtension = Extension.create({
  name: "qslGridMapShortcode",

  addOptions() {
    return {
      getCommandMenuItems() {
        return {
          priority: 80,
          icon: "ri-map-pin-line",
          title: "QSL 通联网格地图",
          keywords: ["qsl", "grid", "map", "qso", "通联", "网格", "地图"],
          command: ({ editor, range }: { editor: any; range: { from: number; to: number } }) => {
            editor
              .chain()
              .focus()
              .deleteRange(range)
              .insertContent(QSL_GRID_MAP_SHORTCODE)
              .run();
          }
        };
      }
    };
  }
});

export default definePlugin({
  extensionPoints: {
    "default:editor:extension:create": () => {
      return [QslGridMapShortcodeExtension];
    }
  }
});
