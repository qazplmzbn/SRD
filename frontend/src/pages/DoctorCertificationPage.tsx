import { useEffect, useState } from 'react';
import { Alert, Button, Checkbox, Form, Input, Spin, Tag, message } from 'antd';
import { BadgeCheck, Download, RefreshCw } from 'lucide-react';
import { doctorVerificationApi as api, type ApplicationInput, type DoctorApplication } from '../api/doctorVerification';
import { fetchMe } from '../api';
import { useAuthStore } from '../store/authStore';
import { Link } from 'react-router-dom';

export const verificationLabels = { PENDING: '待审核', APPROVED: '已通过', REJECTED: '已驳回', REVOKED: '已撤销' };
export default function DoctorCertificationPage() {
  const [items, setItems] = useState<DoctorApplication[]>([]);
  const [loading, setLoading] = useState(true); const [error, setError] = useState('');
  const [version, setVersion] = useState(0); const [saving, setSaving] = useState(false);
  const [file, setFile] = useState<File | null>(null); const [consent, setConsent] = useState(false);
  const [form] = Form.useForm<ApplicationInput>();
  const role = useAuthStore(s => s.user?.role); const updateUser = useAuthStore(s => s.updateUser);
  useEffect(() => {
    let active = true; setLoading(true); setError('');
    Promise.all([api.mine(), fetchMe()]).then(([records, user]) => { if (active) { setItems(records); updateUser(user); } })
      .catch(e => { if (active) setError(e.message); }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [version, updateUser]);
  const canSubmit = !loading && !error && role === 'researcher' && !items.some(item => item.status === 'PENDING' || item.status === 'APPROVED');
  const latest = [...items].sort((a, b) => b.submittedAt.localeCompare(a.submittedAt))[0];
  const stateTitle = role === 'doctor' ? '认证已通过，可以继续使用全部研究功能' : latest?.status === 'PENDING' ? '申请正在审核，无需重复提交' : latest?.status === 'REJECTED' ? '申请未通过，请按反馈修改后重新提交' : latest?.status === 'REVOKED' ? '医生认证已撤销，研究功能仍可使用' : latest?.status === 'APPROVED' ? '申请记录已通过，请刷新确认当前账号权限' : '尚未申请，准备好资质材料即可开始';
  const submit = async (values: ApplicationInput) => {
    if (!file || !consent || saving) return;
    setSaving(true);
    try { await api.submit({ ...values, notes: values.notes || '', consent }, file); message.success('申请已提交，等待平台审核'); form.resetFields(); setFile(null); setConsent(false); setVersion(v => v + 1); }
    catch (e) { message.error(e instanceof Error ? e.message : '提交失败'); }
    finally { setSaving(false); }
  };
  return <div className="pro-page">
    <div className="pro-page-heading"><div><div className="pro-eyebrow">PROFESSIONAL SERVICES</div><h1>医生认证</h1><p className="pro-muted">提交专业资质，审核通过后在同一工作台开通医生权限。</p></div></div>
    {!loading && !error && <section style={{ marginBottom: 24 }}><Alert showIcon type={role === 'doctor' ? 'success' : latest?.status === 'REJECTED' || latest?.status === 'REVOKED' ? 'warning' : 'info'} message={stateTitle} description={latest?.reviewReason ? `最近审核反馈：${latest.reviewReason}` : '认证只开通医生身份。接受预约和访问用户资料仍需要分别确认及授权。'} />
      {role === 'doctor' && <div className="pro-card-grid" style={{ marginTop: 16 }}><Link to="/home" className="pro-feature"><h3>返回专业工作台</h3><p>查看研究数据与风险检测任务。</p><span className="pro-feature-action">进入工作台 →</span></Link><section className="pro-panel"><h3>预约管理 <Tag>原型演示</Tag></h3><p className="pro-muted">使用合成数据体验预约处理，不建立真实预约。</p><Link to="/appointments">进入预约演示 →</Link></section><section className="pro-panel"><h3>授权用户资料 <Tag>原型演示</Tag></h3><p className="pro-muted">查看合成用户的授权范围和访问状态。</p><Link to="/authorized-users">进入授权用户演示 →</Link></section></div>}
    </section>}
    {loading ? <Spin /> : error ? <Alert type="error" showIcon message={error} action={<Button onClick={() => setVersion(v => v + 1)}>重新读取</Button>} /> : <div className="pro-split">
      <section className="pro-panel">
        {canSubmit ? <><h2 className="pro-section-title" style={{ marginTop: 0 }}>提交认证申请</h2>
          {latest && ['REJECTED', 'REVOKED'].includes(latest.status) && <div style={{ marginBottom: 20 }}><p className="pro-muted">可复用上次填写的信息，修改后请重新上传证明并确认。旧申请及审核记录会保留。</p><Button disabled={saving} onClick={() => { form.setFieldsValue({ realName: latest.realName, institution: latest.institution, qualification: latest.qualification, credentialNumber: latest.credentialNumber, notes: latest.notes }); setConsent(false); }}>复用上次填写信息</Button></div>}
          <Form layout="vertical" form={form} onFinish={submit} onValuesChange={() => setConsent(false)} disabled={saving} requiredMark="optional">
          <div className="pro-form-grid">
            <Form.Item name="realName" label="真实姓名" rules={[{ required: true, whitespace: true, message: '请填写真实姓名' }, { max: 80 }]}><Input maxLength={80} /></Form.Item>
            <Form.Item name="institution" label="执业或工作机构" rules={[{ required: true, whitespace: true, message: '请填写机构' }, { max: 200 }]}><Input maxLength={200} /></Form.Item>
            <Form.Item name="qualification" label="资质名称" rules={[{ required: true, whitespace: true, message: '请填写资质名称' }, { max: 100 }]}><Input maxLength={100} placeholder="填写证书上的完整名称" /></Form.Item>
            <Form.Item name="credentialNumber" label="证书或执业编号" rules={[{ required: true, whitespace: true, message: '请填写编号' }, { max: 120 }]}><Input maxLength={120} /></Form.Item>
          </div>
          <Form.Item name="notes" label="补充说明"><Input.TextArea rows={3} maxLength={1000} showCount placeholder="可补充执业范围或便于核查的信息" /></Form.Item>
          <label htmlFor="qualification-file">证明材料</label><input id="qualification-file" className="pro-file-input" type="file" accept=".pdf,.png,.jpg,.jpeg" disabled={saving} onChange={e => {
            const selected = e.target.files?.[0];
            if (!selected) { setFile(null); return; }
            if (selected.size > 5 * 1024 * 1024 || !/\.(pdf|png|jpe?g)$/i.test(selected.name)) { message.error('请选择不超过5MB的PDF、PNG或JPEG文件'); setFile(null); e.target.value = ''; return; }
            setFile(selected); setConsent(false);
          }} />
          <p className="pro-muted">一个文件，最多5MB。请仅提供审核所需内容，遮盖无关个人信息。材料仅供本人和平台审核人员访问。</p>
          <div style={{ margin: '18px 0' }}><Checkbox checked={consent} disabled={saving} onChange={e => setConsent(e.target.checked)}>我确认提交内容真实，同意平台保存并用于本次资质审核及后续审核追溯。</Checkbox></div>
          <Button type="primary" htmlType="submit" loading={saving} disabled={!consent || !file}>提交认证申请</Button>
        </Form></> : <p className="pro-muted">当前无需提交新申请。可在下方查看历史材料与审核反馈。</p>}
        {items.length > 0 && <><h2 className="pro-section-title">申请记录</h2>{items.map(item => <article key={item.id} className="pro-history"><div className="pro-history-head"><strong>{item.qualification}</strong><Tag color={item.status === 'APPROVED' ? 'green' : item.status === 'PENDING' ? 'gold' : 'default'}>{verificationLabels[item.status]}</Tag></div><p className="pro-muted">{item.institution} · {new Date(item.submittedAt).toLocaleString()}</p>{item.reviewReason && <p className="pro-note">审核反馈：{item.reviewReason}</p>}<Button type="link" icon={<Download size={14} />} onClick={() => api.download(item.id).catch(e => message.error(e.message))}>下载已提交材料</Button></article>)}</>}
      </section>
      <aside className="pro-panel"><span className="pro-feature-icon"><BadgeCheck size={22} /></span><h2 className="pro-section-title">认证流程</h2>{[['提交资质', '填写身份与执业信息，上传必要证明。'], ['平台人工核查', '审核人员核对材料并记录结论。驳回后可以补充重提。'], ['开通医生权限', '保留全部研究功能，获得医生业务资格。']].map(([title, text], i) => <div key={title} className="pro-step"><span className="pro-step-number">{i + 1}</span><div><h3>{title}</h3><p className="pro-muted">{text}</p></div></div>)}<div className="pro-note">此状态代表平台审核结果，不代表系统已完成官方资质联网核验。预约与授权资料查看目前仅提供合成演示，尚未接入真实服务。</div></aside>
    </div>}
  </div>;
}
