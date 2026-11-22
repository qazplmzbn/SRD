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
  localStorage.setItem(scopedKey(storageKey), JSON.stringify(posts));
}
export function parseRecords(raw: string, json = true): LocalPost[] {
  const data: unknown = json ? JSON.parse(raw.replace(/^\uFEFF/, '')) : [{ text: raw }];
  if (!Array.isArray(data)) throw new Error('本机记录格式错误。');
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
  const [notice, setNotice] = useState('');
  return <section className="mobile-records"><h2>历史本机记录</h2>
    <p role="status">{notice}</p>
    <p>已保存 · {posts.length} 条</p>{posts.map(post => <article className="mobile-post" key={post.id}><small>{post.platform} · {post.date}</small><p style={{whiteSpace:'pre-wrap',overflowWrap:'anywhere'}}>{post.text}</p><button onClick={() => { if (!window.confirm('删除这条本机记录？此操作无法撤销。')) return; try { onSave(posts.filter(p => p.id !== post.id)); setNotice('记录已删除。'); } catch { setNotice('删除失败，请重试。'); } }}>删除记录</button></article>)}
  </section>;
}
