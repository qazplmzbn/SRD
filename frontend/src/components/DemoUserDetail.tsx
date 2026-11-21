import { useState } from 'react';
import { Alert, Button, Checkbox, Drawer, Input, Space, Tabs, Tag, Timeline, message } from 'antd';

const posts = [
  { id: 'POST-01', day: '演示第3天', text: '最近事情比较多，晚上很难放松下来。' },
  { id: 'POST-02', day: '演示第7天', text: '今天和朋友聊了一会，感觉轻松一些。' },
  { id: 'POST-03', day: '演示第12天', text: '这几天开始规律散步，不过睡前还是会想很多事。' },
];
type Note = { text: string; followup: string; created: string };
export default function DemoUserDetail({ onClose }: { onClose: () => void }) {
  const [tab, setTab] = useState('overview'), [checked, setChecked] = useState<string[]>([]);
  const [text, setText] = useState(''), [followup, setFollowup] = useState(''), [notes, setNotes] = useState<Note[]>([]);
  return <Drawer title="演示用户甲 · 资料详情" width="min(1100px, 95vw)" open onClose={onClose}>
    <Alert type="info" showIcon message="合成演示 · 非真实用户资料" description="演示授权范围：已导入贴文、测评记录。所有文本、分数和分析均为预设示例，未运行真实模型。核查和记录仅在本次打开期间保留，关闭后清空，不发送消息或提醒。" style={{ marginBottom: 20 }} />
    <Tabs activeKey={tab} onChange={setTab} items={[
      { key: 'overview', label: '变化概览', children: <Space direction="vertical" size={20} style={{ width: '100%' }}>
        <section className="pro-panel"><h3>可见资料范围</h3><p className="pro-muted">演示第3至12天，共3条贴文、2次测评。只反映已导入并授权的材料，不能代表完整生活状态。</p></section>
        <section className="pro-panel"><Space><h3>模型分析示例</h3><Tag>预设示例 · 非实时推理</Tag></Space><p>示例材料中出现睡前难以放松、朋友交流和散步等描述。后两条提到活动或交流，但仍有睡前困扰，不能据此判定情况已经改善。</p><p className="pro-muted">依据：POST-01、POST-02、POST-03。需要结合用户本人说明与专业核查。</p><Button onClick={() => setTab('posts')}>查看对应原文</Button></section>
        <Alert type="warning" message="资料缺失不代表状态稳定" description="未导入贴文的日期不生成推断；测评分数与模型文字分析不作为诊断结论。" />
      </Space> },
      { key: 'posts', label: '贴文时间线', children: <><p className="pro-muted">原始贴文示例 · 以下核查标记为医生本次演示操作，不改变贴文内容。</p><Timeline items={posts.map(post => ({ key: post.id, children: <section className="pro-panel"><Space><strong>{post.day}</strong><Tag>{post.id}</Tag></Space><p style={{ margin: '16px 0', whiteSpace: 'pre-wrap' }}>{post.text}</p><Checkbox checked={checked.includes(post.id)} onChange={e => setChecked(previous => e.target.checked ? [...previous, post.id] : previous.filter(id => id !== post.id))}>已核查此条原文（演示）</Checkbox></section> }))} /></> },
      { key: 'scales', label: '测评记录', children: <Space direction="vertical" size={20} style={{ width: '100%' }}><Alert type="info" message="此处使用虚构的“困扰程度自评”展示界面" description="0—10分的演示评分，不是正式心理量表，没有诊断阈值。正式量表接入后需按其规则解释。" /><Timeline items={[{ children: <><strong>演示第4天 · 7 / 10</strong><p>演示备注：最近睡前较难放松。</p></> }, { children: <><strong>演示第11天 · 5 / 10</strong><p>演示备注：交流和活动后有时轻松一些。</p></> }]} /><p className="pro-muted">两次合成评分相差2分。仅展示记录变化，不据此判断恢复或风险下降。</p></Space> },
      { key: 'notes', label: '医生记录与跟进', children: <Space direction="vertical" size={18} style={{ width: '100%' }}>
        <Alert type="info" message="医生记录与模型分析分开保存展示" description="这里是本次演示中的人工记录，不会修改原文、创建真实日程或发送给用户。" />
        <label htmlFor="demo-observation">观察与核查记录</label><Input.TextArea id="demo-observation" value={text} onChange={e => setText(e.target.value)} maxLength={1000} showCount rows={4} placeholder="例如：需要进一步了解睡前困扰的持续时间（请仅填写演示内容）" />
        <label htmlFor="demo-followup">后续跟进计划（选填）</label><Input id="demo-followup" value={followup} onChange={e => setFollowup(e.target.value)} maxLength={200} placeholder="例如：下次交流时核对睡眠与活动记录" />
        <Button type="primary" disabled={!text.trim()} onClick={() => { setNotes(previous => [{ text: text.trim(), followup: followup.trim(), created: new Date().toLocaleTimeString() }, ...previous]); setText(''); setFollowup(''); message.success('演示记录已添加，关闭详情后清空'); }}>添加演示记录</Button>
        {!notes.length ? <p className="pro-muted">本次演示尚未添加医生记录。</p> : notes.map((note, i) => <article className="pro-panel" key={i}><Tag>人工记录 · {note.created}</Tag><p style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere', marginTop: 16 }}>{note.text}</p>{note.followup && <p style={{ overflowWrap: 'anywhere' }}>跟进计划：{note.followup}</p>}</article>)}
      </Space> },
    ]} />
  </Drawer>;
}
