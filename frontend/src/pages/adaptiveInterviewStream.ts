/** 从累积中的决策 JSON 增量提取 content 字段当前已生成的文本。 */
export function extractPartialContent(raw: string): string {
  const markers = [...raw.matchAll(/"content"\s*:\s*"/g)];
  const marker = markers[markers.length - 1];
  if (!marker) {
    return '';
  }
  let result = '';
  let escaping = false;
  for (let index = marker.index + marker[0].length; index < raw.length; index++) {
    const character = raw[index];
    if (escaping) {
      if (character === 'n') result += '\n';
      else if (character === 'r') result += '\r';
      else if (character === 't') result += '\t';
      else if (character === 'u' && /^[0-9a-fA-F]{4}$/.test(raw.slice(index + 1, index + 5))) {
        result += String.fromCharCode(parseInt(raw.slice(index + 1, index + 5), 16));
        index += 4;
      } else result += character;
      escaping = false;
      continue;
    }
    if (character === '\\') {
      escaping = true;
      continue;
    }
    if (character === '"') {
      break;
    }
    result += character;
  }
  return result;
}
