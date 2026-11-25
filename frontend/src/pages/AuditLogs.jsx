import React, { useState, useEffect } from 'react';
import { motion } from 'framer-motion';
import { History, Shield, RefreshCw, Terminal, Eye } from 'lucide-react';
import DataTable from '../components/ui/DataTable';
import StatusBadge from '../components/ui/StatusBadge';
import Button from '../components/ui/Button';
import api from '../api/axios';
import { toast } from 'sonner';

export default function AuditLogs() {
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [autoPoll, setAutoPoll] = useState(false);

  const fetchLogs = async () => {
    try {
      // The newest 100 entries (the API's maximum page size); the table pages and searches them.
      const res = await api.get('/audit?size=100');
      setLogs(res.data?.content || res.data || []);
    } catch (err) {
      console.error('Failed to fetch audit trail', err);
      toast.error('Failed to load audit logs');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchLogs();
  }, []);

  // Live polling effect
  useEffect(() => {
    if (!autoPoll) return;
    const interval = setInterval(() => {
      fetchLogs();
    }, 10000);
    return () => clearInterval(interval);
  }, [autoPoll]);

  const columns = [
    {
      header: 'Action',
      field: 'action',
      render: (val) => (
        <span className="font-mono text-xs font-semibold px-2 py-0.5 rounded bg-indigo-500/10 text-indigo-300 border border-indigo-500/20">
          {val}
        </span>
      ),
    },
    {
      header: 'Actor',
      field: 'username',
      render: (val, row) => (
        <span className="font-semibold text-slate-200">
          {val || row?.actor || (row?.userId ? `User #${row.userId}` : 'System / Administrator')}
        </span>
      ),
    },
    {
      header: 'Client IP',
      field: 'ipAddress',
      render: (val, row) => <span className="font-mono text-xs text-slate-400">{val || row?.ip || '127.0.0.1'}</span>,
    },
    {
      header: 'Timestamp',
      field: 'createdAt',
      render: (val, row) => {
        const time = val || row?.timestamp;
        return time ? new Date(time).toLocaleString() : '—';
      },
    },
  ];

  const renderExpandedRow = (log) => (
    <div className="space-y-3 p-2 text-xs">
      <div className="flex items-center gap-2 text-slate-400 font-semibold uppercase tracking-wider">
        <Terminal className="w-3.5 h-3.5 text-indigo-400" />
        <span>Execution Details & Intercepted Arguments</span>
      </div>
      <div className="bg-slate-950/80 p-3 rounded-xl border border-slate-800 font-mono text-xs text-slate-300 overflow-x-auto">
        <pre className="whitespace-pre-wrap break-all">
          {log.details || log.parameters || 'No additional payload arguments recorded.'}
        </pre>
      </div>
    </div>
  );

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl font-bold tracking-tight text-slate-100">
            Immutable Security Audit Trail
          </h2>
          <p className="text-sm text-slate-400 mt-1">
            Spring AOP-intercepted security events recording actor identities, digital operations, and IP addresses.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <button
            onClick={() => setAutoPoll(!autoPoll)}
            className={`flex items-center gap-2 px-3 py-1.5 rounded-xl border text-xs font-medium transition-colors ${
              autoPoll
                ? 'bg-emerald-500/20 text-emerald-300 border-emerald-500/40 animate-pulse'
                : 'bg-slate-900/60 text-slate-400 border-slate-800 hover:text-slate-200'
            }`}
          >
            <RefreshCw className={`w-3.5 h-3.5 ${autoPoll ? 'animate-spin' : ''}`} />
            <span>{autoPoll ? 'Live Polling (10s)' : 'Enable Live Polling'}</span>
          </button>

          <Button variant="secondary" size="sm" icon={RefreshCw} onClick={fetchLogs}>
            Refresh
          </Button>
        </div>
      </div>

      <DataTable
        columns={columns}
        data={logs}
        loading={loading}
        searchKeys={['action', 'username', 'ip', 'ipAddress', 'details']}
        searchPlaceholder="Filter audit records by Action, Actor..."
        renderExpandedRow={renderExpandedRow}
      />
    </div>
  );
}
