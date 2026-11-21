import { useState } from 'react';
import { Modal } from 'antd';
import { ArrowUpRight, Image, Play, BookOpen } from 'lucide-react';

type Post = { id: string; platform: string; kind: 'status' | 'note' | 'article' | 'video'; date: string; title?: string; text: string; media: number[]; tags: string[]; sourceUrl?: string };
const posts: Post[] = [
  { id: 'demo1', platform: '微博', kind: 'status', date: '演示 · 11月15日', text: '走慢一点，才发现每天经过的路也有新的颜色。今天给自己留了半小时，什么都不赶。', media: [0, 1, 2], tags: ['日常碎片'] },
  { id: 'demo2', platform: '小红书', kind: 'note', date: '演示 · 11月14日', title: '把一个普通下午留给自己', text: '带一本书，找一个靠窗的位置。没有特别的计划，只想安静地待一会儿。\n\n这些小小的片刻，也值得被记住。', media: [1], tags: ['生活记录', '周末'] },
  { id: 'demo3', platform: '知乎', kind: 'article', date: '演示 · 11月12日', title: '你有哪些愿意坚持的小习惯？', text: '我开始在每天结束时记下一件小事。不是一定要有什么收获，也不是必须开心。\n\n有时只是公交车上看到了落日，有时是给朋友回了一条消息。\n\n回看这些记录时，我会想起那一天的细节。它们不需要被评分，也不用与谁比较。', media: [], tags: ['个人回答'] },
  { id: 'demo4', platform: '抖音', kind: 'video', date: '演示 · 时间未知', title: '路上的风，和傍晚的光', text: '把散步时看到的风景留在这里。此卡片只有演示封面，没有可播放的视频文件。', media: [2], tags: ['散步记录'] },
];
function Scene({ variant }: { variant: number }) {
  const colors = [['#e4ecdf', '#e7c687', '#819a78', '#4e725f'], ['#eee4d8', '#d5ab77', '#b1ad86', '#7b8e73'], ['#e8dfeb', '#edbe96', '#a9a0b3', '#6b8089']][variant % 3];
  return <svg viewBox="0 0 400 300" role="img" aria-label="合成风景插画，不是原平台照片"><rect width="400" height="300" fill={colors[0]} /><circle cx="300" cy="70" r="35" fill={colors[1]} /><path d="M0 210 Q100 80 220 170 T400 120 V300 H0Z" fill={colors[2]} /><path d="M0 255 Q130 160 260 235 T400 220 V300 H0Z" fill={colors[3]} /><path d="M130 300 Q280 230 215 170" stroke="#f5eee0" strokeWidth="16" fill="none" /><text x="18" y="28" fill="#344b40" fontSize="12">DEMO · 合成插画</text></svg>;
}
export default function MobileFeed({ records, demo = true, grants=['微博','小红书','知乎','抖音','其他来源'] }: { records?: Post[]; demo?: boolean; grants?:string[] }) {
  const group=(p:string)=>['微博','小红书','知乎','抖音','个人记录'].includes(p.trim())?p.trim():'其他来源';
  const visiblePosts = (demo ? posts : (records || [])).filter(p=>group(p.platform)==='个人记录'||grants.includes(group(p.platform)));
  const [platform, setPlatform] = useState('全部'), [selected, setSelected] = useState<Post>(), [photo, setPhoto] = useState<number>();
  const media = (post: Post) => post.media.length > 0 && <div className={`feed-media ${post.media.length > 1 ? 'feed-grid' : ''} ${post.kind === 'video' ? 'feed-video' : ''}`}>
    {post.media.map((variant, index) => <button key={index} aria-label={post.kind === 'video' ? '查看视频封面说明' : `查看${post.platform}配图${index + 1}`} onClick={() => post.kind === 'video' ? setSelected(post) : setPhoto(variant)}><Scene variant={variant} />{post.kind === 'video' && <span className="feed-video-label"><Play size={24} />仅封面 · 无视频文件</span>}</button>)}
  </div>;
  return <>
    <div className="feed-filters daily-platforms" role="group" aria-label="切换平台">{['全部',...grants].map(value => <button key={value} aria-pressed={value === platform} onClick={() => setPlatform(value)}>{value}</button>)}</div>
    {demo && <p className="mobile-footnote">合成示例</p>}
    {visiblePosts.filter(post => platform === '全部' || platform === group(post.platform)).map(post => <article className={`feed-card feed-${post.kind}`} key={post.id}>
      <div className="feed-author"><span className="feed-avatar" aria-hidden="true">{demo?'禾':'我'}</span><div><strong>{demo ? '小禾' : '我'}</strong><time>{post.date}</time></div><span className="feed-platform">{post.platform}</span></div>
      {post.kind === 'note' && media(post)}
      <button className="feed-body" onClick={() => setSelected(post)} aria-label={`阅读全文：${post.title || post.platform + '动态'}`}>
        {post.title && <h3>{post.kind === 'article' && <BookOpen size={16} />} {post.title}</h3>}<p>{post.text}</p>
      </button>
      {post.kind !== 'note' && media(post)}
      <div className="feed-tags">{post.tags.map(tag => <span key={tag}>#{tag}</span>)}</div>
      <div className="feed-footer"><span>{post.kind === 'video' ? '视频记录' : post.kind === 'article' ? '回答与长文' : post.media.length ? <><Image size={13} /> 图文记录</> : '文字记录'}</span><button onClick={() => setSelected(post)}>查看详情 <ArrowUpRight size={14} /></button></div>
    </article>)}
    {!visiblePosts.some(p=>platform==='全部'||platform===group(p.platform)) && <section className="daily-empty"><BookOpen size={28}/><h2>{platform==='全部'?'还没有日常记录':`${platform}还没有记录`}</h2></section>}
    <Modal title={selected?.title || '动态详情'} open={!!selected} onCancel={() => { setSelected(undefined); setPhoto(undefined); }} footer={null} width={460} className="feed-detail">
      {selected && <><p className="mobile-footnote">{selected.platform} · {selected.date} · {demo ? '合成演示' : '本机导入'}</p><p style={{ whiteSpace: 'pre-wrap', lineHeight: 1.9, margin: '20px 0' }}>{selected.text}</p>{media(selected)}{selected.sourceUrl && <a href={selected.sourceUrl} target="_blank" rel="noopener noreferrer">打开微博原链接（离开本应用）</a>}<p className="mobile-footnote" style={{ marginTop: 20 }}>{selected.sourceUrl ? '仅展示收到的分享内容；未获取完整正文、配图或互动数据。' : '没有真实原帖链接或互动数据。本页不会生成点赞数、评论或推测缺失内容。'}</p></>}
    </Modal>
    <Modal title="配图预览 · 合成插画" open={photo !== undefined} onCancel={() => setPhoto(undefined)} footer={null} width={560}>{photo !== undefined && <Scene variant={photo} />}</Modal>
  </>;
}

