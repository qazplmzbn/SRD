import { useEffect, useState } from 'react';
import { Alert, Button, Checkbox, Descriptions, Drawer, Modal, Pagination, Progress, Select, Space, Table, Tag, message } from 'antd';
import { getAuthHeader } from '../api';
import {readPreferences} from '../professionalPreferences';
import {useAuthStore} from '../store/authStore';

const raw = import.meta.env.VITE_API_BASE;
const base = !raw || raw === 'undefined' ? '' : raw.replace(/\/api\/?$/, '').replace(/\/$/, '');
async function call(path: string, body?: unknown) {
  const r = await fetch(`${base}/api/research${path}`, { method: body === undefined ? 'GET' : 'POST', headers: { ...getAuthHeader(), 'Content-Type': 'application/json' }, body: body === undefined ? undefined : JSON.stringify(body) });
  const data = await r.json(); if (!r.ok || !data.success) throw new Error(data.message || '请求失败'); return data.data;
}
type Result = { sampleIndex: number; subjectId: string; text: string; expected: number; predicted: number | null; response: string; error: string | null; elapsedMs: number };
type Experiment = { id: string; versionName: string; datasetChecksum: string; modelName: string; modelSnapshot: string; promptVersion: string; prompt: string; status: string; total: number; completed: number; message: string; createdAt: string; results: Result[]; metrics: Record<string, number | null> };
type Version = { id: string; name: string; versionName: string; source: string; usageScope: string; checksum: string; samples: { split: string }[] };
type Model = { id: number; modelName: string; supportsTrial: boolean };
const states: Record<string, string> = { QUEUED: '排队中', RUNNING: '运行中', SUCCEEDED: '已完成', PARTIAL_FAILED: '部分失败', FAILED: '失败', CANCELLED: '已取消' };
const active = (status: string) => ['QUEUED', 'RUNNING'].includes(status);
function modelSettings(e:Experiment){const {prompt,promptVersion,...settings}=JSON.parse(e.modelSnapshot);return JSON.stringify(settings);}
function PairedChanges({a,b}:{a:Experiment;b:Experiment}){
  const previous=new Map(a.results.map(r=>[r.sampleIndex,r]));
  const pairs=b.results.filter(r=>r.predicted!=null&&previous.get(r.sampleIndex)?.predicted!=null);
  const changed=pairs.filter(r=>r.predicted!==previous.get(r.sampleIndex)!.predicted);
  return <details><summary>逐条比较：共同有效 {pairs.length}/{a.total}，改善 {changed.filter(r=>r.predicted===r.expected).length}，退步 {changed.filter(r=>r.predicted!==r.expected).length}</summary><p>基线 {a.id.slice(0,8)} → 对照 {b.id.slice(0,8)}</p>{changed.map(r=><p key={r.sampleIndex}><strong>{r.predicted===r.expected?'改善':'退步'} · 样本 {r.sampleIndex+1}：</strong>{r.text}</p>)}</details>;
}
const percent = (value: number | null | undefined) => value == null ? '无法计算' : `${(value * 100).toFixed(1)}%`;
export default function ResearchExperimentsPage() {
  const [compareIds, setCompareIds] = useState<string[]>([]), [compareOpen, setCompareOpen] = useState(false), [comparison, setComparison] = useState<Experiment[]>([]);
  const [compareError, setCompareError] = useState('');
  const [rows, setRows] = useState<Experiment[]>([]), [total, setTotal] = useState(0), [page, setPage] = useState(1), [refresh, setRefresh] = useState(0);
  const [error, setError] = useState(''), [loading, setLoading] = useState(false), [selectedId, setSelectedId] = useState<string>(), [detail, setDetail] = useState<Experiment>();
  const [filter, setFilter] = useState('errors');
  const [checkSpeaker,setSpeaker]=useState(false),[checkNegation,setNegation]=useState(false);
  useEffect(() => {
    if (!compareOpen) { setComparison([]); return; }
    let alive = true;
    const refreshComparison = async () => {
      try {
        const values: Experiment[] = await Promise.all(compareIds.map(id => call(`/experiments/${id}`)));
        if (!alive) return;
        const promptChanged=values.some(v=>v.prompt!==values[0].prompt);
        if (values.length < 2 || values.some(v => v.datasetChecksum !== values[0].datasetChecksum || v.promptVersion !== values[0].promptVersion || v.total !== values[0].total || (promptChanged&&modelSettings(v)!==modelSettings(values[0])))) {
          setComparison([]); setCompareError('请选择相同数据版本与评估协议。比较不同提示词时，模型配置也必须相同。'); return;
        }
        setComparison(values); setCompareError('');
      } catch (e) { if (alive) { setComparison([]); setCompareError(e instanceof Error ? e.message : '读取失败'); } }
    };
    void refreshComparison();
    const timer = window.setInterval(() => { if (document.visibilityState === 'visible') void refreshComparison(); }, 4000);
    return () => { alive = false; window.clearInterval(timer); };
  }, [compareOpen, compareIds]);
  const [open, setOpen] = useState(false), [versions, setVersions] = useState<Version[]>([]), [models, setModels] = useState<Model[]>([]), [version, setVersion] = useState<Version>(), [modelId, setModelId] = useState<number>();
  const [consent, setConsent] = useState(false), [saving, setSaving] = useState(false), [versionId, setVersionId] = useState<string>(), [choicesLoading, setChoicesLoading] = useState(false);
  useEffect(() => {
    let alive = true; setLoading(true);
    call(`/experiments?page=${page}`).then(data => { if (alive) { setRows(data.items); setTotal(data.total); setError(''); } }).catch(e => { if (alive) { setRows([]); setError(e.message); } }).finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, [page, refresh]);
  useEffect(() => {
    if (!selectedId) { setDetail(undefined); return; }
    let alive = true;
    call(`/experiments/${selectedId}`).then(data => { if (alive) setDetail(data); }).catch(e => { if (alive) { setDetail(undefined); setSelectedId(undefined); message.error(e.message); } });
    return () => { alive = false; };
  }, [selectedId, refresh]);
  useEffect(() => {
    if (!rows.some(row => active(row.status)) && !selectedId) return;
    const timer = window.setInterval(() => { if (document.visibilityState === 'visible') setRefresh(v => v + 1); }, 4000);
    return () => window.clearInterval(timer);
  }, [rows, selectedId]);
  useEffect(() => {
    setVersion(undefined); setConsent(false); if (!versionId) return;
    let alive = true; call(`/versions/${versionId}`).then(data => { if (alive) setVersion(data); }).catch(e => { if (alive) message.error(e.message); });
    return () => { alive = false; };
  }, [versionId]);
  const create = async () => {
    const prefs=readPreferences(useAuthStore.getState().user?.id);
    setSpeaker(prefs.speaker);setNegation(prefs.negation);
    setOpen(true); setVersionId(undefined); setVersion(undefined); setModelId(undefined); setConsent(false); setVersions([]); setModels([]); setChoicesLoading(true);
    try {
      const [first, available] = await Promise.all([call('/versions?page=1'), call('/models')]);
      const all = [...first.items];
      for (let p = 2; p <= Math.ceil(first.total / 20); p++) all.push(...(await call(`/versions?page=${p}`)).items);
      setVersions(all); setModels(available.filter((m: Model) => m.supportsTrial));
      if(available.some((m:Model)=>m.supportsTrial&&m.id===prefs.model))setModelId(prefs.model);
    } catch (e) { message.error(e instanceof Error ? e.message : '读取失败'); } finally { setChoicesLoading(false); }
  };
  const testCount = version?.samples.filter(sample => sample.split === 'TEST').length || 0;
  const resultRows = (detail?.results || []).filter(row => filter === 'all' || (filter === 'failures' ? row.predicted === null : filter === 'fp' ? row.expected === 0 && row.predicted === 1 : filter === 'fn' ? row.expected === 1 && row.predicted === 0 : row.predicted !== null && row.expected !== row.predicted));
  return <div className="pro-page">
    <div className="pro-page-heading"><div><div className="pro-eyebrow">RESEARCH EXPERIMENTS</div><h1>批量实验</h1><p className="pro-muted">固定数据版本和模型配置，评估测试集并核查误判原因。</p></div><Space><Button type="primary" onClick={create}>新建实验</Button></Space></div>
    {error && <Alert type="error" message={error} />}
    <section className="pro-panel"><Space style={{ marginBottom: 16 }}><Button disabled={compareIds.length < 2} onClick={() => { setCompareError(''); setComparison([]); setCompareOpen(true); }}>对比所选实验（{compareIds.length}/4）</Button><Button disabled={!compareIds.length} onClick={() => setCompareIds([])}>清空选择</Button><span className="pro-muted">最多选择4个实验，可跨页选择。</span></Space><Table rowKey="id" rowSelection={{ selectedRowKeys: compareIds, preserveSelectedRowKeys: true, onChange: keys => setCompareIds(keys.map(String)), getCheckboxProps: row => ({ disabled: compareIds.length >= 4 && !compareIds.includes(row.id) }) }} loading={loading} dataSource={rows} pagination={false} scroll={{ x: 950 }} columns={[
      { title: '数据版本', dataIndex: 'versionName' }, { title: '模型', dataIndex: 'modelName' }, { title: '状态', render: (_, row) => <Tag>{states[row.status] || row.status}</Tag> },
      { title: '进度', width: 170, render: (_, row) => <><Progress percent={Math.round(row.completed / row.total * 100)} showInfo={false} status={row.status === 'FAILED' ? 'exception' : undefined} />{row.completed} / {row.total}</> },
      { title: '创建时间', render: (_, row) => new Date(row.createdAt).toLocaleString() }, { title: '操作', render: (_, row) => <Button onClick={() => { setDetail(undefined); setFilter('errors'); setSelectedId(row.id); }}>查看结果</Button> },
    ]} /><Pagination current={page} total={total} pageSize={20} showSizeChanger={false} onChange={setPage} style={{ marginTop: 20 }} />
      <p className="pro-muted" style={{ marginTop: 16 }}>仅评估 TEST 样本。实验仅对创建者开放，并持续检查数据版本授权。当前支持 大模型。</p></section>
    <Modal title="实验对比" width={1100} open={compareOpen} onCancel={() => setCompareOpen(false)} footer={null}>
      {compareError ? <Alert type="error" message={compareError} /> : !comparison.length ? <p>正在读取并核对实验条件…</p> : <Space direction="vertical" size={18} style={{ width: '100%' }}>
        <Alert type={comparison.some(v => v.status !== 'SUCCEEDED' || v.metrics.coverage !== 1) ? 'warning' : 'info'} message={comparison.some(v => v.status !== 'SUCCEEDED' || v.metrics.coverage !== 1) ? '包含未完整成功的实验：各实验有效样本可能不同，请勿仅按F1判断优劣。' : '测试集内容及评估协议一致，可结合误报与漏报比较结果。'} description="运行中显示阶段指标；调用失败不作为标签0。此处不自动排名，也不代表临床诊断效果。" />
        <Table pagination={false} rowKey="key" scroll={{ x: 850 }} dataSource={[
          { key: 'version', label: '数据版本' }, { key: 'status', label: '状态' }, { key: 'time', label: '创建时间' },
          ...[['coverage', '有效预测覆盖率'], ['accuracy', '准确率'], ['precision', '精确率'], ['recall', '召回率'], ['f1', 'F1'], ['fp', '误报 FP'], ['fn', '漏报 FN'], ['failed', '失败样本'], ['pending', '未处理样本']].map(([key, label]) => ({ key, label })),
        ]} columns={[{ title: '对比项目', dataIndex: 'label', width: 160 }, ...comparison.map(v => ({ title: `${v.modelName} · ${v.id.slice(0, 8)}`, key: v.id, render: (_: unknown, item: { key: string }) => item.key === 'version' ? v.versionName : item.key === 'status' ? states[v.status] : item.key === 'time' ? new Date(v.createdAt).toLocaleString() : ['fp', 'fn', 'failed', 'pending'].includes(item.key) ? v.metrics[item.key] : percent(v.metrics[item.key]) }))]} />
        {comparison.length===2&&<PairedChanges a={comparison[0]} b={comparison[1]}/>}
      </Space>}
    </Modal>
    <Modal title="新建批量实验" width={720} open={open} onCancel={() => { if (!saving) setOpen(false); }} footer={null}>
      <Space direction="vertical" size={18} style={{ width: '100%' }}>
        <div><p>数据版本</p><Select aria-label="数据版本" style={{ width: '100%' }} loading={choicesLoading} disabled={saving} value={versionId} placeholder="选择有权使用的数据版本" showSearch optionFilterProp="label" options={versions.map(v => ({ value: v.id, label: `${v.name} / ${v.versionName}` }))} onChange={setVersionId} /></div>
        {version && <Alert type="info" message={`测试集 ${testCount} 条，训练集不参与评估`} description={<><div>来源：{version.source}</div><div>使用范围：{version.usageScope}</div></>} />}
        <div><p>模型</p><Select aria-label="模型" style={{ width: '100%' }} loading={choicesLoading} disabled={saving} value={modelId} placeholder="选择已启用的文本模型" options={models.map(m => ({ value: m.id, label: m.modelName }))} onChange={id => { setModelId(id); setConsent(false); }} /></div>
        <fieldset><legend>分类提示词</legend><p>固定任务：判断贴文是否表露作者本人的自杀想法。标签与输出格式不可修改。可组合以下补充判定步骤：</p><Checkbox disabled={saving} checked={checkSpeaker} onChange={e=>{setSpeaker(e.target.checked);setConsent(false)}}>区分自述、转述与引用</Checkbox><Checkbox disabled={saving} checked={checkNegation} onChange={e=>{setNegation(e.target.checked);setConsent(false)}}>核对否定词的对象和范围</Checkbox></fieldset>
        <Checkbox checked={consent} disabled={saving} onChange={e => setConsent(e.target.checked)}>我确认有权按上述使用范围处理测试集，并同意将测试集贴文发送给所选模型服务（可能产生调用费用）。</Checkbox>
        <Button type="primary" loading={saving} disabled={!consent || !version || !modelId || !testCount} onClick={async () => {
          setSaving(true); try { const id = await call('/experiments', { versionId: version!.id, modelId, consent,checkSpeaker,checkNegation }); setOpen(false); setPage(1); setRefresh(v => v + 1); setDetail(undefined); setSelectedId(id); message.success('实验已加入队列'); } catch (e) { message.error(e instanceof Error ? e.message : '创建失败'); } finally { setSaving(false); }
        }}>创建并运行</Button>
      </Space>
    </Modal>
    <Drawer title="实验结果" width="min(1180px, 95vw)" open={!!selectedId} onClose={() => setSelectedId(undefined)}>
      {!detail ? <p>正在读取结果…</p> : <Space direction="vertical" size={20} style={{ width: '100%' }}>
        <Space><Tag>{states[detail.status]}</Tag><span>{detail.completed} / {detail.total} 条</span>{active(detail.status) && <Button danger onClick={async () => { try { await call(`/experiments/${detail.id}/cancel`, {}); setRefresh(v => v + 1); } catch (e) { message.error(e instanceof Error ? e.message : '取消失败'); } }}>取消实验</Button>}</Space>
        {detail.message && <Alert type={detail.status === 'SUCCEEDED' ? 'success' : 'info'} message={detail.message} />}
        <Descriptions size="small" column={2} bordered items={[
          { key: 'version', label: '数据版本', children: detail.versionName }, { key: 'model', label: '模型', children: detail.modelName },
          { key: 'hash', label: '数据校验值', span: 2, children: <span style={{ overflowWrap: 'anywhere' }}>{detail.datasetChecksum}</span> },
          { key: 'protocol', label: '评估协议', children: detail.promptVersion }, { key: 'coverage', label: '有效预测覆盖率', children: percent(detail.metrics.coverage) },
          ...[['accuracy', '准确率'], ['precision', '精确率'], ['recall', '召回率'], ['f1', 'F1']].map(([key, label]) => ({ key, label, children: percent(detail.metrics[key]) })),
          { key: 'counts', label: '混淆矩阵计数', span: 2, children: `TP ${detail.metrics.tp} · TN ${detail.metrics.tn} · FP ${detail.metrics.fp} · FN ${detail.metrics.fn}` },
          { key: 'failed', label: '失败样本', children: detail.metrics.failed }, { key: 'pending', label: '未处理样本', children: detail.metrics.pending },
        ]} />
        <p className="pro-muted">指标只统计有效预测，分母为零时显示“无法计算”。运行中的数值为阶段结果；失败和未处理样本不算作标签 0。这里衡量贴文分类与研究标注的一致性，不代表临床诊断能力。</p>
        <details><summary>查看固定参数和分类提示词</summary><pre style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{detail.modelSnapshot}</pre><p>{detail.prompt}</p></details>
        <Select aria-label="样本筛选" value={filter} style={{ width: 240 }} onChange={setFilter} options={[{ value: 'errors', label: '误判样本（FP + FN）' }, { value: 'fp', label: '误报（FP）：标注0 → 预测1' }, { value: 'fn', label: '漏报（FN）：标注1 → 预测0' }, { value: 'failures', label: '调用 / 格式失败' }, { value: 'all', label: '全部已处理样本' }]} />
        <Table<Result> rowKey="sampleIndex" dataSource={resultRows} pagination={{ pageSize: 10, showSizeChanger: false }} scroll={{ x: 800 }} expandable={{ expandedRowRender: row => <div><p style={{ whiteSpace: 'pre-wrap' }}>{row.text}</p><strong>原始模型输出</strong><pre style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{row.response || '无有效输出'}</pre>{row.error && <p>{row.error}</p>}</div> }} columns={[
          { title: '研究账号标识', dataIndex: 'subjectId', width: 150 }, { title: '贴文', dataIndex: 'text', ellipsis: true }, { title: '标注', dataIndex: 'expected', width: 70 }, { title: '预测', width: 70, render: (_, row) => row.predicted ?? '失败' }, { title: '耗时', width: 100, render: (_, row) => `${row.elapsedMs}ms` }, { title: '结果', width: 140, render: (_, row) => row.predicted === null ? '调用 / 格式失败' : row.expected === row.predicted ? '正确' : row.expected === 1 ? '漏报 FN' : '误报 FP' },
        ]} />
      </Space>}
    </Drawer>
  </div>;
}
