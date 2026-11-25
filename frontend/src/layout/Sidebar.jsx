import React from 'react';
import { NavLink, useLocation } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  LayoutDashboard,
  Shield,
  KeyRound,
  FileSignature,
  FileCheck2,
  GitPullRequest,
  ClipboardList,
  History,
  Users,
  ChevronLeft,
  ChevronRight,
  LogOut,
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import clsx from 'clsx';

const NAV_ITEMS = [
  { path: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { path: '/certificates', label: 'Certificates', icon: Shield },
  { path: '/tokens', label: 'Tokens', icon: KeyRound },
  { path: '/sign', label: 'Sign Document', icon: FileSignature, roles: ['ADMIN', 'USER'] },
  { path: '/verify', label: 'Verify Signature', icon: FileCheck2 },
  { path: '/requests', label: 'Service Requests', icon: GitPullRequest },
  { path: '/dsc', label: 'DSC Applications', icon: ClipboardList },
  { path: '/audit', label: 'Audit Logs', icon: History, roles: ['ADMIN', 'AUDITOR'] },
  { path: '/users', label: 'Users', icon: Users, roles: ['ADMIN'] },
];

export default function Sidebar({ collapsed, setCollapsed }) {
  const { user, logout } = useAuth();
  const location = useLocation();

  const userRole = user?.role || 'USER';
  const filteredNav = NAV_ITEMS.filter((item) => !item.roles || item.roles.includes(userRole));

  return (
    <motion.aside
      initial={false}
      animate={{ width: collapsed ? 76 : 260 }}
      transition={{ duration: 0.3, ease: [0.23, 1, 0.32, 1] }}
      className="relative z-30 h-screen glass border-r border-slate-800/80 flex flex-col justify-between select-none"
    >
      {/* Top Header / Logo */}
      <div>
        <div className="h-16 flex items-center justify-between px-4 border-b border-slate-800/80">
          <div className="flex items-center gap-3 overflow-hidden">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-indigo-600 to-violet-500 flex items-center justify-center shrink-0 shadow-lg shadow-indigo-500/25">
              <Shield className="w-5 h-5 text-white" />
            </div>
            {!collapsed && (
              <motion.div
                initial={{ opacity: 0, x: -10 }}
                animate={{ opacity: 1, x: 0 }}
                className="overflow-hidden whitespace-nowrap"
              >
                <h1 className="text-base font-bold text-slate-100 tracking-tight leading-none">
                  Key<span className="text-indigo-400">Bridge</span>
                </h1>
                <p className="text-[10px] text-slate-500 uppercase tracking-widest font-semibold mt-0.5">
                  PKI & Crypto Platform
                </p>
              </motion.div>
            )}
          </div>
          <button
            onClick={() => setCollapsed(!collapsed)}
            className="w-7 h-7 rounded-lg bg-slate-800/60 hover:bg-slate-700/60 text-slate-400 hover:text-slate-100 flex items-center justify-center transition-colors"
            title={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
          >
            {collapsed ? <ChevronRight className="w-4 h-4" /> : <ChevronLeft className="w-4 h-4" />}
          </button>
        </div>

        {/* Navigation links */}
        <nav className="p-3 space-y-1.5">
          {filteredNav.map((item) => {
            const Icon = item.icon;
            const isActive = location.pathname.startsWith(item.path);

            return (
              <NavLink
                key={item.path}
                to={item.path}
                className={clsx(
                  'relative flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-medium transition-all group',
                  isActive ? 'text-white' : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/40'
                )}
              >
                {/* Framer Motion layoutId active pill */}
                {isActive && (
                  <motion.div
                    layoutId="nav-pill"
                    transition={{ type: 'spring', stiffness: 350, damping: 30 }}
                    className="absolute inset-0 bg-gradient-to-r from-indigo-600/80 to-violet-600/80 rounded-xl shadow-lg shadow-indigo-500/20 border border-indigo-400/30"
                  />
                )}

                <span className="relative z-10 shrink-0">
                  <Icon
                    className={clsx(
                      'w-5 h-5 transition-transform group-hover:scale-110',
                      isActive ? 'text-white' : 'text-slate-400 group-hover:text-slate-200'
                    )}
                  />
                </span>

                {!collapsed && (
                  <motion.span
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    className="relative z-10 whitespace-nowrap overflow-hidden text-ellipsis"
                  >
                    {item.label}
                  </motion.span>
                )}
              </NavLink>
            );
          })}
        </nav>
      </div>

      {/* User profile & Logout */}
      <div className="p-3 border-t border-slate-800/80">
        <div
          className={clsx(
            'flex items-center gap-3 p-2 rounded-xl bg-slate-900/60 border border-slate-800/80',
            collapsed ? 'justify-center' : 'justify-between'
          )}
        >
          <div className="flex items-center gap-2.5 overflow-hidden">
            <div className="w-8 h-8 rounded-lg bg-indigo-500/20 text-indigo-400 border border-indigo-500/30 flex items-center justify-center font-bold text-xs shrink-0">
              {user?.name ? user.name.slice(0, 2).toUpperCase() : 'U'}
            </div>
            {!collapsed && (
              <div className="overflow-hidden leading-tight">
                <p className="text-xs font-semibold text-slate-200 truncate">{user?.name || user?.email}</p>
                <p className="text-[10px] text-indigo-400 font-mono">{user?.role}</p>
              </div>
            )}
          </div>
          {!collapsed && (
            <button
              onClick={logout}
              className="p-1.5 hover:bg-slate-800 text-slate-400 hover:text-rose-400 rounded-lg transition-colors"
              title="Logout"
            >
              <LogOut className="w-4 h-4" />
            </button>
          )}
        </div>
      </div>
    </motion.aside>
  );
}
