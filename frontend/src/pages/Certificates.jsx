import React, { useState, useEffect } from 'react';
import { motion } from 'framer-motion';
import {
  Shield,
  LayoutGrid,
  Table as TableIcon,
  Plus,
  Upload,
  AlertTriangle,
  Download,
  Filter,
  CheckCircle2,
} from 'lucide-react';
import CertFlipCard from '../components/crypto/CertFlipCard';
import DataTable from '../components/ui/DataTable';
import StatusBadge from '../components/ui/StatusBadge';
import Button from '../components/ui/Button';
import Modal from '../components/ui/Modal';
import api from '../api/axios';
import { useAuth } from '../context/AuthContext';
import { toast } from 'sonner';

export default function Certificates() {
  const [certs, setCerts] = useState([]);
  const [tokens, setTokens] = useState([]);
  const [loading, setLoading] = useState(true);
  const [viewMode, setViewMode] = useState('grid');
  const [statusFilter, setStatusFilter] = useState('');

  // Modals
  const [issueModalOpen, setIssueModalOpen] = useState(false);
  const [uploadModalOpen, setUploadModalOpen] = useState(false);
  const [revokeModalOpen, setRevokeModalOpen] = useState(false);
  const [selectedCert, setSelectedCert] = useState(null);

  // Issue Form State
  const [commonName, setCommonName] = useState('');
  const [organization, setOrganization] = useState('KeyBridge Corp');
  const [country, setCountry] = useState('US');
  const [validityDays, setValidityDays] = useState(365);
  const [tokenId, setTokenId] = useState('');
  const [tokenPin, setTokenPin] = useState('');
  const [issueSubmitting, setIssueSubmitting] = useState(false);

  const selectedToken = tokens.find((t) => String(t.id) === String(tokenId));

  const closeIssueModal = () => {
    setTokenPin('');
    setIssueModalOpen(false);
  };
  const onPkcs11Token = selectedToken?.keyStorage === 'PKCS11';

  // Revoke Form State
  const [revokeReason, setRevokeReason] = useState('KEY_COMPROMISE');
  const [revokeSubmitting, setRevokeSubmitting] = useState(false);

  // Upload Form State
  const [uploadFile, setUploadFile] = useState(null);
  const [uploadSubmitting, setUploadSubmitting] = useState(false);

  const { user } = useAuth();

  const fetchCertificates = async () => {
    setLoading(true);
    try {
      const url = statusFilter ? `/certs?status=${statusFilter}` : '/certs';
      const [certRes, tknRes] = await Promise.all([
        api.get(url),
        api.get('/tokens'),
      ]);
      setCerts(certRes.data || []);
      setTokens(tknRes.data || []);
    } catch (err) {
      console.error('Failed to fetch certificates', err);
      toast.error('Failed to load certificates registry');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchCertificates();
  }, [statusFilter]);

  const handleIssueSubmit = async (e) => {
    e.preventDefault();
    if (!commonName.trim()) {
      toast.error('Common Name is required');
      return;
    }
    setIssueSubmitting(true);
    try {
      await api.post('/certs/issue-test', {
        commonName,
        organization,
        country,
        validityDays: parseInt(validityDays, 10),
        tokenId: tokenId ? parseInt(tokenId, 10) : null,
        // A certificate on an assigned token belongs to the token holder.
        userId: selectedToken?.ownerId ?? null,
        pin: onPkcs11Token ? tokenPin : null,
      });
      toast.success(`Certificate for "${commonName}" successfully issued by KeyBridge Root CA!`);
      setIssueModalOpen(false);
      setCommonName('');
      fetchCertificates();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to issue certificate');
    } finally {
      setTokenPin('');
      setIssueSubmitting(false);
    }
  };

  const handleRevokeSubmit = async (e) => {
    e.preventDefault();
    if (!selectedCert) return;
    setRevokeSubmitting(true);
    try {
      await api.post(`/certs/${selectedCert.id}/revoke?reason=${revokeReason}`);
      toast.success(`Certificate ${selectedCert.serialNumber} revoked in CRL`);
      setRevokeModalOpen(false);
      setSelectedCert(null);
      fetchCertificates();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to revoke certificate');
    } finally {
      setRevokeSubmitting(false);
    }
  };

  const handleUploadSubmit = async (e) => {
    e.preventDefault();
    if (!uploadFile) {
      toast.error('Please choose a .cer or .pem certificate file');
      return;
    }
    setUploadSubmitting(true);
    const formData = new FormData();
    formData.append('file', uploadFile);
    try {
      await api.post('/certs/upload', formData);
      toast.success('Certificate uploaded and registered successfully!');
      setUploadModalOpen(false);
      setUploadFile(null);
      fetchCertificates();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to upload certificate');
    } finally {
      setUploadSubmitting(false);
    }
  };

  const handleDownloadPem = async (id) => {
    try {
      const res = await api.get(`/certs/${id}/download`, { responseType: 'blob' });
      const blob = new Blob([res.data], { type: 'application/x-pem-file' });
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', `certificate-${id}.pem`);
      document.body.appendChild(link);
      link.click();
      link.remove();
      toast.success('Certificate PEM downloaded');
    } catch (err) {
      toast.error('Failed to download certificate PEM');
    }
  };

  const tableColumns = [
    {
      header: 'Subject DN',
      field: 'subjectDn',
      render: (val, row) => (
        <div className="flex items-center gap-2.5">
          <Shield className="w-4 h-4 text-indigo-400 shrink-0" />
          <div className="min-w-0">
            <span className="font-semibold text-slate-100 truncate block">{val}</span>
            <span className="text-[10px] text-slate-400 font-mono">SN: {row.serialNumber?.slice(0, 16)}...</span>
          </div>
        </div>
      ),
    },
    {
      header: 'Issuer',
      field: 'issuerDn',
      render: (val) => <span className="text-xs text-slate-300 font-mono truncate max-w-[200px] block">{val}</span>,
    },
    {
      header: 'Status',
      field: 'status',
      render: (val) => <StatusBadge status={val} size="sm" />,
    },
    {
      header: 'Valid From',
      field: 'validFrom',
      render: (val) => new Date(val).toLocaleDateString(),
    },
    {
      header: 'Valid To',
      field: 'validTo',
      render: (val) => new Date(val).toLocaleDateString(),
    },
    {
      header: 'Actions',
      field: 'id',
      sortable: false,
      render: (id, row) => (
        <div className="flex items-center gap-2">
          <button
            onClick={() => handleDownloadPem(id)}
            className="p-1.5 hover:bg-slate-800 rounded text-slate-400 hover:text-white transition-colors"
            title="Download PEM"
          >
            <Download className="w-4 h-4" />
          </button>
          {row.status !== 'REVOKED' && (
            <button
              onClick={() => {
                setSelectedCert(row);
                setRevokeModalOpen(true);
              }}
              className="p-1.5 hover:bg-rose-500/20 rounded text-slate-400 hover:text-rose-400 transition-colors"
              title="Revoke Certificate"
            >
              <AlertTriangle className="w-4 h-4" />
            </button>
          )}
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      {/* Top Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl font-bold tracking-tight text-slate-100">
            X.509 Certificate Registry
          </h2>
          <p className="text-sm text-slate-400 mt-1">
            Browse, inspect cryptographic fingerprints, issue test certificates, or download PEM envelopes.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <Button
            variant="secondary"
            size="sm"
            icon={Upload}
            onClick={() => setUploadModalOpen(true)}
          >
            Upload PEM
          </Button>
          <Button
            variant="primary"
            size="sm"
            icon={Plus}
            onClick={() => setIssueModalOpen(true)}
          >
            Issue Test Certificate
          </Button>
        </div>
      </div>

      {/* Filter & View Switcher Bar */}
      <div className="glass p-3 flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <Filter className="w-4 h-4 text-slate-400" />
          <span className="text-xs font-semibold text-slate-400">Filter Status:</span>
          {[
            { label: 'All', value: '' },
            { label: 'Valid', value: 'VALID' },
            { label: 'Expiring', value: 'EXPIRING' },
            { label: 'Expired', value: 'EXPIRED' },
            { label: 'Revoked', value: 'REVOKED' },
          ].map((st) => (
            <button
              key={st.value}
              onClick={() => setStatusFilter(st.value)}
              className={`px-3 py-1 rounded-lg text-xs font-medium transition-colors ${
                statusFilter === st.value
                  ? 'bg-indigo-600 text-white'
                  : 'bg-slate-900/60 hover:bg-slate-800 text-slate-400'
              }`}
            >
              {st.label}
            </button>
          ))}
        </div>

        {/* View Mode Toggle */}
        <div className="flex items-center gap-1 bg-slate-900/60 p-1 rounded-xl border border-slate-800">
          <button
            onClick={() => setViewMode('grid')}
            className={`p-1.5 rounded-lg transition-colors ${
              viewMode === 'grid' ? 'bg-indigo-600 text-white' : 'text-slate-400 hover:text-slate-200'
            }`}
            title="3D Card Grid View"
          >
            <LayoutGrid className="w-4 h-4" />
          </button>
          <button
            onClick={() => setViewMode('table')}
            className={`p-1.5 rounded-lg transition-colors ${
              viewMode === 'table' ? 'bg-indigo-600 text-white' : 'text-slate-400 hover:text-slate-200'
            }`}
            title="Data Table View"
          >
            <TableIcon className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Grid or Table View */}
      {viewMode === 'grid' ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {certs.map((cert) => (
            <CertFlipCard
              key={cert.id}
              cert={cert}
              onDownload={handleDownloadPem}
              onRevoke={(c) => {
                setSelectedCert(c);
                setRevokeModalOpen(true);
              }}
            />
          ))}
        </div>
      ) : (
        <DataTable
          columns={tableColumns}
          data={certs}
          loading={loading}
          searchKeys={['subjectDn', 'issuerDn', 'serialNumber']}
          searchPlaceholder="Search certificates by Subject, Serial..."
        />
      )}

      {/* ISSUE TEST CERTIFICATE MODAL */}
      <Modal
        isOpen={issueModalOpen}
        onClose={closeIssueModal}
        title="Issue New Test Certificate"
        maxWidth="max-w-lg"
      >
        <form onSubmit={handleIssueSubmit} className="space-y-4">
          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
              Common Name (CN) *
            </label>
            <input
              type="text"
              required
              value={commonName}
              onChange={(e) => setCommonName(e.target.value)}
              placeholder="e.g. Alice Smith (Finance Ops)"
              className="w-full px-3.5 py-2 bg-slate-900/80 border border-slate-800 rounded-xl text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:border-indigo-500/80"
            />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
                Organization (O)
              </label>
              <input
                type="text"
                value={organization}
                onChange={(e) => setOrganization(e.target.value)}
                className="w-full px-3.5 py-2 bg-slate-900/80 border border-slate-800 rounded-xl text-sm text-slate-100 focus:outline-none focus:border-indigo-500/80"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
                Country (C)
              </label>
              <input
                type="text"
                maxLength={2}
                value={country}
                onChange={(e) => setCountry(e.target.value)}
                className="w-full px-3.5 py-2 bg-slate-900/80 border border-slate-800 rounded-xl text-sm text-slate-100 focus:outline-none focus:border-indigo-500/80 uppercase font-mono"
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
                Validity (Days)
              </label>
              <input
                type="number"
                min={1}
                max={3650}
                value={validityDays}
                onChange={(e) => setValidityDays(e.target.value)}
                className="w-full px-3.5 py-2 bg-slate-900/80 border border-slate-800 rounded-xl text-sm text-slate-100 focus:outline-none focus:border-indigo-500/80"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
                Bind Hardware Token
              </label>
              <select
                value={tokenId}
                onChange={(e) => setTokenId(e.target.value)}
                className="w-full px-3.5 py-2 bg-slate-900/80 border border-slate-800 rounded-xl text-sm text-slate-100 focus:outline-none focus:border-indigo-500/80"
              >
                <option value="">None (Soft Keystore)</option>
                {tokens.map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.serialNo} {t.label ? `(${t.label})` : ''}
                    {t.keyStorage === 'PKCS11' ? ` - PKCS#11 slot ${t.pkcs11Slot}` : ''}
                  </option>
                ))}
              </select>
            </div>
          </div>

          {selectedToken?.ownerId && (
            <p className="text-xs text-slate-400">
              Certificate owner: <span className="font-semibold text-slate-200">{selectedToken.ownerName || selectedToken.ownerEmail}</span> (token holder)
            </p>
          )}

          {onPkcs11Token && (
            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
                Token PIN *
              </label>
              <input
                type="password"
                required
                autoComplete="new-password"
                value={tokenPin}
                onChange={(e) => setTokenPin(e.target.value)}
                placeholder="PIN of the PKCS#11 token"
                className="w-full px-3.5 py-2 bg-slate-900/80 border border-slate-800 rounded-xl text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:border-indigo-500/80"
              />
              <span className="text-[10px] text-slate-500 mt-1 block">
                Used once to create the key on the token. Never stored or logged.
              </span>
            </div>
          )}

          <div className="p-3 bg-indigo-500/10 border border-indigo-500/20 rounded-xl text-xs text-indigo-300">
            {onPkcs11Token
              ? 'The token generates an RSA-2048 key pair on the device; the private key is non-extractable and never leaves it. KeyBridge Test CA signs the certificate, which is stored on the token next to the key.'
              : 'KeyBridge Test CA will generate an RSA-2048 keypair, sign the certificate with SHA-256 with RSA, and secure the private key inside password-protected `.p12` storage.'}
          </div>

          <div className="flex items-center justify-end gap-3 pt-4 border-t border-slate-800">
            <Button variant="ghost" onClick={closeIssueModal}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" loading={issueSubmitting}>
              Issue Certificate
            </Button>
          </div>
        </form>
      </Modal>

      {/* REVOKE CERTIFICATE MODAL */}
      <Modal
        isOpen={revokeModalOpen}
        onClose={() => setRevokeModalOpen(false)}
        title="Revoke Certificate"
        maxWidth="max-w-md"
      >
        <form onSubmit={handleRevokeSubmit} className="space-y-4">
          <p className="text-xs text-slate-300">
            Are you sure you want to revoke certificate for{' '}
            <span className="font-bold text-slate-100">{selectedCert?.subjectDn}</span>? This action is irrevocable and will immediately cause signature verifications to fail.
          </p>

          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
              Revocation Reason
            </label>
            <select
              value={revokeReason}
              onChange={(e) => setRevokeReason(e.target.value)}
              className="w-full px-3.5 py-2 bg-slate-900/80 border border-slate-800 rounded-xl text-sm text-slate-100 focus:outline-none focus:border-rose-500/80"
            >
              <option value="KEY_COMPROMISE">Key Compromise</option>
              <option value="AFFILIATION_CHANGED">Affiliation Changed</option>
              <option value="SUPERSEDED">Superseded</option>
              <option value="CESSATION_OF_OPERATION">Cessation of Operation</option>
            </select>
          </div>

          <div className="flex items-center justify-end gap-3 pt-4 border-t border-slate-800">
            <Button variant="ghost" onClick={() => setRevokeModalOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" variant="danger" loading={revokeSubmitting}>
              Revoke Now
            </Button>
          </div>
        </form>
      </Modal>

      {/* UPLOAD PEM CERTIFICATE MODAL */}
      <Modal
        isOpen={uploadModalOpen}
        onClose={() => setUploadModalOpen(false)}
        title="Upload Existing Certificate (.cer / .pem)"
        maxWidth="max-w-md"
      >
        <form onSubmit={handleUploadSubmit} className="space-y-4">
          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
              Select Certificate File
            </label>
            <input
              type="file"
              accept=".cer,.pem,.crt"
              required
              onChange={(e) => setUploadFile(e.target.files[0])}
              className="w-full text-xs text-slate-400 file:mr-4 file:py-2 file:px-4 file:rounded-xl file:border-0 file:text-xs file:font-semibold file:bg-indigo-600 file:text-white hover:file:bg-indigo-500 cursor-pointer"
            />
          </div>

          <div className="flex items-center justify-end gap-3 pt-4 border-t border-slate-800">
            <Button variant="ghost" onClick={() => setUploadModalOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" loading={uploadSubmitting}>
              Upload & Parse
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
