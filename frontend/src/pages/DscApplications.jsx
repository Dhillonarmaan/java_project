import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { ClipboardList, Plus, Clock, RefreshCw } from 'lucide-react';
import DataTable from '../components/ui/DataTable';
import StatusBadge from '../components/ui/StatusBadge';
import Button from '../components/ui/Button';
import Modal from '../components/ui/Modal';
import api from '../api/axios';
import { useAuth } from '../context/AuthContext';
import { toast } from 'sonner';

const STATUS_FILTERS = ['', 'SUBMITTED', 'VERIFICATION', 'APPROVED', 'ISSUED', 'REJECTED'];
const VALIDITY_OPTIONS = [365, 730, 1095];

const inputClass =
  'w-full px-3.5 py-2 bg-slate-900/80 border border-slate-800 rounded-xl text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:border-indigo-500/80';
const labelClass = 'block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1';

export default function DscApplications() {
  const [applications, setApplications] = useState([]);
  const [reminders, setReminders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState('');

  const [modalOpen, setModalOpen] = useState(false);
  const [form, setForm] = useState({});
  const [submitting, setSubmitting] = useState(false);

  const { user } = useAuth();
  const navigate = useNavigate();
  const isAdmin = user?.role === 'ADMIN';
  const canApply = user?.role === 'ADMIN' || user?.role === 'USER';

  const fetchData = async () => {
    setLoading(true);
    try {
      const [appRes, remRes] = await Promise.all([
        api.get('/dsc-applications'),
        api.get('/dsc-applications/reminders'),
      ]);
      setApplications(appRes.data || []);
      setReminders(remRes.data || []);
    } catch (err) {
      console.error('Failed to fetch DSC applications', err);
      toast.error('Failed to load DSC applications');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  const openApply = (renewal = null) => {
    setForm(
      renewal
        ? {
            fullName: renewal.fullName,
            email: renewal.email,
            phone: renewal.phone || '',
            organization: renewal.organization || '',
            // The form only offers whole years; anything else (e.g. set through the API) renews for one year.
            validityDays: VALIDITY_OPTIONS.includes(renewal.validityDays) ? renewal.validityDays : 365,
            renewalOfId: renewal.id,
          }
        : { fullName: user?.name || '', email: user?.email || '', phone: '', organization: '', validityDays: 365 }
    );
    setModalOpen(true);
  };

  const openRenewal = (reminder) => {
    const original = applications.find((a) => a.id === reminder.applicationId);
    if (original) openApply(original);
  };

  const setField = (key) => (e) => setForm((f) => ({ ...f, [key]: e.target.value }));

  const handleSubmit = async (e) => {
    e.preventDefault();
    setSubmitting(true);
    try {
      const res = await api.post('/dsc-applications', {
        ...form,
        validityDays: parseInt(form.validityDays, 10),
        renewalOfId: form.renewalOfId ?? null,
      });
      toast.success(`DSC application #${res.data.id} submitted`);
      setModalOpen(false);
      fetchData();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to submit application');
    } finally {
      setSubmitting(false);
    }
  };

  const visible = statusFilter ? applications.filter((a) => a.status === statusFilter) : applications;

  const columns = [
    {
      header: '#',
      field: 'id',
      render: (val) => <span className="font-mono text-xs text-slate-400">#{val}</span>,
    },
    {
      header: 'Applicant',
      field: 'fullName',
      render: (val, row) => (
        <div>
          <p className="font-semibold text-slate-100">{val}</p>
          <p className="text-xs text-slate-400 font-mono">{row.email}</p>
        </div>
      ),
    },
    {
      header: 'Organization',
      field: 'organization',
      render: (val) => <span className="text-xs text-slate-300">{val || '—'}</span>,
    },
    {
      header: 'Documents',
      field: 'documents',
      sortable: false,
      render: (docs) => {
        const received = (docs || []).filter((d) => d.status === 'RECEIVED').length;
        return (
          <span className={`text-xs font-mono ${received === docs?.length ? 'text-emerald-400' : 'text-amber-400'}`}>
            {received}/{docs?.length ?? 0} received
          </span>
        );
      },
    },
    {
      header: 'Status',
      field: 'status',
      render: (val, row) => (
        <div className="flex items-center gap-2">
          <StatusBadge status={val} size="sm" />
          {row.renewalOfId && <span className="text-[10px] text-slate-500">renewal</span>}
        </div>
      ),
    },
    {
      header: 'Submitted',
      field: 'createdAt',
      render: (val) => (val ? new Date(val).toLocaleDateString() : '—'),
    },
    {
      header: '',
      field: 'id',
      key: 'open',
      sortable: false,
      render: (id) => (
        <Button variant="secondary" size="sm" onClick={() => navigate(`/dsc/${id}`)}>
          {isAdmin ? 'Review' : 'View'}
        </Button>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl font-bold tracking-tight text-slate-100">DSC Applications</h2>
          <p className="text-sm text-slate-400 mt-1">
            {isAdmin
              ? 'Review applications, check documents, and issue digital signature certificates.'
              : 'Apply for a digital signature certificate and follow its progress.'}
          </p>
        </div>
        {canApply && (
          <Button variant="primary" size="sm" icon={Plus} onClick={() => openApply()}>
            New Application
          </Button>
        )}
      </div>

      {reminders.length > 0 && (
        <div className="glass p-4 border border-amber-500/30 space-y-2">
          <div className="flex items-center gap-2 text-amber-300 text-sm font-semibold">
            <Clock className="w-4 h-4" />
            Renewal reminders
          </div>
          {reminders.map((r) => (
            <div key={r.applicationId} className="flex flex-wrap items-center justify-between gap-2 text-xs text-slate-300">
              <span>
                DSC for <span className="font-semibold text-slate-100">{r.fullName}</span> (application #{r.applicationId},
                certificate #{r.certId}){' '}
                {r.daysLeft < 0
                  ? `expired ${-r.daysLeft} day(s) ago`
                  : `expires in ${r.daysLeft} day(s)`}{' '}
                on {new Date(r.validTo).toLocaleDateString()}.
              </span>
              {r.applicantId === user?.id && (
                <Button variant="secondary" size="sm" icon={RefreshCw} onClick={() => openRenewal(r)}>
                  Apply for renewal
                </Button>
              )}
            </div>
          ))}
        </div>
      )}

      <div className="glass p-3 flex flex-wrap items-center gap-2">
        <ClipboardList className="w-4 h-4 text-slate-400" />
        {STATUS_FILTERS.map((st) => (
          <button
            key={st || 'ALL'}
            onClick={() => setStatusFilter(st)}
            className={`px-3 py-1 rounded-lg text-xs font-medium transition-colors ${
              statusFilter === st ? 'bg-indigo-600 text-white' : 'bg-slate-900/60 hover:bg-slate-800 text-slate-400'
            }`}
          >
            {st || 'All'}
          </button>
        ))}
      </div>

      <DataTable
        columns={columns}
        data={visible}
        loading={loading}
        searchKeys={['fullName', 'email', 'organization', 'status']}
        searchPlaceholder="Search applications..."
        emptyTitle="No DSC applications"
        emptyDescription={canApply ? 'Submit a new application to get started.' : 'There are no applications yet.'}
      />

      <Modal
        isOpen={modalOpen}
        onClose={() => setModalOpen(false)}
        title={form.renewalOfId ? `Renew DSC (application #${form.renewalOfId})` : 'Apply for a Digital Signature Certificate'}
        maxWidth="max-w-lg"
      >
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className={labelClass}>Full Name (as on ID) *</label>
            <input type="text" required maxLength={100} value={form.fullName || ''} onChange={setField('fullName')} className={inputClass} />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className={labelClass}>Email *</label>
              <input type="email" required value={form.email || ''} onChange={setField('email')} className={inputClass} />
            </div>
            <div>
              <label className={labelClass}>Phone</label>
              <input type="tel" value={form.phone || ''} onChange={setField('phone')} placeholder="+91 98765 43210" className={inputClass} />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className={labelClass}>Organization</label>
              <input type="text" maxLength={100} value={form.organization || ''} onChange={setField('organization')} className={inputClass} />
            </div>
            <div>
              <label className={labelClass}>Validity</label>
              <select value={form.validityDays || 365} onChange={setField('validityDays')} className={inputClass}>
                {VALIDITY_OPTIONS.map((days) => (
                  <option key={days} value={days}>
                    {days / 365} year{days > 365 ? 's' : ''}
                  </option>
                ))}
              </select>
            </div>
          </div>
          <div className="p-3 bg-indigo-500/10 border border-indigo-500/20 rounded-xl text-xs text-indigo-300">
            After you submit, bring the application form, identity proof, address proof and a photograph for
            verification. The certificate is issued by the KeyBridge Test CA once an administrator approves it.
          </div>
          <div className="flex items-center justify-end gap-3 pt-4 border-t border-slate-800">
            <Button variant="ghost" onClick={() => setModalOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" loading={submitting}>
              Submit Application
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
