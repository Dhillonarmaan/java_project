import React, { useState } from 'react';
import { Search, Bell, RefreshCw, Shield, LogOut, Sun, Moon } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { useTheme } from '../context/ThemeContext';
import api from '../api/axios';
import { toast } from 'sonner';

export default function Topbar({ onOpenCommand, onOpenNotifications }) {
  const { user, logout } = useAuth();
  const { isDark, toggleTheme } = useTheme();
  const [refreshing, setRefreshing] = useState(false);

  const handleRefreshStatus = async () => {
    setRefreshing(true);
    try {
      const res = await api.post('/certs/refresh-status');
      toast.success(res.data?.message || 'Certificate expiry evaluation completed');
    } catch (err) {
      toast.error('Failed to trigger certificate status refresh');
    } finally {
      setRefreshing(false);
    }
  };

  return (
    <header className="h-16 px-6 glass border-b border-slate-800/80 flex items-center justify-between gap-4 z-20">
      {/* Command Palette Trigger */}
      <div className="flex-1 max-w-md">
        <button
          onClick={onOpenCommand}
          className="w-full flex items-center justify-between px-3.5 py-1.5 rounded-xl bg-slate-900/60 hover:bg-slate-900 border border-slate-800 text-xs text-slate-400 hover:text-slate-200 transition-colors cursor-pointer group"
        >
          <div className="flex items-center gap-2">
            <Search className="w-4 h-4 text-slate-500 group-hover:text-indigo-400 transition-colors" />
            <span>Search or jump to...</span>
          </div>
          <kbd className="font-mono text-[10px] bg-slate-800 border border-slate-700 px-1.5 py-0.5 rounded text-slate-400">
            Ctrl + K
          </kbd>
        </button>
      </div>

      {/* Right Controls */}
      <div className="flex items-center gap-3">
        {/* On-demand Expiry Check Button */}
        {user?.role === 'ADMIN' && (
          <button
            onClick={handleRefreshStatus}
            disabled={refreshing}
            className="p-2 rounded-xl bg-slate-900/60 hover:bg-slate-800 border border-slate-800 text-slate-400 hover:text-indigo-400 transition-colors disabled:opacity-50"
            title="Evaluate Expiry Statuses Now"
          >
            <RefreshCw className={`w-4 h-4 ${refreshing ? 'animate-spin text-indigo-400' : ''}`} />
          </button>
        )}

        {/* Notification Bell */}
        <button
          onClick={onOpenNotifications}
          className="relative p-2 rounded-xl bg-slate-900/60 hover:bg-slate-800 border border-slate-800 text-slate-400 hover:text-slate-200 transition-colors cursor-pointer"
          title="Notifications & Alerts"
        >
          <Bell className="w-4 h-4" />
          <span className="absolute top-1.5 right-1.5 w-2 h-2 rounded-full bg-indigo-500 ring-2 ring-slate-950 animate-pulse" />
        </button>

        {/* User Info & Avatar */}
        <div className="flex items-center gap-2.5 pl-2 border-l border-slate-800">
          <div className="w-8 h-8 rounded-full bg-gradient-to-tr from-indigo-500 to-purple-600 flex items-center justify-center font-bold text-xs text-white shadow-md shadow-indigo-500/20">
            {user?.name ? user.name.charAt(0).toUpperCase() : 'U'}
          </div>
          <div className="hidden sm:block text-left text-xs">
            <p className="font-semibold text-slate-200 leading-tight">{user?.name || user?.email}</p>
            <p className="text-[10px] text-slate-400 font-mono">{user?.role}</p>
          </div>
          <button
            onClick={logout}
            className="p-1.5 hover:bg-slate-800 text-slate-400 hover:text-rose-400 rounded-lg transition-colors ml-1"
            title="Sign out"
          >
            <LogOut className="w-4 h-4" />
          </button>
        </div>
      </div>
    </header>
  );
}
