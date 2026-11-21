import { useEffect, useState } from 'react';
import { Alert, Button, Checkbox, Drawer, Empty, Input, Modal, Pagination, Select, Space, Spin, Tag, message } from 'antd';
import { doctorVerificationApi as api, type DoctorApplication } from '../api/doctorVerification';
import { verificationLabels } from './DoctorCertificationPage';

export default function DoctorVerificationReviewPage() {
  const [items, setItems] = useState<DoctorApplication[]>([]); const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1); const [status, setStatus] = useState('PENDING'); const [version, setVersion] = useState(0);
  const [loading, setLoading] = useState(true); const [error, setError] = useState(''); const [selected, setSelected] = useState<DoctorApplication | null>(null);
  const [decision, setDecision] = useState<string | null>(null); const [reason, setReason] = useState(''); const [confirmed, setConfirmed] = useState(false); const [saving, setSaving] = useState(false);
  useEffect(() => {
    let active = true; setLoading(true); setError(''); setItems([]);
    api.list(page, status).then(result => { if (active) { if (!result.items.length && page > 1) setPage(page - 1); else { setItems(result.items); setTotal(result.total); } } })
      .catch(e => { if (active) setError(e.message); }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [page, status, version]);
  const decide = (value: string) => { setDecision(value); setReason(''); setConfirmed(false); };
  const save = async () => {
    if (!selected || !decision || !reason.trim() || !confirmed || saving) return;
    setSaving(true);
    try { await api.review(selected.id, decision, reason.trim()); message.success('审核结果已保存'); setDecision(null); setSelected(null); setVersion(v => v + 1); }
    catch (e) { message.error(e instanceof Error ? e.message : '处理失败'); }
    finally { setSaving(false); }
  };
  return <div className="pro-page">
    <div className="pro-page-heading"><div><div className="pro-eyebrow">PLATFORM REVIEW</div><h1>医生认证审核</h1><p className="pro-muted">核查资质材料、记录处理依据，控制医生服务权限。</p></div></div>
    <section className="pro-panel"><Space style={{ marginBottom: 18 }} wrap><span>申请状态</span><Select aria-label="申请状态" value={status} style={{ width: 150 }} options={[{ value: 'ALL', label: '全部' }, ...Object.entries(verificationLabels).map(([value, label]) => ({ value, label }))]} onChange={value => { setStatus(value); setPage(1); }} /></Space>
      {loading ? <Spin /> : error ? <Alert type="error" showIcon message={error} /> : !items.length ? <Empty description="暂无此状态的认证申请" /> : <>{items.map(item => <article className="pro-review-item" key={item.id}><div><Space><strong>{item.realName}</strong><Tag>{verificationLabels[item.status]}</Tag></Space><p className="pro-muted">{item.institution} · {item.qualification}</p><p className="pro-muted">提交于 {new Date(item.submittedAt).toLocaleString()}</p></div><Button onClick={() => setSelected(item)}>查看申请</Button></article>)}<Pagination style={{ marginTop: 20 }} current={page} pageSize={20} total={total} showSizeChanger={false} onChange={setPage} /></>}
    </section>
    <Drawer title="认证申请详情" open={!!selected} width={560} onClose={() => { if (!saving) setSelected(null); }}>
      {selected && <><dl className="pro-detail-grid"><dt>申请人</dt><dd>{selected.realName}</dd><dt>机构</dt><dd>{selected.institution}</dd><dt>资质名称</dt><dd>{selected.qualification}</dd><dt>证书编号</dt><dd>{selected.credentialNumber}</dd><dt>补充说明</dt><dd>{selected.notes || '无'}</dd><dt>当前状态</dt><dd>{verificationLabels[selected.status]}</dd><dt>审核反馈</dt><dd>{selected.reviewReason || '尚未审核'}</dd></dl><Button style={{ margin: '24px 0' }} onClick={() => api.download(selected.id).catch(e => message.error(e.message))}>下载证明材料</Button><p className="pro-note">请根据实际核查结果处理。系统不会自动验证证书真伪，通过后只开通医生身份，不开放任何用户的个人资料。</p><Space style={{ marginTop: 20 }} wrap>{selected.status === 'PENDING' && <><Button type="primary" onClick={() => decide('APPROVE')}>审核通过</Button><Button danger onClick={() => decide('REJECT')}>驳回申请</Button></>}{selected.status === 'APPROVED' && <Button danger onClick={() => decide('REVOKE')}>撤销医生认证</Button>}</Space></>}
    </Drawer>
    <Modal title={decision === 'APPROVE' ? '确认审核通过' : decision === 'REJECT' ? '确认驳回申请' : '确认撤销认证'} open={!!decision} onCancel={() => { if (!saving) setDecision(null); }} onOk={save} confirmLoading={saving} okText="保存审核结果" cancelText="取消" okButtonProps={{ disabled: !reason.trim() || !confirmed, danger: decision !== 'APPROVE' }}>
      <p className="pro-muted">{decision === 'APPROVE' ? '请记录人工核查方式和结论。' : decision === 'REVOKE' ? '撤销后恢复研究者身份，后续请求不再拥有医生权限。' : '请说明需要补充或不符合要求的内容，申请人可修改后重新提交。'}</p>
      <Input.TextArea aria-label="核查结论或处理原因" value={reason} onChange={e => setReason(e.target.value)} rows={4} maxLength={1000} disabled={saving} />
      <Checkbox style={{ marginTop: 16 }} checked={confirmed} disabled={saving} onChange={e => setConfirmed(e.target.checked)}>我已核查材料并确认本次处理结果</Checkbox>
    </Modal>
  </div>;
}
