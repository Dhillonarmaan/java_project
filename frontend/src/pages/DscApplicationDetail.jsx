import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ArrowLeft, CheckCircle2, XCircle, Circle, FileCheck2, ShieldCheck } from 'lucide-react';
import StatusBadge from '../components/ui/StatusBadge';
import Button from '../components/ui/Button';
import Modal from '../components/ui/Modal';
import api from '../api/axios';
import { useAuth } from '../context/AuthContext';
import { toast } from 'sonner';

const TIMELINE = ['SUBMITTED', 'VERIFICATION', 'APPROVED', 'ISSUED'];

// What an admin can do next from each status (REJECTED is offered separately).
const NEXT_STEP = {
  SUBMITTED: { status: 'VERIFICATION', label: 'Start Verification' },
  VERIFICATION: { status: 'APPROVED', label: 'Approve' },
  APPROVED: { status: 'ISSUED', label: 'Issue Certificate' },
};

export default function DscApplicationDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';

  const [app, setApp] = useState(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [rejectOpen, setRejectOpen] = useState(false);
  const [rejectReason, setRejectReason] = useState('');

  const fetchApp = async () => {
    try {
      const res = await api.get(`/dsc-applications/${id}`);
      setApp(res.data);
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to load application');
      navigate('/dsc');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchApp();
  }, [id]);

  const setDocument = async (document, status) => {
    setBusy(true);
    try {
      const res = await api.put(`/dsc-applications/${id}/documents`, { document, status });
      setApp(res.data);
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to update document');
    } finally {
      setBusy(false);
    }
  };

  const transition = async (status, note = null) => {
    setBusy(true);
    try {
      const res = await api.put(`/dsc-applications/${id}/status`, { status, note });
      setApp(res.data);
      toast.success(`Application moved to ${status}`);
      return true;
    } catch (err) {
      toast.error(err.response?.data?.message || 'State transition denied');
      return false;
    } finally {
      setBusy(false);
    }
  };

  const handleReject = async (e) => {
    e.preventDefault();
    if (await transition('REJECTED', rejectReason)) {
      setRejectOpen(false);
      setRejectReason('');
    }
  };

  if (loading || !app) {
    return <div className="text-sm text-slate-400">Loading application...</div>;
  }

  const eventFor = (status) => app.events.find((e) => e.status === status);
  const rejected = app.status === 'REJECTED';
  const docsEditable = isAdmin && (app.status === 'SUBMITTED' || app.status === 'VERIFICATION');
  const allReceived = app.documents.every((d) => d.status === 'RECEIVED');
  const next = isAdmin ? NEXT_STEP[app.status] : null;

  return (
    <div className="space-y-6">
      <button onClick={() => navigate('/dsc')} className="flex items-center gap-1.5 text-xs text-slate-400 hover:text-slate-200">
        <ArrowLeft className="w-3.5 h-3.5" /> All applications
      </button>

      <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-4">
        <div>
          <div className="flex items-center gap-3">
            <h2 className="text-2xl font-bold tracking-tight text-slate-100">DSC Application #{app.id}</h2>
            <StatusBadge status={app.status} />
          </div>
          <p className="text-sm text-slate-400 mt-1">
            {app.renewalOfId ? `Renewal of application #${app.renewalOfId}. ` : ''}
            Submitted by {app.applicantName} on {new Date(app.createdAt).toLocaleString()}
          </p>
        </div>

        {isAdmin && !rejected && app.status !== 'ISSUED' && (
          <div className="flex items-center gap-2">
            {next && (
              <Button
                variant={next.status === 'ISSUED' ? 'primary' : 'success'}
                size="sm"
                icon={next.status === 'ISSUED' ? ShieldCheck : CheckCircle2}
                loading={busy}
                disabled={next.status === 'APPROVED' && !allReceived}
                onClick={() => transition(next.status)}
              >
                {next.label}
              </Button>
            )}
            <Button variant="danger" size="sm" icon={XCircle} onClick={() => setRejectOpen(true)}>
              Reject
            </Button>
          </div>
        )}
      </div>

      {/* Status timeline */}
      <div className="glass p-5">
        <h3 className="text-xs font-bold uppercase tracking-wider text-slate-400 mb-4">Status Timeline</h3>
        <ol className="space-y-4">
          {TIMELINE.map((status) => {
            const ev = eventFor(status);
            return (
              <li key={status} className="flex items-start gap-3">
                {ev ? (
                  <CheckCircle2 className="w-5 h-5 text-emerald-400 shrink-0" />
                ) : (
                  <Circle className="w-5 h-5 text-slate-600 shrink-0" />
                )}
                <div className="text-xs">
                  <p className={`font-semibold ${ev ? 'text-slate-100' : 'text-slate-500'}`}>{status}</p>
                  {ev && (
                    <p className="text-slate-400">
                      {new Date(ev.at).toLocaleString()} by {ev.actorName}
                      {ev.note ? `: ${ev.note}` : ''}
                    </p>
                  )}
                </div>
              </li>
            );
          })}
          {rejected && (
            <li className="flex items-start gap-3">
              <XCircle className="w-5 h-5 text-rose-400 shrink-0" />
              <div className="text-xs">
                <p className="font-semibold text-rose-300">REJECTED</p>
                <p className="text-slate-400">
                  {new Date(eventFor('REJECTED').at).toLocaleString()} by {eventFor('REJECTED').actorName}:{' '}
                  {eventFor('REJECTED').note}
                </p>
              </div>
            </li>
          )}
        </ol>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Applicant */}
        <div className="glass p-5 space-y-3 text-xs">
          <h3 className="font-bold uppercase tracking-wider text-slate-400">Applicant</h3>
          {[
            ['Full name', app.fullName],
            ['Email', app.email],
            ['Phone', app.phone],
            ['Organization', app.organization],
            ['Validity', `${app.validityDays} days`],
          ].map(([label, value]) => (
            <div key={label} className="flex justify-between gap-4 border-b border-slate-800/60 pb-2">
              <span className="text-slate-400">{label}</span>
              <span className="text-slate-100 text-right">{value || '—'}</span>
            </div>
          ))}
          {app.certId && (
            <div className="p-3 rounded-xl bg-emerald-500/10 border border-emerald-500/20 text-emerald-300 space-y-1">
              <p className="font-semibold">Certificate #{app.certId} issued</p>
              <p className="font-mono">SN: {app.certSerialHex}</p>
              <p>
                Valid to {new Date(app.certValidTo).toLocaleDateString()} <StatusBadge status={app.certStatus} size="sm" />
              </p>
            </div>
          )}
        </div>

        {/* Document checklist */}
        <div className="glass p-5 space-y-3 text-xs">
          <h3 className="font-bold uppercase tracking-wider text-slate-400 flex items-center gap-2">
            <FileCheck2 className="w-4 h-4" /> Document Checklist
          </h3>
          {app.documents.map((d) => (
            <div key={d.document} className="flex items-center justify-between gap-3 border-b border-slate-800/60 pb-2">
              <span className="text-slate-200">{d.document}</span>
              {docsEditable ? (
                <div className="flex items-center gap-1">
                  {['RECEIVED', 'MISSING'].map((st) => (
                    <button
                      key={st}
                      disabled={busy}
                      onClick={() => d.status !== st && setDocument(d.document, st)}
                      className={`px-2.5 py-1 rounded-lg text-[10px] font-semibold transition-colors ${
                        d.status === st
                          ? st === 'RECEIVED'
                            ? 'bg-emerald-600 text-white'
                            : 'bg-rose-600 text-white'
                          : 'bg-slate-900/60 text-slate-400 hover:bg-slate-800'
                      }`}
                    >
                      {st === 'RECEIVED' ? 'Received' : 'Missing'}
                    </button>
                  ))}
                </div>
              ) : (
                <StatusBadge status={d.status} size="sm" />
              )}
            </div>
          ))}
          {isAdmin && app.status === 'VERIFICATION' && !allReceived && (
            <p className="text-amber-300">Mark every document received before approving.</p>
          )}
        </div>
      </div>

      <Modal isOpen={rejectOpen} onClose={() => setRejectOpen(false)} title="Reject Application" maxWidth="max-w-md">
        <form onSubmit={handleReject} className="space-y-4">
          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">Reason *</label>
            <textarea
              rows={3}
              required
              maxLength={500}
              value={rejectReason}
              onChange={(e) => setRejectReason(e.target.value)}
              placeholder="Shown to the applicant on the timeline"
              className="w-full px-3.5 py-2 bg-slate-900/80 border border-slate-800 rounded-xl text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:border-rose-500/80 resize-none"
            />
          </div>
          <div className="flex items-center justify-end gap-3 pt-4 border-t border-slate-800">
            <Button variant="ghost" onClick={() => setRejectOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" variant="danger" loading={busy}>
              Reject
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
