import { useState } from 'react';
import {scopedKey} from './mobileSession';

export type LocalPost = { id: string; platform: string; kind: 'status' | 'note' | 'article' | 'video'; date: string; title?: string; text: string; media: number[]; tags: string[]; sourceUrl?: string };
const storageKey = 'vis4srd.local-records.v1';
export function readRecords(): LocalPost[] {
  const raw = localStorage.getItem(scopedKey(storageKey));
  if (!raw) return [];
  const data = JSON.parse(raw);
  return parseRecords(raw).map((post, i) => ({ ...post, id: typeof data[i].id === 'string' ? data[i].id : `saved-${i}` }));
}
export function saveRecords(posts: LocalPost[]) {
  if (posts.length > 500) throw new Error('本机原型最多保存 500 条，请先删除部分记录。');
  localStorage.setItem(scopedKey(storageKey), JSON.stringify(posts));
}
export function parseRecords(raw: string, json = true): LocalPost[] {
  const data: unknown = json ? JSON.parse(raw.replace(/^\uFEFF/, '')) : [{ text: raw }];
  if (!Array.isArray(data) || data.length > 500) throw new Error('JSON 必须是贴文数组，每次最多 500 条。');
  return data.map((value, index) => {
    if (!value || typeof value !== 'object') throw new Error(`第 ${index + 1} 条格式不正确。`);
    const item = value as Record<string, unknown>;
    if (typeof item.text !== 'string' || !item.text.trim() || item.text.length > 20000) throw new Error(`第 ${index + 1} 条需要 1～20000 字正文 text。`);
    for (const key of ['platform', 'date', 'title']) if (item[key] !== undefined && (typeof item[key] !== 'string' || (item[key] as string).length > 200)) throw new Error(`第 ${index + 1} 条 ${key} 格式错误。`);
    if (item.kind !== undefined && !['status', 'note', 'article', 'video'].includes(String(item.kind))) throw new Error(`第 ${index + 1} 条 kind 不受支持。`);
    let sourceUrl: string | undefined;
    if (item.sourceUrl !== undefined) {
      if (typeof item.sourceUrl !== 'string' || item.sourceUrl.length > 4096) throw new Error('原链接格式错误');
      const url = new URL(item.sourceUrl);
      if (!['https:', 'http:'].includes(url.protocol) || url.username || url.password) throw new Error('原链接必须是 HTTP(S) 链接');
      sourceUrl = item.sourceUrl;
    }
    return { sourceUrl, id: `preview-${index}`, text: item.text.trim(), platform: String(item.platform || '个人记录'), date: String(item.date || '时间未知'), title: item.title as string | undefined, kind: (item.kind || 'status') as LocalPost['kind'], media: [], tags: [] };
  });
}
export default function MobileRecords({ posts, onSave }: { posts: LocalPost[]; onSave: (posts: LocalPost[]) => void }) {
  const [draft, setDraft] = useState<LocalPost[]>([]);
  const [text, setText] = useState('');
  const [notice, setNotice] = useState('');
  const [consent, setConsent] = useState(false);
  const [busy, setBusy] = useState(false);
  const preview = (raw: string, json: boolean) => { setConsent(false); setDraft([]); try { setDraft(parseRecords(raw, json)); setNotice('请核对正文、来源和时间，可取消不想保存的记录。'); } catch (error) { setNotice((error as Error).message); } };
  const commit = () => {
    const signature = (p: LocalPost) => JSON.stringify([p.platform.trim(), p.date.trim(), p.text.trim()]);
    const known = new Set(posts.map(signature));
    const added = draft.filter(p => { const key = signature(p); if (known.has(key)) return false; known.add(key); return true; });
    if (posts.length + added.length > 500) { setNotice('本机原型最多保存 500 条，请先删除部分记录。'); return; }
    try { onSave([...added.map(p => ({ ...p, id: crypto.randomUUID() })), ...posts]); setNotice(`已保存 ${added.length} 条，跳过 ${draft.length - added.length} 条重复记录。`); setDraft([]); setText(''); setConsent(false); } catch (e) { setNotice(e instanceof Error?e.message:'保存失败，预览仍保留。'); }
  };
  return <section className="mobile-records"><h2>导入贴文</h2><p className="mobile-footnote">记录按当前账号或访客保存在本机。已开启持续共享的预约会同步更新；清除应用数据会删除本机记录。</p>
    <label>选择 TXT 或约定格式 JSON<input type="file" accept=".txt,.json,text/plain,application/json" disabled={busy} onChange={async event => {
      const file = event.target.files?.[0]; event.target.value = ''; if (!file) return;
      setDraft([]); setConsent(false); if (file.size > 2 * 1024 * 1024) { setNotice('请选择 2 MB 以内的文件。'); return; }
      if (!/\.(txt|json)$/i.test(file.name)) { setNotice('仅支持 TXT 和 JSON。'); return; }
      setBusy(true); try { preview(await file.text(), /\.json$/i.test(file.name)); } catch { setNotice('文件读取失败，请重新选择。'); } finally { setBusy(false); }
    }} /></label>
    <p className="mobile-footnote">TXT 整个文件作为一条记录。JSON 示例：{'[{"text":"今天散步了","platform":"微博","date":"2026-11-18"}]'}。尚未适配平台导出包；图片和视频附件不会导入。</p>
    <label>或粘贴一条记录<textarea value={text} maxLength={20000} onChange={e => setText(e.target.value)} placeholder="写下或粘贴正文" /></label><button disabled={!text.trim() || busy} onClick={() => preview(text, false)}>预览文字</button>
    <p role="status">{notice}</p>
    {draft.map((post, index) => <article className="mobile-post" key={post.id}><label>来源<input maxLength={200} value={post.platform} onChange={e => setDraft(draft.map((p, i) => i === index ? { ...p, platform: e.target.value } : p))} /></label><label>原发布时间<input maxLength={200} value={post.date} onChange={e => setDraft(draft.map((p, i) => i === index ? { ...p, date: e.target.value } : p))} /></label><label>正文<textarea maxLength={20000} value={post.text} onChange={e => setDraft(draft.map((p, i) => i === index ? { ...p, text: e.target.value } : p))} /></label><button onClick={() => setDraft(draft.filter((_, i) => i !== index))}>取消导入这条</button></article>)}
    {draft.length > 0 && <><label><input type="checkbox" checked={consent} onChange={e => setConsent(e.target.checked)} />我确认这些是我有权保存的内容，并同意保存在本机。</label><button disabled={!consent || draft.some(p => !p.text.trim() || !p.platform.trim() || !p.date.trim())} onClick={commit}>确认保存 {draft.length} 条</button><button onClick={() => setDraft([])}>取消本次导入</button></>}
    <h2>已保存 · {posts.length} 条</h2>{posts.map(post => <article className="mobile-post" key={post.id}><small>{post.platform} · {post.date}</small><p>{post.text.slice(0, 120)}</p><button onClick={() => { if (!window.confirm('删除这条本机记录？此操作无法撤销。')) return; try { onSave(posts.filter(p => p.id !== post.id)); setNotice('记录已删除。'); } catch { setNotice('删除失败，请重试。'); } }}>删除记录</button></article>)}
  </section>;
}
