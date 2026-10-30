import React, { useState, useEffect } from 'react';
import { motion } from 'framer-motion';
import { KeyRound, Plus, UserCheck, ShieldAlert, Check, RefreshCw, Smartphone } from 'lucide-react';
import StatusBadge from '../components/ui/StatusBadge';
import Button from '../components/ui/Button';
import Modal from '../components/ui/Modal';
import api from '../api/axios';
import { useAuth } from '../context/AuthContext';
import { toast } from 'sonner';

export default function Tokens() {
  const [tokens, setTokens] = useState([]);
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);

  // Modals
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [assignModalOpen, setAssignModalOpen] = useState(false);
  const [selectedToken, setSelectedToken] = useState(null);

  // Create Form State
  const [serialNumber, setSerialNumber] = useState('');
  const [model, setModel] = useState('YubiKey 5 NFC');
  const [createSubmitting, setCreateSubmitting] = useState(false);

  // Assign Form State
  const [assignedUserId, setAssignedUserId] = useState('');
  const [assignSubmitting, setAssignSubmitting] = useState(false);

  const { user } = useAuth();

  const fetchTokensAndUsers = async () => {
    setLoading(true);
    try {
      const [tknRes, usrRes] = await Promise.allSettled([
        api.get('/tokens'),
        user?.role === 'ADMIN' ? api.get('/users') : Promise.resolve({ data: [] }),
      ]);
      if (tknRes.status === 'fulfilled') setTokens(tknRes.value.data || []);
      if (usrRes.status === 'fulfilled') setUsers(usrRes.value.data || []);
    } catch (err) {
      console.error('Failed to fetch tokens', err);
      toast.error('Failed to load token inventory');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchTokensAndUsers();
  }, []);

  const handleCreateSubmit = async (e) => {
    e.preventDefault();
    if (!serialNumber.trim()) {
      toast.error('Token serial number is required');
      return;
    }
    setCreateSubmitting(true);
    try {
      await api.post('/tokens', { serialNo: serialNumber, label: model });
      toast.success(`Token ${serialNumber} enrolled successfully!`);
      setCreateModalOpen(false);
      setSerialNumber('');
      fetchTokensAndUsers();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to enroll token');
    } finally {
      setCreateSubmitting(false);
    }
  };

  const handleAssignSubmit = async (e) => {
    e.preventDefault();
    if (!selectedToken || !assignedUserId) return;
    setAssignSubmitting(true);
    try {
      await api.put(`/tokens/${selectedToken.id}/assign`, {
        userId: parseInt(assignedUserId, 10),
      });
      toast.success(`Token ${selectedToken.serialNo} assigned to user`);
      setAssignModalOpen(false);
      setSelectedToken(null);
      fetchTokensAndUsers();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to assign token');
    } finally {
      setAssignSubmitting(false);
    }
  };

  const handleStatusChange = async (tokenId, status) => {
    try {
      await api.put(`/tokens/${tokenId}/status`, { status });
      toast.success(`Token status updated to ${status}`);
      fetchTokensAndUsers();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to update token status');
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl font-bold tracking-tight text-slate-100">
            Cryptographic Tokens Inventory
          </h2>
          <p className="text-sm text-slate-400 mt-1">
            Hardware security keys, PKCS#11 devices, and software keystore token allocations.
          </p>
        </div>

        {user?.role === 'ADMIN' && (
          <Button
            variant="primary"
            size="sm"
            icon={Plus}
            onClick={() => setCreateModalOpen(true)}
          >
            Enroll New Token
          </Button>
        )}
      </div>

      {/* Grid of Tokens */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        {tokens.map((token) => (
          <motion.div
            key={token.id}
            initial={{ opacity: 0, scale: 0.95 }}
            animate={{ opacity: 1, scale: 1 }}
            className="glass p-5 flex flex-col justify-between border-slate-800/80 hover:border-indigo-500/40 transition-colors shadow-lg"
          >
            <div>
              <div className="flex items-start justify-between mb-4">
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 rounded-xl bg-indigo-500/10 border border-indigo-500/20 flex items-center justify-center text-indigo-400">
                    <KeyRound className="w-5 h-5" />
                  </div>
                  <div>
                    <h4 className="text-sm font-bold text-slate-100 font-mono">{token.serialNo}</h4>
                    <p className="text-xs text-slate-400">{token.label}</p>
                  </div>
                </div>
                <StatusBadge status={token.status} size="sm" />
              </div>

              {/* Assignment details */}
              <div className="bg-slate-900/50 p-3 rounded-xl border border-slate-800/60 mb-4 text-xs space-y-1">
                <div className="flex justify-between">
                  <span className="text-slate-500">Assigned Operator:</span>
                  <span className="font-semibold text-slate-200">
                    {token.ownerName || token.ownerEmail || 'Unassigned (In Pool)'}
                  </span>
                </div>
              </div>
            </div>

            {/* Actions */}
            {user?.role === 'ADMIN' && (
              <div className="flex items-center justify-between gap-2 pt-3 border-t border-slate-800/80">
                <Button
                  variant="secondary"
                  size="sm"
                  icon={UserCheck}
                  onClick={() => {
                    setSelectedToken(token);
                    setAssignModalOpen(true);
                  }}
                  className="flex-1"
                >
                  Assign
                </Button>

                {token.status !== 'LOST' && (
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => handleStatusChange(token.id, 'LOST')}
                    className="text-amber-400 hover:text-amber-300"
                  >
                    Flag Lost
                  </Button>
                )}
                {token.status === 'LOST' && (
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => handleStatusChange(token.id, token.ownerId ? 'ASSIGNED' : 'AVAILABLE')}
                    className="text-emerald-400 hover:text-emerald-300"
                  >
                    Reactivate
                  </Button>
                )}
              </div>
            )}
          </motion.div>
        ))}
      </div>

      {/* CREATE TOKEN MODAL */}
      <Modal
        isOpen={createModalOpen}
        onClose={() => setCreateModalOpen(false)}
        title="Enroll New Cryptographic Token"
        maxWidth="max-w-md"
      >
        <form onSubmit={handleCreateSubmit} className="space-y-4">
          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
              Token Serial Number *
            </label>
            <input
              type="text"
              required
              value={serialNumber}
              onChange={(e) => setSerialNumber(e.target.value)}
              placeholder="e.g. TKN-009 or YUBI-99812"
              className="w-full px-3.5 py-2 bg-slate-900/80 border border-slate-800 rounded-xl text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:border-indigo-500/80 font-mono"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
              Hardware / Keystore Model
            </label>
            <select
              value={model}
              onChange={(e) => setModel(e.target.value)}
              className="w-full px-3.5 py-2 bg-slate-900/80 border border-slate-800 rounded-xl text-sm text-slate-100 focus:outline-none focus:border-indigo-500/80"
            >
              <option value="YubiKey 5 NFC">YubiKey 5 NFC</option>
              <option value="Nitrokey Pro 2">Nitrokey Pro 2</option>
              <option value="Thales SafeNet eToken 5110">Thales SafeNet eToken 5110</option>
              <option value="PKCS#12 Software Keystore">PKCS#12 Software Keystore</option>
            </select>
          </div>

          <div className="flex items-center justify-end gap-3 pt-4 border-t border-slate-800">
            <Button variant="ghost" onClick={() => setCreateModalOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" loading={createSubmitting}>
              Enroll Token
            </Button>
          </div>
        </form>
      </Modal>

      {/* ASSIGN TOKEN MODAL */}
      <Modal
        isOpen={assignModalOpen}
        onClose={() => setAssignModalOpen(false)}
        title={`Assign Token ${selectedToken?.serialNo}`}
        maxWidth="max-w-md"
      >
        <form onSubmit={handleAssignSubmit} className="space-y-4">
          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
              Select Operator / User
            </label>
            <select
              required
              value={assignedUserId}
              onChange={(e) => setAssignedUserId(e.target.value)}
              className="w-full px-3.5 py-2 bg-slate-900/80 border border-slate-800 rounded-xl text-sm text-slate-100 focus:outline-none focus:border-indigo-500/80"
            >
              <option value="">-- Choose Operator --</option>
              {users.map((u) => (
                <option key={u.id} value={u.id}>
                  {u.name} ({u.email}) - {u.role}
                </option>
              ))}
            </select>
          </div>

          <div className="flex items-center justify-end gap-3 pt-4 border-t border-slate-800">
            <Button variant="ghost" onClick={() => setAssignModalOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" loading={assignSubmitting}>
              Confirm Assignment
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
