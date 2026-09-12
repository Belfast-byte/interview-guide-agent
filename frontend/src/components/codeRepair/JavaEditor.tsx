import { useEffect, useRef } from 'react';
import { basicSetup } from 'codemirror';
import { EditorState } from '@codemirror/state';
import { EditorView } from '@codemirror/view';
import { java } from '@codemirror/lang-java';
import { MergeView } from '@codemirror/merge';

const theme = EditorView.theme({
  '&': { background: 'var(--raised)', color: 'var(--ink)', fontSize: '13px' },
  '.cm-scroller': { fontFamily: '"IBM Plex Mono", monospace', overflow: 'auto', maxHeight: '65vh' },
  '.cm-content': { minHeight: '260px' },
  '.cm-gutters': { background: 'var(--paper)', color: 'var(--wk-muted)', borderColor: 'var(--line)' },
  '&.cm-focused': { outline: '2px solid var(--cinnabar)', outlineOffset: '2px' },
});
const extensions = (readOnly: boolean) => [basicSetup, java(), theme,
  EditorState.readOnly.of(readOnly), EditorView.editable.of(!readOnly)];

export default function JavaEditor(props: {
  code: string; readOnly?: boolean; onChange?: (code: string) => void;
}) {
  const host = useRef<HTMLDivElement>(null);
  const view = useRef<EditorView>();
  const onChange = useRef(props.onChange);
  onChange.current = props.onChange;
  useEffect(() => {
    const editor = new EditorView({ parent: host.current!, doc: props.code,
      extensions: [...extensions(Boolean(props.readOnly)),
        EditorView.contentAttributes.of({ 'aria-label': props.readOnly ? 'Java 代码（只读）' : 'Java 代码' }),
        EditorView.updateListener.of(update => {
          if (update.docChanged) onChange.current?.(update.state.doc.toString());
        })],
    });
    view.current = editor;
    return () => { editor.destroy(); view.current = undefined; };
  }, [props.readOnly]);
  useEffect(() => {
    const editor = view.current;
    if (editor && editor.state.doc.toString() !== props.code) {
      editor.dispatch({ changes: { from: 0, to: editor.state.doc.length, insert: props.code } });
    }
  }, [props.code]);
  return <div ref={host} className="min-w-0 overflow-hidden border border-line" />;
}

export function JavaDiff({ original, code, currentLabel }: { original: string; code: string; currentLabel: string }) {
  const host = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const merge = new MergeView({ parent: host.current!,
      a: { doc: original, extensions: extensions(true) },
      b: { doc: code, extensions: extensions(true) },
      highlightChanges: true, gutter: true,
    });
    return () => merge.destroy();
  }, [original, code]);
  return <div><p className="mb-2 text-xs text-wk-muted">左：原始代码 · 右：{currentLabel}</p>
    <div ref={host} aria-label={`原始代码与${currentLabel}的差异`} className="min-w-0 overflow-x-auto border border-line" /></div>;
}
