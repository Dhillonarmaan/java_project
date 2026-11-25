import React, { useState, useEffect } from 'react';
import { motion } from 'framer-motion';
import { Users as UsersIcon, Plus, UserPlus, Shield, Check, X } from 'lucide-react';
import DataTable from '../components/ui/DataTable';
import StatusBadge from '../components/ui/StatusBadge';
import Button from '../components/ui/Button';
import Modal from '../components/ui/Modal';
import api from '../api/axios';
import { toast } from 'sonner';

export default function Users() {
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);

  // Modal
  const [modalOpen, setModalOpen] = useState(false);
  const [email, setEmail] = useState('');
  const [fullName, setFullName] = useState('');
  const [password, setPassword] = useState('');
  const [role, setRole] = useState('USER');
  const [submitting, setSubmitting] = useState(false);

  const fetchUsers = async () => {
    setLoading(true);
    try {
      const res = await api.get('/users');
      setUsers(res.data || []);
    } catch (err) {
      console.error('Failed to fetch users', err);
      toast.error('Failed to load user directory');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchUsers();
  }, []);

  const handleCreateSubmit = async (e) => {
    e.preventDefault();
    setSubmitting(true);
    try {
      await api.post('/users', {
        name: fullName,
        email,
        password,
        role,
      });
      toast.success(`User ${fullName} created successfully!`);
      setModalOpen(false);
      setEmail('');
      setFullName('');
      setPassword('');
      fetchUsers();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to create user');
    } finally {
      setSubmitting(false);
    }
  };

  const handleToggleActive = async (user) => {
    try {
      await api.put(`/users/${user.id}`, {
        active: !user.active,
        role: user.role,
      });
      toast.success(`User status updated to ${!user.active ? 'Active' : 'Disabled'}`);
      fetchUsers();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to update user status');
    }
  };

  const columns = [
    {
      header: 'Operator',
      field: 'name',
      render: (val, row) => (
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-lg bg-indigo-500/20 text-indigo-400 font-bold flex items-center justify-center text-xs">
            {val?.slice(0, 2).toUpperCase()}
          </div>
          <div>
            <p className="font-semibold text-slate-100">{val}</p>
            <p className="text-xs text-slate-400 font-mono">{row.email}</p>
          </div>
        </div>
      ),
    },
    {
      header: 'System Role',
      field: 'role',
      render: (val) => <StatusBadge status={val} size="sm" />,
    },
    {
      header: 'Status',
      field: 'active',
      render: (val) => (
        <span
          className={`inline-flex items-center px-2 py-0.5 rounded text-[11px] font-semibold ${
            val ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20' : 'bg-rose-500/10 text-rose-400 border border-rose-500/20'
          }`}
        >
          {val ? 'Active' : 'Disabled'}
        </span>
      ),
    },
    {
      header: 'Enrolled On',
      field: 'createdAt',
      render: (val) => new Date(val).toLocaleDateString(),
    },
    {
      header: 'Actions',
      field: 'id',
      sortable: false,
      render: (id, row) => (
        <Button
          variant={row.active ? 'secondary' : 'success'}
          size="sm"
          onClick={() => handleToggleActive(row)}
        >
          {row.active ? 'Disable' : 'Activate'}
        </Button>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl font-bold tracking-tight text-slate-100">
            User Administration & Roles
          </h2>
          <p className="text-sm text-slate-400 mt-1">
            Manage administrative operators, signing users, and security auditors.
          </p>
        </div>

        <Button
          variant="primary"
          size="sm"
          icon={Plus}
          onClick={() => setModalOpen(true)}
        >
          Create New User
        </Button>
      </div>

      <DataTable
        columns={columns}
        data={users}
        loading={loading}
        searchKeys={['name', 'email', 'role']}
        searchPlaceholder="Search users by name, email, or role..."
      />

      {/* CREATE USER MODAL */}
      <Modal
        isOpen={modalOpen}
        onClose={() => setModalOpen(false)}
        title="Create New System Operator"
        maxWidth="max-w-md"
      >
        <form onSubmit={handleCreateSubmit} className="space-y-4">
          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
              Full Name *
            </label>
            <input
              type="text"
              required
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
              placeholder="e.g. John Doe"
              className="w-full px-3.5 py-2 bg-slate-900/80 border border-slate-800 rounded-xl text-sm text-slate-100 focus:outline-none focus:border-indigo-500/80"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
              Email Address *
            </label>
            <input
              type="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="e.g. jdoe@keybridge.com"
              className="w-full px-3.5 py-2 bg-slate-900/80 border border-slate-800 rounded-xl text-sm text-slate-100 focus:outline-none focus:border-indigo-500/80"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
              Password *
            </label>
            <input
              type="password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••••••"
              className="w-full px-3.5 py-2 bg-slate-900/80 border border-slate-800 rounded-xl text-sm text-slate-100 focus:outline-none focus:border-indigo-500/80"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
              Role Authority
            </label>
            <select
              value={role}
              onChange={(e) => setRole(e.target.value)}
              className="w-full px-3.5 py-2 bg-slate-900/80 border border-slate-800 rounded-xl text-sm text-slate-100 focus:outline-none focus:border-indigo-500/80"
            >
              <option value="USER">USER (Sign documents & test verify)</option>
              <option value="ADMIN">ADMIN (Full PKI & token issuance control)</option>
              <option value="AUDITOR">AUDITOR (Read-only security & audit logs)</option>
            </select>
          </div>

          <div className="flex items-center justify-end gap-3 pt-4 border-t border-slate-800">
            <Button variant="ghost" onClick={() => setModalOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" loading={submitting}>
              Create Operator
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
