import { useEffect, useRef, useState } from 'react';
import { LocalPost } from './MobileRecords';

type Message = { role: 'user' | 'assistant'; text: string; refs?: string[] };
type Session = { id: string; messages: Message[] };
export function MobileConversation({ companion, records }: { companion: boolean; records: LocalPost[] }) {
  const [pet, setPet] = useState('🐱');
  const [nickname, setNickname] = useState('小禾');
  const [reference, setReference] = useState(false);
  const [sessions, setSessions] = useState<Session[]>([{ id: 'first', messages: [] }]);
  const [active, setActive] = useState('first');
  const [input, setInput] = useState('');
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState('');
  const timer = useRef<ReturnType<typeof setTimeout>>();
  const current = sessions.find(s => s.id === active)!;
  const stop = () => { clearTimeout(timer.current); setBusy(false); };
  useEffect(() => () => clearTimeout(timer.current), []);
  const send = (question: string) => {
    if (!question.trim() || busy) return;
    const q = question.trim(); setInput(''); setBusy(true);
    setSessions(list => list.map(s => s.id === active ? { ...s, messages: [...s.messages, { role: 'user', text: q }] } : s));
    // Explicit, local demonstration of matching; never an embedding/model claim.
    const tokens = q.match(/[\u4e00-\u9fff]{2}|[a-zA-Z]{3,}/g) || [];
    const related = companion && reference ? records.filter(p => tokens.some(t => p.text.includes(t))).slice(0, 2).map(p => p.id) : [];
    timer.current = setTimeout(() => {
      const answer = companion
        ? `${nickname}在这里听你说。${related.length ? '找到了一些包含相同词语的记录，你可以展开核对。' : '我们可以从你现在最想说的一件事开始。'}你希望先说说发生了什么，还是一起整理接下来想做的小事？\n\n这是预设陪伴回复，尚未调用大模型。`
        : '这是独立问答的交互演示。真实模型尚未接入，因此暂时不能针对你的问题生成答案。你可以体验新建会话、切换历史、停止和复制；本页面不会读取个人贴文。';
      setSessions(list => list.map(s => s.id === active ? { ...s, messages: [...s.messages, { role: 'assistant', text: answer, refs: related }] } : s)); setBusy(false);
    }, 650);
  };
  return <section className="mobile-records mobile-chat">
    <p className="mobile-footnote">离线交互演示 · 预设回复 · 会话仅在本次打开期间保留</p>
    {companion && <section className="mobile-post"><div className={`companion-avatar ${busy ? 'thinking' : ''}`} aria-label={busy ? '伙伴正在思考' : '伙伴待机'}>{pet}</div><div className="feed-filters">{['🐱', '🐰', '🐻', '🌱'].map(value => <button key={value} aria-label={`选择伙伴 ${value}`} aria-pressed={pet === value} onClick={() => setPet(value)}>{value}</button>)}</div><label>伙伴昵称<input value={nickname} maxLength={20} onChange={e => setNickname(e.target.value)} /></label><label><input type="checkbox" checked={reference} onChange={e => { stop(); setReference(e.target.checked); }} />允许本次陪伴参考我的记录</label><p className="mobile-footnote">开启后仅在本机按相同词语查找，供你体验参考记录；不是语义检索或模型训练。关闭后不再展示引用。</p></section>}
    <div className="feed-filters"><button onClick={() => { stop(); const id = crypto.randomUUID(); setSessions(s => [...s, { id, messages: [] }]); setActive(id); }}>新建对话</button><button onClick={() => { stop(); setSessions([{ id: 'first', messages: [] }]); setActive('first'); setNotice('会话已清除'); }}>清空会话</button></div>
    <label>本次会话历史<select value={active} onChange={e => { stop(); setActive(e.target.value); }}>{sessions.map((s, i) => <option key={s.id} value={s.id}>{s.messages[0]?.text.slice(0, 20) || `新对话 ${i + 1}`}</option>)}</select></label>
    {!current.messages.length && <div className="mobile-post"><h2>{companion ? '今天想聊些什么？' : '有什么想问的？'}</h2><button onClick={() => send(companion ? '最近想养成散步的习惯' : '如何安排一天的学习？')}>试试一个话题</button></div>}
    <div aria-live="polite">{current.messages.map((m, i) => <article className={`mobile-post message-${m.role}`} key={i}><small>{m.role === 'user' ? '我' : companion ? nickname : 'AI问答 · 演示'}</small><p style={{ whiteSpace: 'pre-wrap' }}>{m.text}</p>{reference && !!m.refs?.length && <details><summary>参考记录（本机词语匹配）</summary>{m.refs.map(id => { const post = records.find(p => p.id === id); return <p key={id}>{post ? `${post.platform} · ${post.date}：${post.text}` : '这条记录已删除，不再展示。'}</p>; })}</details>}{m.role === 'assistant' && <button onClick={async () => { try { await navigator.clipboard.writeText(m.text); setNotice('已复制'); } catch { setNotice('复制不可用，请长按文字复制。'); } }}>复制</button>}</article>)}</div>
    {busy && <p role="status">正在准备演示回复… <button onClick={stop}>停止</button></p>}<p role="status">{notice}</p><form onSubmit={e => { e.preventDefault(); send(input); }}><label>消息<textarea value={input} maxLength={2000} onChange={e => setInput(e.target.value)} placeholder="输入你想说的话" /></label><button disabled={busy || !input.trim()}>发送</button></form>
  </section>;
}

