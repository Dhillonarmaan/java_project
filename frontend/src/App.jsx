import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { MotionConfig } from 'framer-motion';
import { Toaster } from 'sonner';

import { AuthProvider } from './context/AuthContext';
import { ThemeProvider } from './context/ThemeContext';
import ProtectedRoute from './components/ui/ProtectedRoute';
import AppShell from './layout/AppShell';

import Login from './pages/Login';
import Dashboard from './pages/Dashboard';
import Certificates from './pages/Certificates';
import Tokens from './pages/Tokens';
import SignDocument from './pages/SignDocument';
import VerifyDocument from './pages/VerifyDocument';
import Requests from './pages/Requests';
import AuditLogs from './pages/AuditLogs';
import Users from './pages/Users';
import DscApplications from './pages/DscApplications';
import DscApplicationDetail from './pages/DscApplicationDetail';

export default function App() {
  return (
    <MotionConfig reducedMotion="user">
      <ThemeProvider>
        <AuthProvider>
          <BrowserRouter>
            <Toaster
              theme="dark"
              position="bottom-right"
              richColors
              closeButton
              toastOptions={{
                className: 'glass border-slate-800 text-xs shadow-2xl',
              }}
            />
            <Routes>
              {/* Public route */}
              <Route path="/login" element={<Login />} />

              {/* Protected system shell */}
              <Route
                path="/"
                element={
                  <ProtectedRoute>
                    <AppShell />
                  </ProtectedRoute>
                }
              >
                <Route index element={<Navigate to="/dashboard" replace />} />
                <Route path="dashboard" element={<Dashboard />} />
                <Route path="certificates" element={<Certificates />} />
                <Route path="tokens" element={<Tokens />} />
                <Route
                  path="sign"
                  element={
                    <ProtectedRoute roles={['ADMIN', 'USER']}>
                      <SignDocument />
                    </ProtectedRoute>
                  }
                />
                <Route path="verify" element={<VerifyDocument />} />
                <Route path="requests" element={<Requests />} />
                <Route path="dsc" element={<DscApplications />} />
                <Route path="dsc/:id" element={<DscApplicationDetail />} />
                <Route
                  path="audit"
                  element={
                    <ProtectedRoute roles={['ADMIN', 'AUDITOR']}>
                      <AuditLogs />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="users"
                  element={
                    <ProtectedRoute roles={['ADMIN']}>
                      <Users />
                    </ProtectedRoute>
                  }
                />
              </Route>

              {/* Fallback */}
              <Route path="*" element={<Navigate to="/dashboard" replace />} />
            </Routes>
          </BrowserRouter>
        </AuthProvider>
      </ThemeProvider>
    </MotionConfig>
  );
}
