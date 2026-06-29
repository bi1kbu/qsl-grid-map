import {
  Decoration,
  DecorationSet,
  Extension,
  Plugin,
  PluginKey,
  type Editor,
  type PMNode
} from "@halo-dev/richtext-editor";

const QSL_GRID_MAP_SHORTCODE = "[qsl-grid-map]";
const SHORTCODE_PATTERN = /^\s*\[qsl-grid-map\]\s*$/i;
const shortcodePreviewPluginKey = new PluginKey("qsl-grid-map-shortcode-preview-plugin");

function insertShortcodeParagraph(editor: Editor) {
  editor
    .chain()
    .focus()
    .insertContent({
      type: "paragraph",
      content: [{ type: "text", text: QSL_GRID_MAP_SHORTCODE }]
    })
    .run();
}

function removeShortcodeParagraph(view: any, getPos: () => number | undefined) {
  const pos = getPos();
  if (typeof pos !== "number" || Number.isNaN(pos)) {
    return;
  }
  const resolved = view.state.doc.resolve(pos);
  view.dispatch(view.state.tr.delete(resolved.before(), resolved.after()));
  view.focus();
}

function createPreviewDecorations(doc: PMNode): DecorationSet {
  const decorations: Decoration[] = [];
  doc.descendants((node, pos) => {
    if (!node.isTextblock || !node.textContent || !SHORTCODE_PATTERN.test(node.textContent)) {
      return;
    }

    const contentFrom = pos + 1;
    const contentTo = pos + node.nodeSize - 1;
    if (contentTo <= contentFrom) {
      return;
    }

    decorations.push(
      Decoration.inline(contentFrom, contentTo, {
        class: "qsl-grid-map-shortcode-preview-hidden"
      })
    );
    decorations.push(
      Decoration.widget(
        contentFrom,
        (view, getPos) => {
          const card = document.createElement("div");
          card.className = "qsl-grid-map-shortcode-preview-card";
          card.setAttribute("title", "点击右侧“删除”可移除该地图短码");

          const title = document.createElement("div");
          title.className = "qsl-grid-map-shortcode-preview-card__title";
          title.textContent = "QSL 通联网格地图";
          card.appendChild(title);

          const subtitle = document.createElement("div");
          subtitle.className = "qsl-grid-map-shortcode-preview-card__subtitle";
          subtitle.textContent = "展示 QSO 通联网格覆盖情况";
          card.appendChild(subtitle);

          const removeButton = document.createElement("button");
          removeButton.type = "button";
          removeButton.className = "qsl-grid-map-shortcode-preview-card__remove";
          removeButton.textContent = "删除";
          removeButton.addEventListener("click", (event) => {
            event.preventDefault();
            event.stopPropagation();
            removeShortcodeParagraph(view, getPos);
          });
          card.appendChild(removeButton);

          return card;
        },
        {
          side: -1,
          ignoreSelection: true
        }
      )
    );
  });
  return DecorationSet.create(doc, decorations);
}

export const QslGridMapEditorExtension = Extension.create({
  name: "qslGridMapShortcodePreview",

  addProseMirrorPlugins() {
    return [
      new Plugin({
        key: shortcodePreviewPluginKey,
        state: {
          init(_, state) {
            return createPreviewDecorations(state.doc);
          },
          apply(transaction, oldDecorationSet, _, newState) {
            if (!transaction.docChanged) {
              return oldDecorationSet;
            }
            return createPreviewDecorations(newState.doc);
          }
        },
        props: {
          decorations(state) {
            return shortcodePreviewPluginKey.getState(state);
          }
        }
      })
    ];
  },

  addOptions() {
    return {
      getCommandMenuItems() {
        return {
          priority: 80,
          icon: "ri-map-pin-line",
          title: "QSL 通联网格地图",
          keywords: ["qsl", "grid", "map", "qso", "通联", "网格", "地图"],
          command: ({ editor, range }: { editor: Editor; range: { from: number; to: number } }) => {
            editor.chain().focus().deleteRange(range).run();
            insertShortcodeParagraph(editor);
          }
        };
      }
    };
  }
});
