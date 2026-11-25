import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { AlertTriangle, Clock, GitPullRequest, ArrowRight, ShieldCheck, Check } from 'lucide-react';
import Drawer from '../components/ui/Drawer';
import api from '../api/axios';
import { useAuth } from '../context/AuthContext';

export default function NotificationDrawer({ isOpen, onClose }) {
  const [notifications, setNotifications] = useState([]);
  const [loading, setLoading] = useState(false);
  const { user } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    if (isOpen) {
      loadNotifications();
    }
  }, [isOpen]);

  const loadNotifications = async () => {
    setLoading(true);
    try {
      const res = await api.get('/dashboard');
      const stats = res.data || {};
      const items = [];

      if (user?.role === 'ADMIN' && stats.pendingRequests > 0) {
        items.push({
          id: 'req-pending',
          type: 'request',
          title: `${stats.pendingRequests} Service Request(s) Pending Review`,
          desc: 'Action required: Review certificate renewal/revocation or token allocation.',
          icon: GitPullRequest,
          color: 'indigo',
          path: '/requests',
        });
      }

      if (stats.expiring > 0) {
        items.push({
          id: 'cert-expiring',
          type: 'cert',
          title: `${stats.expiring} Certificate(s) Expiring Soon`,
          desc: 'Certificates expiring within 30 days require renewal or reissuance.',
          icon: Clock,
          color: 'amber',
          path: '/certificates',
        });
      }

      setNotifications(items);
    } catch (err) {
      console.error('Failed to load notifications', err);
    } finally {
      setLoading(false);
    }
  };

  const handleAction = (path) => {
    onClose();
    if (path) navigate(path);
  };

  return (
    <Drawer isOpen={isOpen} onClose={onClose} title="System Notifications & Alerts">
      <div className="space-y-4">
        {loading ? (
          <div className="text-center py-12 text-slate-500 text-xs">Checking alerts...</div>
        ) : notifications.length === 0 ? (
          <div className="text-center py-12 space-y-2">
            <div className="w-12 h-12 rounded-full bg-emerald-500/10 text-emerald-400 mx-auto flex items-center justify-center border border-emerald-500/20">
              <ShieldCheck className="w-6 h-6" />
            </div>
            <h4 className="text-sm font-semibold text-slate-200">All Systems Nominal</h4>
            <p className="text-xs text-slate-400">No urgent expiration warnings or pending requests.</p>
          </div>
        ) : (
          notifications.map((item) => {
            const Icon = item.icon;
            const isAmber = item.color === 'amber';
            return (
              <div
                key={item.id}
                onClick={() => handleAction(item.path)}
                className={`p-4 rounded-xl border transition-all cursor-pointer ${
                  isAmber
                    ? 'bg-amber-950/20 border-amber-500/30 hover:border-amber-500/60'
                    : 'bg-indigo-950/20 border-indigo-500/30 hover:border-indigo-500/60'
                }`}
              >
                <div className="flex items-start gap-3">
                  <div
                    className={`w-8 h-8 rounded-lg flex items-center justify-center shrink-0 border ${
                      isAmber
                        ? 'bg-amber-500/20 text-amber-400 border-amber-500/40'
                        : 'bg-indigo-500/20 text-indigo-400 border-indigo-500/40'
                    }`}
                  >
                    <Icon className="w-4 h-4" />
                  </div>
                  <div className="flex-1 min-w-0">
                    <h5 className="text-xs font-semibold text-slate-100">{item.title}</h5>
                    <p className="text-xs text-slate-400 mt-0.5">{item.desc}</p>
                    <div className="mt-2 flex items-center gap-1 text-[11px] font-medium text-indigo-400">
                      <span>Review Details</span>
                      <ArrowRight className="w-3 h-3" />
                    </div>
                  </div>
                </div>
              </div>
            );
          })
        )}
      </div>
    </Drawer>
  );
}