type DemoState = { status: 'none' | 'pending' | 'accepted' | 'rejected' | 'cancelled'; time: string; granted: boolean; expires: number; scope: string[] };
const demoKey = 'vis4srd.service-demo.v1';
const emptyDemo: DemoState = { status: 'none', time: '', granted: false, expires: 0, scope: [] };
function loadDemo(): DemoState {
  try { const value = JSON.parse(localStorage.getItem(demoKey) || 'null'); if (value && ['none', 'pending', 'accepted', 'rejected', 'cancelled'].includes(value.status) && typeof value.time === 'string' && typeof value.granted === 'boolean' && typeof value.expires === 'number' && Array.isArray(value.scope) && value.scope.every((s: unknown) => s === '合成贴文' || s === '合成测评')) return value; } catch { /* Only disposable synthetic state. */ }
  return emptyDemo;
}
export function ServiceDemo({ professional = false }: { professional?: boolean }) {
  const [state, setState] = useState(loadDemo);
  const [time, setTime] = useState('演示时段 A · 09:00');
  const [scope, setScope] = useState<string[]>([]);
  const [days, setDays] = useState(7);
  const [notice, setNotice] = useState('');
  const [now, setNow] = useState(Date.now());
  useEffect(() => { const refresh = () => setState(loadDemo()); window.addEventListener('storage', refresh); window.addEventListener('service-demo', refresh); const clock = setInterval(() => setNow(Date.now()), 1000); return () => { window.removeEventListener('storage', refresh); window.removeEventListener('service-demo', refresh); clearInterval(clock); }; }, []);
  const update = (next: DemoState) => { try { localStorage.setItem(demoKey, JSON.stringify(next)); setState(next); window.dispatchEvent(new Event('service-demo')); setNotice('演示状态已更新'); } catch { setNotice('本机存储不可用，未保存。'); } };
  const authorized = state.status === 'accepted' && state.granted && state.expires > now;
  const labels = { none: '未预约', pending: '待医生处理', accepted: '已接受', rejected: '已拒绝', cancelled: '已取消' };
  return <section className="mobile-records pro-panel"><h2>预约与资料共享 · 联动演示</h2><p className="mobile-footnote">仅使用固定合成资料，不会共享你导入的记录。本演示在同一浏览器、同一站点的页面间同步；APK 与电脑之间尚未联网同步，不建立真实预约。</p><p>演示医生：林医生（虚构） · {labels[state.status]}</p>{state.time && <p>{state.time}</p>}
    {!professional && <><label>选择演示时段<select value={time} onChange={e => setTime(e.target.value)}><option>演示时段 A · 09:00</option><option>演示时段 B · 14:00</option></select></label><button disabled={state.status === 'pending' || state.status === 'accepted'} onClick={() => update({ ...emptyDemo, status: 'pending', time })}>提交演示预约</button>{['pending', 'accepted'].includes(state.status) && <button onClick={() => update({ ...state, status: 'cancelled', granted: false })}>取消预约并停止共享</button>}
      <h3>单独授权</h3><p>预约被接受后，选择资料范围和有效期。</p>{['合成贴文', '合成测评'].map(s => <label key={s}><input type="checkbox" checked={scope.includes(s)} onChange={e => setScope(e.target.checked ? [...scope, s] : scope.filter(v => v !== s))} />{s}</label>)}<label>授权天数<select value={days} onChange={e => setDays(Number(e.target.value))}><option value={1}>1 天</option><option value={7}>7 天</option><option value={30}>30 天</option></select></label><button disabled={state.status !== 'accepted' || !scope.length} onClick={() => update({ ...state, granted: true, scope, expires: Date.now() + days * 86400000 })}>确认授权所选合成资料</button><button disabled={!state.granted} onClick={() => update({ ...state, granted: false })}>撤销资料授权</button></>}
    {professional && state.status === 'pending' && <><button onClick={() => update({ ...state, status: 'accepted' })}>接受演示预约</button><button onClick={() => update({ ...state, status: 'rejected', granted: false })}>拒绝演示预约</button></>}
    <p>资料访问：{authorized ? `有效，至 ${new Date(state.expires).toLocaleString()}` : '未授权、已到期或已停止'}</p>
    {authorized && <><p>范围：{state.scope.join('、')}</p>{state.scope.includes('合成贴文') && <p>合成贴文：今天和朋友散步，聊了一些近况。</p>}{state.scope.includes('合成测评') && <p>合成测评：演示自评 5/10，不是正式量表结果。</p>}</>}
    <p role="status">{notice}</p>
  </section>;
}

export function MobileCheckIn() {
  const [value, setValue] = useState(5);
  const [history, setHistory] = useState<{ value: number; date: string }[]>([]);
  return <section className="mobile-records mobile-post"><h2>我的近况 · 自评演示</h2><p>此处仅体验作答与历史展示，不是正式心理量表，没有诊断阈值。记录仅在本次打开期间保存。</p><label>此刻的困扰程度：{value}/10<input type="range" min={0} max={10} value={value} onChange={e => setValue(Number(e.target.value))} /></label><button onClick={() => setHistory(h => [{ value, date: new Date().toLocaleString() }, ...h])}>保存本次自评</button><button onClick={() => setHistory([])}>清空自评</button>{history.map((item, i) => <p key={i}>{item.date} · {item.value}/10</p>)}</section>;
}
