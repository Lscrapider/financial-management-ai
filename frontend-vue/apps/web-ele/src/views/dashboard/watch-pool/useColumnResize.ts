import { onBeforeUnmount, reactive } from 'vue';

export function useColumnResize() {
  const widths = reactive<Record<string, number>>({});
  let state: { key: string; startX: number; startWidth: number } | null = null;

  function onMouseDown(key: string, event: MouseEvent) {
    const th = (event.target as HTMLElement).closest('th');
    if (!th) return;
    const startWidth = th.offsetWidth;
    widths[key] = startWidth;
    state = { key, startX: event.clientX, startWidth };
    document.body.style.userSelect = 'none';
    document.body.style.cursor = 'col-resize';
    document.addEventListener('mousemove', onMouseMove);
    document.addEventListener('mouseup', onMouseUp);
    event.preventDefault();
    event.stopPropagation();
  }

  function onMouseMove(event: MouseEvent) {
    if (!state) return;
    const diff = event.clientX - state.startX;
    widths[state.key] = Math.max(50, state.startWidth + diff);
  }

  function onMouseUp() {
    state = null;
    document.body.style.userSelect = '';
    document.body.style.cursor = '';
    document.removeEventListener('mousemove', onMouseMove);
    document.removeEventListener('mouseup', onMouseUp);
  }

  function cleanup() {
    document.body.style.userSelect = '';
    document.body.style.cursor = '';
    document.removeEventListener('mousemove', onMouseMove);
    document.removeEventListener('mouseup', onMouseUp);
  }

  onBeforeUnmount(cleanup);

  return { onMouseDown, widths };
}
