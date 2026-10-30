import React, { useState, useEffect } from 'react';
import { motion } from 'framer-motion';
import {
  GitPullRequest,
  Plus,
  CheckCircle2,
  XCircle,
  Clock,
  ArrowRight,
  Shield,
  KeyRound,
  AlertTriangle,
} from 'lucide-react';
import StatusBadge from '../components/ui/StatusBadge';
import Button from '../components/ui/Button';
import Modal from '../components/ui/Modal';
import api from '../api/axios';
import { useAuth } from '../context/AuthContext';
import { toast } from 'sonner';

const COLUMNS = [
  { id: 'PENDING', label: 'Pending Review', color: 'indigo', border: 'border-indigo-500/30' },
  { id: 'APPROVED', label: 'Approved', color: 'amber', border: 'border-amber-500/30' },
  { id: 'COMPLETED', label: 'Completed', color: 'emerald', border: 'border-emerald-500/30' },
  { id: 'REJECTED', label: 'Rejected', color: 'rose', border: 'border-rose-500/30' },
];

export default function Requests() {
  const [requests, setRequests] = useState([]);
  const [loading, setLoading] = useState(true);
  const [certs, setCerts] = useState([]);
  const [tokens, setTokens] = useState([]);

  // Modal State
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [requestType, setRequestType] = useState('RENEW_CERT');
  const [targetCertId, setTargetCertId] = useState('');
  const [targetTokenId, setTargetTokenId] = useState('');
  const [comments, setComments] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const { user } = useAuth();

  const fetchRequestsAndData = async () => {
    setLoading(true);
    try {
      const [reqRes, certRes, tknRes] = await Promise.all([
        api.get('/requests'),
        api.get('/certs'),
        api.get('/tokens'),
      ]);
      setRequests(reqRes.data || []);
      setCerts(certRes.data || []);
      setTokens(tknRes.data || []);
    } catch (err) {
      console.error('Failed to fetch requests', err);
      toast.error('Failed to load service requests');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchRequestsAndData();
  }, []);

  const handleCreateSubmit = async (e) => {
    e.preventDefault();
    setSubmitting(true);
    try {
      await api.post('/requests', {
        type: requestType,
        targetCertificateId: targetCertId ? parseInt(targetCertId, 10) : null,
        targetTokenId: targetTokenId ? parseInt(targetTokenId, 10) : null,
        comments,
      });
      toast.success('Service request submitted successfully');
      setCreateModalOpen(false);
      setComments('');
      fetchRequestsAndData();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to submit service request');
    } finally {
      setSubmitting(false);
    }
  };

  const handleTransition = async (requestId, newStatus) => {
    try {
      await api.put(`/requests/${requestId}/status`, {
        status: newStatus,
        reviewerNotes: `Transitioned to ${newStatus} by ${user?.username || 'Operator'}`,
      });
      toast.success(`Request status updated to ${newStatus}`);
      fetchRequestsAndData();
    } catch (err) {
      toast.error(err.response?.data?.message || 'State transition denied');
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl font-bold tracking-tight text-slate-100">
            Service Requests & Approvals
          </h2>
          <p className="text-sm text-slate-400 mt-1">
            Formal lifecycle state machine for certificate renewal, revocation, and cryptographic token allocation.
          </p>
        </div>

        <Button
          variant="primary"
          size="sm"
          icon={Plus}
          onClick={() => setCreateModalOpen(true)}
        >
          New Service Request
        </Button>
      </div>

      {/* Kanban Board Columns */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 items-start">
        {COLUMNS.map((col) => {
          const colRequests = requests.filter((r) => r.status === col.id);

          return (
            <div
              key={col.id}
              className={`glass p-4 rounded-2xl border ${col.border} min-h-[500px] flex flex-col`}
            >
              {/* Column Header */}
              <div className="flex items-center justify-between pb-3 border-b border-slate-800 mb-4">
                <div className="flex items-center gap-2">
                  <span className="text-xs font-bold uppercase tracking-wider text-slate-200">
                    {col.label}
                  </span>
                </div>
                <span className="w-5 h-5 rounded-full bg-slate-800 text-slate-400 text-xs font-mono font-semibold flex items-center justify-center">
                  {colRequests.length}
                </span>
              </div>

              {/* Request Cards */}
              <div className="space-y-3 flex-1">
                {colRequests.length === 0 ? (
                  <div className="p-8 text-center text-xs text-slate-500">
                    No requests in this stage
                  </div>
                ) : (
                  colRequests.map((req) => (
                    <motion.div
                      key={req.id}
                      layout
                      initial={{ opacity: 0, scale: 0.95 }}
                      animate={{ opacity: 1, scale: 1 }}
                      className="bg-slate-900/80 p-4 rounded-xl border border-slate-800/80 shadow-md space-y-3 hover:border-slate-700 transition-colors"
                    >
                      <div className="flex items-start justify-between">
                        <span className="text-[10px] font-mono font-bold px-2 py-0.5 rounded bg-indigo-500/10 text-indigo-300 border border-indigo-500/20 uppercase">
                          {req.type.replace('_', ' ')}
                        </span>
                        <span className="text-[10px] text-slate-500">#{req.id}</span>
                      </div>

                      <div>
                        <p className="text-xs font-semibold text-slate-200">
                          {req.requesterFullName || req.requesterEmail}
                        </p>
                        <p className="text-xs text-slate-400 mt-1 line-clamp-2">
                          {req.comments || 'No description provided'}
                        </p>
                      </div>

                      {/* State Transition Actions for Admin */}
                      {user?.role === 'ADMIN' && (
                        <div className="pt-2 border-t border-slate-800 flex items-center justify-end gap-1.5">
                          {req.status === 'PENDING' && (
                            <>
                              <Button
                                variant="success"
                                size="sm"
                                icon={CheckCircle2}
                                onClick={() => handleTransition(req.id, 'APPROVED')}
                              >
                                Approve
                              </Button>
                              <Button
                                variant="danger"
                                size="sm"
                                icon={XCircle}
                                onClick={() => handleTransition(req.id, 'REJECTED')}
                              >
                                Reject
                              </Button>
                            </>
                          )}
                          {req.status === 'APPROVED' && (
                            <>
                              <Button
                                variant="primary"
                                size="sm"
                                icon={CheckCircle2}
                                onClick={() => handleTransition(req.id, 'COMPLETED')}
                              >
                                Complete Action
                              </Button>
                              <Button
                                variant="danger"
                                size="sm"
                                icon={XCircle}
                                onClick={() => handleTransition(req.id, 'REJECTED')}
                              >
                                Reject
                              </Button>
                            </>
                          )}
                        </div>
                      )}
                    </motion.div>
                  ))
                )}
              </div>
            </div>
          );
        })}
      </div>

      {/* NEW REQUEST MODAL */}
      <Modal
        isOpen={createModalOpen}
        onClose={() => setCreateModalOpen(false)}
        title="Submit New Service Request"
        maxWidth="max-w-md"
      >
        <form onSubmit={handleCreateSubmit} className="space-y-4">
          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
              Request Type
            </label>
            <select
              value={requestType}
              onChange={(e) => setRequestType(e.target.value)}
              className="w-full px-3.5 py-2 bg-slate-900/80 border border-slate-800 rounded-xl text-sm text-slate-100 focus:outline-none focus:border-indigo-500/80"
            >
              <option value="RENEW_CERT">Renew Existing Certificate</option>
              <option value="REVOKE_CERT">Revoke Compromised Certificate</option>
              <option value="NEW_TOKEN">Request New Hardware Token</option>
              <option value="LOST_TOKEN">Report Lost / Stolen Token</option>
            </select>
          </div>

          {(requestType === 'RENEW_CERT' || requestType === 'REVOKE_CERT') && (
            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
                Target Certificate
              </label>
              <select
                value={targetCertId}
                onChange={(e) => setTargetCertId(e.target.value)}
                className="w-full px-3.5 py-2 bg-slate-900/80 border border-slate-800 rounded-xl text-sm text-slate-100 focus:outline-none focus:border-indigo-500/80"
              >
                <option value="">-- Choose Certificate --</option>
                {certs.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.subjectDn} ({c.status})
                  </option>
                ))}
              </select>
            </div>
          )}

          {(requestType === 'NEW_TOKEN' || requestType === 'LOST_TOKEN') && (
            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
                Target Token (Optional)
              </label>
              <select
                value={targetTokenId}
                onChange={(e) => setTargetTokenId(e.target.value)}
                className="w-full px-3.5 py-2 bg-slate-900/80 border border-slate-800 rounded-xl text-sm text-slate-100 focus:outline-none focus:border-indigo-500/80"
              >
                <option value="">-- Choose Token --</option>
                {tokens.map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.serialNo} ({t.label}) - {t.status}
                  </option>
                ))}
              </select>
            </div>
          )}

          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
              Comments / Justification
            </label>
            <textarea
              rows={3}
              required
              value={comments}
              onChange={(e) => setComments(e.target.value)}
              placeholder="State the technical reason or business justification..."
              className="w-full px-3.5 py-2 bg-slate-900/80 border border-slate-800 rounded-xl text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:border-indigo-500/80 resize-none"
            />
          </div>

          <div className="flex items-center justify-end gap-3 pt-4 border-t border-slate-800">
            <Button variant="ghost" onClick={() => setCreateModalOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" loading={submitting}>
              Submit Request
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
