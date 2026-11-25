import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  Shield,
  KeyRound,
  FileSignature,
  FileCheck2,
  Clock,
  AlertTriangle,
  ArrowUpRight,
  Plus,
  RefreshCw,
} from 'lucide-react';
import StatCard from '../components/ui/StatCard';
import StatusBadge from '../components/ui/StatusBadge';
import Button from '../components/ui/Button';
import ActivityAreaChart from '../components/charts/ActivityAreaChart';
import StatusDonut from '../components/charts/StatusDonut';
import ExpiryBarChart from '../components/charts/ExpiryBarChart';
import api from '../api/axios';
import { useAuth } from '../context/AuthContext';
import { toast } from 'sonner';

export default function Dashboard() {
  const [data, setData] = useState(null);
  const [activityData, setActivityData] = useState([]);
  const [distributionData, setDistributionData] = useState([]);
  const [expiryTimeline, setExpiryTimeline] = useState([]);
  const [activityDays, setActivityDays] = useState(7);
  const [loading, setLoading] = useState(true);
  const { user } = useAuth();
  const navigate = useNavigate();

  

  const fetchDashboardData = async () => {
    setLoading(true);
    try {
      const [dashRes, actRes, distRes, expRes] = await Promise.all([
        api.get('/dashboard'),
        api.get(`/dashboard/activity?days=${activityDays}`),
        api.get('/dashboard/status-distribution'),
        api.get('/dashboard/expiry-timeline'),
      ]);

      setData(dashRes.data);
      setActivityData(Array.isArray(actRes.data) ? actRes.data : []);

      // Normalize status distribution: backend returns Map<String, Long> { VALID: 5, ... } or Array
      const rawDist = distRes.data;
      const formattedDist = Array.isArray(rawDist)
        ? rawDist
        : rawDist && typeof rawDist === 'object'
        ? Object.entries(rawDist).map(([status, count]) => ({
            status,
            count: Number(count) || 0,
          }))
        : [];
      setDistributionData(formattedDist);

      setExpiryTimeline(Array.isArray(expRes.data) ? expRes.data : []);
    } catch (err) {
      console.error('Failed to load dashboard', err);
      toast.error('Unable to fetch cryptographic dashboard metrics');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchDashboardData();
  }, [activityDays]);

  const safeDistributionData = Array.isArray(distributionData) ? distributionData : [];
  const totalCertificates = safeDistributionData.reduce(
    (acc, curr) => acc + (Number(curr?.count) || 0),
    0
  );

  return (
    <div className="space-y-8">
      {/* Welcome & Quick Action Bar */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl font-bold tracking-tight text-slate-100">
            Cryptographic Operations Center
          </h2>
          <p className="text-sm text-slate-400 mt-1">
            Real-time monitoring for X.509 certificates, hardware tokens, and verification health.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <Button
            variant="secondary"
            size="sm"
            icon={FileCheck2}
            onClick={() => navigate('/verify')}
          >
            Verify Signature
          </Button>
          {user?.role !== 'AUDITOR' && (
            <Button
              variant="primary"
              size="sm"
              icon={FileSignature}
              onClick={() => navigate('/sign')}
            >
              Sign Document
            </Button>
          )}
          {user?.role === 'ADMIN' && (
            <Button
              variant="outline"
              size="sm"
              icon={Plus}
              onClick={() => navigate('/certificates')}
            >
              Issue Cert
            </Button>
          )}
        </div>
      </div>

      {/* KPI Stat Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5">
        <StatCard
          title="Active Certificates"
          value={data?.valid ?? data?.activeCertificatesCount ?? 0}
          icon={Shield}
          color="indigo"
          subtitle="Trusted by KeyBridge Root CA"
          onClick={() => navigate('/certificates')}
        />
        <StatCard
          title="Hardware / Soft Tokens"
          value={data?.totalTokens ?? data?.totalTokensCount ?? 0}
          icon={KeyRound}
          color="emerald"
          subtitle={`${data?.assignedTokens ?? data?.assignedTokensCount ?? 0} assigned to operators`}
          onClick={() => navigate('/tokens')}
        />
        <StatCard
          title="Pending Requests"
          value={data?.pendingRequests ?? data?.pendingRequestsCount ?? 0}
          icon={Clock}
          color="amber"
          subtitle="Awaiting administrative approval"
          onClick={() => navigate('/requests')}
        />
        <StatCard
          title="Expiring Within 30 Days"
          value={data?.expiring ?? data?.expiringCount ?? 0}
          icon={AlertTriangle}
          color="rose"
          subtitle="Action required for renewal"
          onClick={() => navigate('/certificates')}
        />
      </div>

      {/* Charts Row */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Activity Area Chart (2 Cols) */}
        <div className="lg:col-span-2 glass p-6 space-y-4">
          <div className="flex items-center justify-between">
            <div>
              <h3 className="text-base font-semibold text-slate-100">Cryptographic Operations Volume</h3>
              <p className="text-xs text-slate-400">Daily RSA signatures generated vs verifications performed</p>
            </div>
            <div className="flex items-center gap-1.5 bg-slate-900/60 p-1 rounded-xl border border-slate-800">
              <button
                onClick={() => setActivityDays(7)}
                className={`px-2.5 py-1 text-xs font-medium rounded-lg transition-colors ${
                  activityDays === 7 ? 'bg-indigo-600 text-white shadow' : 'text-slate-400 hover:text-slate-200'
                }`}
              >
                7 Days
              </button>
              <button
                onClick={() => setActivityDays(30)}
                className={`px-2.5 py-1 text-xs font-medium rounded-lg transition-colors ${
                  activityDays === 30 ? 'bg-indigo-600 text-white shadow' : 'text-slate-400 hover:text-slate-200'
                }`}
              >
                30 Days
              </button>
            </div>
          </div>

          <ActivityAreaChart data={activityData} />
        </div>

        {/* Certificate Status Donut (1 Col) */}
        <div className="glass p-6 space-y-4 flex flex-col justify-between">
          <div>
            <h3 className="text-base font-semibold text-slate-100">Status Distribution</h3>
            <p className="text-xs text-slate-400">Inventory breakdown across lifecycle states</p>
          </div>

          <StatusDonut
            data={safeDistributionData}
            totalCount={totalCertificates}
          />

          <div className="grid grid-cols-2 gap-2 pt-2 border-t border-slate-800 text-xs">
            {safeDistributionData.map((d) => (
              <div key={d.status} className="flex items-center justify-between p-1.5 rounded bg-slate-900/40">
                <span className="text-slate-400 capitalize">{(d.status || "").toLowerCase()}</span>
                <span className="font-mono font-bold text-slate-200">{d.count}</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Expiry Timeline & Live Verification Feed */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Expiry Bar Chart (1 Col) */}
        <div className="glass p-6 space-y-4">
          <div className="flex items-center justify-between">
            <div>
              <h3 className="text-base font-semibold text-slate-100">Expiration Projections</h3>
              <p className="text-xs text-slate-400">Projected certificate expirations for next 6 months</p>
            </div>
            <Clock className="w-4 h-4 text-amber-400" />
          </div>
          <ExpiryBarChart data={expiryTimeline} />
        </div>

        {/* Recent Verifications Feed (2 Cols) */}
        <div className="lg:col-span-2 glass p-6 space-y-4">
          <div className="flex items-center justify-between">
            <div>
              <h3 className="text-base font-semibold text-slate-100">Recent Verification Audit Stream</h3>
              <p className="text-xs text-slate-400">Live feed of signatures submitted to the 5-stage verification engine</p>
            </div>
            <button
              onClick={() => navigate('/verify')}
              className="text-xs font-semibold text-indigo-400 hover:text-indigo-300 flex items-center gap-1 transition-colors"
            >
              <span>Test Verify</span>
              <ArrowUpRight className="w-3.5 h-3.5" />
            </button>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-900/60 uppercase text-[10px] text-slate-400 font-semibold tracking-wider border-b border-slate-800">
                <tr>
                  <th className="py-2.5 px-3">Status</th>
                  <th className="py-2.5 px-3">Document / Subject</th>
                  <th className="py-2.5 px-3">Timestamp</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/40 text-slate-300">
                {!data?.recentVerifications || data.recentVerifications.length === 0 ? (
                  <tr>
                    <td colSpan={3} className="text-center py-6 text-slate-500">
                      No verifications performed yet. Submit a signed document to test.
                    </td>
                  </tr>
                ) : (
                  data.recentVerifications.map((v, idx) => (
                    <tr key={v.id || idx} className="hover:bg-slate-800/30 transition-colors">
                      <td className="py-2.5 px-3">
                        <StatusBadge status={v.result} size="sm" />
                      </td>
                      <td className="py-2.5 px-3 font-medium text-slate-200 truncate max-w-[200px]">
                        {v.file || v.signerSubject || 'Document'}
                      </td>
                      <td className="py-2.5 px-3 text-slate-400 whitespace-nowrap">
                        {v.at || v.verifiedAt ? new Date(v.at || v.verifiedAt).toLocaleTimeString() : '—'}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  );
}
