import { useState } from 'react';
import { LocalPost } from './MobileRecords';

export function parseWeiboShare(raw: string): LocalPost {
  const text = raw.trim();
  if (!text || text.length > 20000) throw new Error('请提供 1～20000 字的分享内容。');
  const urls = text.match(/https?:\/\/[^\s<>"“”]+/g) || [];
  const sourceUrl = urls.map(value => value.replace(/[。，、！!？?；;）)]+$/, '')).find(value => {
    try { const url = new URL(value); if (url.username || url.password) return false; const host = url.hostname.toLowerCase(); return ['weibo.com', 'weibo.cn', 't.cn'].some(domain => host === domain || host.endsWith('.' + domain)); } catch { return false; }
  });
  return { id: crypto.randomUUID(), platform: '微博', kind: 'status', date: '时间未知', text, media: [], tags: [], sourceUrl };
}
export default function WeiboImport({ incoming, posts, onSave }: { incoming?: { id: string; text?: string; error?: string }; posts: LocalPost[]; onSave: (posts: LocalPost[]) => void }) {
  const [raw, setRaw] = useState('');
  const [draft, setDraft] = useState<LocalPost>();
  const [consent, setConsent] = useState(false);
  const [notice, setNotice] = useState('');
  const preview = (value: string) => { setConsent(false); try { setDraft(parseWeiboShare(value)); setNotice('请确认内容属于自己。分享文字可能只是摘要；时间缺失时保留“时间未知”。'); } catch (error) { setNotice((error as Error).message); setDraft(undefined); } };
  return <section className="mobile-records mobile-post"><h2>从微博整理记录</h2><p>在微博打开自己的贴文，尝试“分享 → 更多／系统分享 → 我的日常”。如果没有系统分享入口，可复制文字或链接粘贴到这里。</p><p className="mobile-footnote">只接收实际分享的文字与链接，不自动抓取全文、图片、评论或历史微博。来源由你确认；微博短链接仅保存，不自动展开。</p>
    {incoming && <div role="status">{incoming.error || '收到一份系统分享，尚未保存。'}{incoming.text && <><p style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{incoming.text.slice(0, 300)}</p><button onClick={() => preview(incoming.text!)}>预览收到的分享</button></>}</div>}
    <label>微博文字或链接<textarea value={raw} maxLength={20000} onChange={e => setRaw(e.target.value)} /></label><button disabled={!raw.trim()} onClick={() => preview(raw)}>预览微博内容</button>
    {draft && <><label>收到的内容（可纠正）<textarea value={draft.text} maxLength={20000} onChange={e => { setConsent(false); setDraft({ ...draft, text: e.target.value }); }} /></label><label>原发布时间<input value={draft.date} maxLength={200} onChange={e => setDraft({ ...draft, date: e.target.value })} /></label><p className="mobile-footnote">识别的原链接：{draft.sourceUrl || '未识别到微博域名链接，请自行核对来源'}。仅有链接时，保存的也是链接，不代表正文已导入。</p><label><input type="checkbox" checked={consent} onChange={e => setConsent(e.target.checked)} />我确认是自己的微博，允许保存在本机</label><button disabled={!consent || !draft.text.trim() || !draft.date.trim()} onClick={() => {
      try { const normalized = { ...parseWeiboShare(draft.text), date: draft.date.trim() }; const duplicate = posts.some(p => p.platform === '微博' && (normalized.sourceUrl ? p.sourceUrl === normalized.sourceUrl : p.text.trim() === normalized.text)); if (duplicate) { setNotice('这条微博已经保存，已跳过重复记录。'); return; } onSave([normalized, ...posts]); setDraft(undefined); setRaw(''); setConsent(false); setNotice('已保存，可以到“日常 → 我的记录”查看。'); } catch (error) { setNotice((error as Error).message); }
    }}>保存这条微博</button><button onClick={() => { setDraft(undefined); setConsent(false); }}>取消预览</button></>}
    <p role="status">{notice}</p>
  </section>;
}
