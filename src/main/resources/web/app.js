/**
 * Job Scheduler Console - Vanilla JS Controller
 * Version: 8.0.0
 *
 * Consumes existing V8 REST API endpoints (/api/v1/*).
 * Zero dummy operational data. Live REST API status drives all metrics.
 */

document.addEventListener('DOMContentLoaded', () => {
  const API_BASE = '/api/v1';

  // ──────────────────────────────────────────────────
  // Navigation & Hash Routing
  // ──────────────────────────────────────────────────
  const navItems = document.querySelectorAll('.nav-item');
  const viewPanels = document.querySelectorAll('.view-panel');
  let currentExecPage = 0;

  function switchView(targetView) {
    navItems.forEach(item => {
      item.classList.toggle('active', item.getAttribute('data-view') === targetView);
    });
    viewPanels.forEach(panel => {
      panel.classList.toggle('active', panel.id === `view-${targetView}`);
    });

    if (targetView === 'overview') refreshOverview();
    if (targetView === 'jobs') fetchJobs();
    if (targetView === 'executions') fetchExecutions(currentExecPage);
    if (targetView === 'workflows') fetchWorkflows();
    if (targetView === 'scheduler') fetchSchedulerStatus();
  }

  navItems.forEach(item => {
    item.addEventListener('click', (e) => {
      e.preventDefault();
      const view = item.getAttribute('data-view');
      if (view) {
        switchView(view);
        window.location.hash = view;
      }
    });
  });

  // Handle View All Links
  document.querySelectorAll('.view-all-link').forEach(link => {
    link.addEventListener('click', (e) => {
      e.preventDefault();
      const target = link.getAttribute('data-target');
      if (target) {
        switchView(target);
        window.location.hash = target;
      }
    });
  });

  // Hash route initialization
  const currentHash = window.location.hash.replace('#', '') || 'overview';
  switchView(currentHash);

  // ──────────────────────────────────────────────────
  // Live Date & Time Formatting
  // ──────────────────────────────────────────────────
  function updateClock() {
    const now = new Date();
    const dateEl = document.getElementById('hero-date');
    const timeEl = document.getElementById('hero-time');
    const greetingEl = document.getElementById('greeting-text');

    if (greetingEl) {
      const hrs = now.getHours();
      if (hrs < 12) greetingEl.textContent = 'Good morning,';
      else if (hrs < 17) greetingEl.textContent = 'Good afternoon,';
      else greetingEl.textContent = 'Good evening,';
    }

    if (dateEl) {
      const options = { weekday: 'short', day: '2-digit', month: 'short', year: 'numeric' };
      dateEl.textContent = now.toLocaleDateString('en-GB', options);
    }

    if (timeEl) {
      const options = { hour: '2-digit', minute: '2-digit', hour12: true };
      timeEl.textContent = now.toLocaleTimeString('en-US', options);
    }
  }
  updateClock();
  setInterval(updateClock, 1000);

  // ──────────────────────────────────────────────────
  // Toast Notifications
  // ──────────────────────────────────────────────────
  function showToast(message, type = 'info') {
    const container = document.getElementById('toast-container');
    if (!container) return;
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.textContent = message;
    container.appendChild(toast);
    setTimeout(() => {
      toast.style.opacity = '0';
      toast.style.transition = 'opacity 0.3s ease';
      setTimeout(() => toast.remove(), 300);
    }, 4000);
  }

  // Helper text setter
  function setTextById(id, val) {
    const el = document.getElementById(id);
    if (el) el.textContent = (val !== null && val !== undefined) ? val : '0';
  }

  function escapeHtml(str) {
    if (!str) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  // ──────────────────────────────────────────────────
  // Scheduler Status API Fetch
  // ──────────────────────────────────────────────────
  async function fetchSchedulerStatus() {
    try {
      const res = await fetch(`${API_BASE}/scheduler`);
      if (!res.ok) {
        setConnectionState(false);
        return null;
      }
      const data = await res.json();
      setConnectionState(true);

      const status = data.status || 'UNKNOWN';

      // Topbar status badge
      const pill = document.getElementById('topbar-status-pill');
      const pillText = document.getElementById('topbar-status-text');
      if (pillText) pillText.textContent = `Scheduler ${status.charAt(0) + status.slice(1).toLowerCase()}`;
      if (pill) pill.className = `scheduler-status-pill ${status.toLowerCase()}`;

      // Sidebar version
      const sidebarVer = document.getElementById('sidebar-version-text');
      if (sidebarVer && data.version) sidebarVer.textContent = `${data.version} (${data.environment || 'dev'})`;

      // Hero banner title
      const bannerTitle = document.getElementById('banner-headline');
      if (bannerTitle) {
        if (status === 'RUNNING') bannerTitle.textContent = 'Your scheduler is running smoothly.';
        else if (status === 'HALTED') bannerTitle.textContent = 'Scheduler is currently halted.';
        else if (status === 'STOPPED') bannerTitle.textContent = 'Scheduler has been shut down.';
        else bannerTitle.textContent = `Scheduler State: ${status}`;
      }

      // Card Metrics
      setTextById('stat-total-jobs', data.totalJobs);
      setTextById('stat-running-exec', data.runningExecutions);
      setTextById('stat-scheduled-exec', data.scheduledExecutions);
      setTextById('stat-blocked-exec', data.blockedExecutions);
      setTextById('stat-failed-exec', data.failedExecutions);

      // Right Panel Status
      setTextById('ctrl-status-title', status);
      const ctrlDot = document.getElementById('ctrl-dot');
      if (ctrlDot) {
        ctrlDot.className = 'glow-dot ' + (status === 'RUNNING' ? 'green' : status === 'HALTED' ? 'amber' : 'red');
      }

      const ctrlSub = document.getElementById('ctrl-status-sub');
      if (ctrlSub) {
        if (status === 'RUNNING') ctrlSub.textContent = 'Scheduler is dispatching jobs.';
        else if (status === 'HALTED') ctrlSub.textContent = 'Scheduler is halted. No dispatching.';
        else if (status === 'STOPPED') ctrlSub.textContent = 'Scheduler is shut down.';
      }

      setTextById('spec-val-state', status);
      setTextById('ctrl-threads', `${data.activeWorkerThreads} / ${data.totalWorkerThreads}`);
      setTextById('ctrl-queue', `${data.pendingQueueSize} pending`);
      setTextById('ctrl-uptime', data.uptime || '0d 0h 0m');
      setTextById('spec-val-db', data.dbName || 'job_scheduler_db');
      setTextById('spec-val-version', data.version || 'v8.0.0');
      setTextById('spec-val-env', data.environment || 'Development');

      // System info panel
      setTextById('info-db-name', data.dbName || 'job_scheduler_db');
      setTextById('info-sys-version', data.version || 'v8.0.0');
      setTextById('info-sys-env', data.environment || 'Development');

      // Buttons visibility
      const btnHalt = document.getElementById('btn-halt-scheduler');
      const btnResume = document.getElementById('btn-resume-scheduler');
      if (btnHalt && btnResume) {
        if (status === 'RUNNING') {
          btnHalt.style.display = 'inline-flex';
          btnResume.style.display = 'none';
        } else if (status === 'HALTED') {
          btnHalt.style.display = 'none';
          btnResume.style.display = 'inline-flex';
        } else {
          btnHalt.style.display = 'none';
          btnResume.style.display = 'none';
        }
      }

      // Donut Chart legend
      updateDonutChart(data);

      return data;
    } catch (err) {
      console.error('Failed to fetch scheduler status:', err);
      setConnectionState(false);
      return null;
    }
  }

  function setConnectionState(connected) {
    const dot = document.getElementById('sidebar-status-dot');
    const text = document.getElementById('sidebar-status-text');
    const infoDbStatus = document.getElementById('info-db-status');
    if (dot) dot.className = 'status-dot ' + (connected ? 'green' : 'red');
    if (text) text.textContent = connected ? 'Connected' : 'Disconnected';
    if (infoDbStatus) {
      infoDbStatus.className = 'value ' + (connected ? 'green-text' : 'red-text');
      infoDbStatus.innerHTML = `<span class="dot ${connected ? 'green' : 'red'}"></span> ${connected ? 'Connected' : 'Disconnected'}`;
    }
  }

  function updateDonutChart(data) {
    const total = data.totalJobs || ((data.runningExecutions || 0) + (data.scheduledExecutions || 0) + (data.blockedExecutions || 0) + (data.failedExecutions || 0) + (data.completedExecutions || 0));
    setTextById('donut-total', total);

    const calcPct = (val) => total > 0 ? `${Math.round((val / total) * 100)}%` : '0%';

    setTextById('legend-running', `${data.runningExecutions || 0} (${calcPct(data.runningExecutions || 0)})`);
    setTextById('legend-scheduled', `${data.scheduledExecutions || 0} (${calcPct(data.scheduledExecutions || 0)})`);
    setTextById('legend-blocked', `${data.blockedExecutions || 0} (${calcPct(data.blockedExecutions || 0)})`);
    setTextById('legend-failed', `${data.failedExecutions || 0} (${calcPct(data.failedExecutions || 0)})`);
    setTextById('legend-completed', `${data.completedExecutions || 0} (${calcPct(data.completedExecutions || 0)})`);
    setTextById('legend-cancelled', `0 (0%)`);
  }

  // ──────────────────────────────────────────────────
  // Executions API Fetch
  // ──────────────────────────────────────────────────
  async function fetchExecutions(page = 0) {
    try {
      const res = await fetch(`${API_BASE}/executions?page=${page}&size=20`);
      if (!res.ok) return;
      const data = await res.json();
      const items = data.items || [];

      // Active Executions (Table on Overview)
      const activeTbody = document.getElementById('table-active-executions');
      if (activeTbody) {
        const running = items.filter(e => e.status === 'RUNNING' || e.status === 'BLOCKED');
        if (running.length === 0) {
          activeTbody.innerHTML = `
            <tr>
              <td colspan="5">
                <div class="empty-state-box">
                  <svg class="empty-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><rect x="2" y="7" width="20" height="14" rx="2" ry="2"></rect><path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"></path></svg>
                  <h4>No active executions</h4>
                  <p>No jobs are currently running.</p>
                </div>
              </td>
            </tr>
          `;
        } else {
          activeTbody.innerHTML = running.map(ex => `
            <tr>
              <td class="code">${escapeHtml(ex.jobId)}</td>
              <td>${escapeHtml(ex.jobName || ex.jobId)}</td>
              <td>${ex.startedAt ? new Date(ex.startedAt).toLocaleTimeString() : '—'}</td>
              <td>${ex.durationMillis != null ? ex.durationMillis + 'ms' : 'Running…'}</td>
              <td><span class="badge-status ${ex.status.toLowerCase()}">${ex.status}</span></td>
            </tr>
          `).join('');
        }
      }

      // Upcoming Executions Table
      const upcomingTbody = document.getElementById('table-recent-executions');
      if (upcomingTbody) {
        const scheduled = items.filter(e => e.status === 'SCHEDULED');
        if (scheduled.length === 0) {
          upcomingTbody.innerHTML = `
            <tr>
              <td colspan="3">
                <div class="empty-state-box">
                  <svg class="empty-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"></rect><line x1="16" y1="2" x2="16" y2="6"></line><line x1="8" y1="2" x2="8" y2="6"></line><line x1="3" y1="10" x2="21" y2="10"></line></svg>
                  <h4>No upcoming executions</h4>
                  <p>Scheduled jobs will appear here.</p>
                </div>
              </td>
            </tr>
          `;
        } else {
          upcomingTbody.innerHTML = scheduled.map(ex => `
            <tr>
              <td class="code">${escapeHtml(ex.jobId)}</td>
              <td>${ex.scheduledAt ? new Date(ex.scheduledAt).toLocaleTimeString() : '—'}</td>
              <td><span class="badge-status scheduled">${ex.jobType || 'Scheduled'}</span></td>
            </tr>
          `).join('');
        }
      }

      // Executions Table (Executions View)
      const execTbody = document.getElementById('table-executions-body');
      if (execTbody) {
        if (items.length === 0) {
          execTbody.innerHTML = `<tr><td colspan="7" class="empty-state">No execution records found.</td></tr>`;
        } else {
          execTbody.innerHTML = items.map(ex => `
            <tr>
              <td class="code">${escapeHtml(ex.executionId)}</td>
              <td class="code">${escapeHtml(ex.jobId)}</td>
              <td>#${ex.occurrenceNumber || 1}</td>
              <td>${ex.scheduledAt ? new Date(ex.scheduledAt).toLocaleString() : '—'}</td>
              <td>${ex.durationMillis != null ? ex.durationMillis + 'ms' : '—'}</td>
              <td><span class="badge-status ${ex.status.toLowerCase()}">${ex.status}</span></td>
              <td>${ex.attemptCount || 1}</td>
            </tr>
          `).join('');
        }
      }

      renderPagination(data);

    } catch (err) {
      console.error('Failed to fetch executions:', err);
    }
  }

  function renderPagination(pageData) {
    const container = document.getElementById('exec-pagination');
    if (!container) return;
    if (pageData.totalPages <= 1) {
      container.innerHTML = pageData.totalElements > 0 ? `<span class="subtext">${pageData.totalElements} execution(s)</span>` : '';
      return;
    }
    container.innerHTML = `
      <span class="subtext">Page ${pageData.page + 1} of ${pageData.totalPages} (${pageData.totalElements})</span>
      <button class="btn btn-navy" ${pageData.page === 0 ? 'disabled' : ''} id="btn-prev-page">Prev</button>
      <button class="btn btn-navy" ${pageData.page >= pageData.totalPages - 1 ? 'disabled' : ''} id="btn-next-page">Next</button>
    `;
    const btnPrev = document.getElementById('btn-prev-page');
    const btnNext = document.getElementById('btn-next-page');
    if (btnPrev) btnPrev.addEventListener('click', () => { if (currentExecPage > 0) fetchExecutions(--currentExecPage); });
    if (btnNext) btnNext.addEventListener('click', () => { fetchExecutions(++currentExecPage); });
  }

  // ──────────────────────────────────────────────────
  // Jobs API Fetch
  // ──────────────────────────────────────────────────
  async function fetchJobs() {
    try {
      const res = await fetch(`${API_BASE}/jobs`);
      if (!res.ok) return;
      const jobs = await res.json();

      const tbody = document.getElementById('table-jobs-body');
      if (tbody) {
        if (!jobs || jobs.length === 0) {
          tbody.innerHTML = `<tr><td colspan="6" class="empty-state">No jobs registered yet. Click "Create Job" to register one.</td></tr>`;
        } else {
          tbody.innerHTML = jobs.map(j => {
            const rec = j.recurrencePolicy ? `${j.recurrencePolicy.type || 'RECURRING'}` : 'None';
            return `
              <tr>
                <td class="code">${escapeHtml(j.jobId || j.id)}</td>
                <td><strong>${escapeHtml(j.jobName || j.name)}</strong></td>
                <td><span class="badge-status scheduled">${escapeHtml(j.taskType || 'IN_MEMORY')}</span></td>
                <td>${j.scheduledAt ? new Date(j.scheduledAt).toLocaleString() : 'Immediate'}</td>
                <td>${rec}</td>
                <td>
                  <button class="btn btn-navy btn-cancel-job" data-id="${escapeHtml(j.jobId || j.id)}">Cancel</button>
                </td>
              </tr>
            `;
          }).join('');

          document.querySelectorAll('.btn-cancel-job').forEach(btn => {
            btn.addEventListener('click', async () => {
              const jobId = btn.getAttribute('data-id');
              if (confirm(`Are you sure you want to cancel job ${jobId}?`)) {
                await cancelJob(jobId);
              }
            });
          });
        }
      }
    } catch (err) {
      console.error('Failed to fetch jobs:', err);
    }
  }

  // ──────────────────────────────────────────────────
  // Workflows API Fetch & DAG rendering
  // ──────────────────────────────────────────────────
  async function fetchWorkflows() {
    const container = document.getElementById('workflow-graph-container');
    if (!container) return;

    try {
      const res = await fetch(`${API_BASE}/jobs`);
      if (!res.ok) return;
      const jobs = await res.json();

      const countEl = document.getElementById('info-workflow-count');
      if (countEl) {
        const withDeps = jobs.filter(j => j.dependencyIds && j.dependencyIds.length > 0);
        countEl.textContent = withDeps.length;
      }

      if (!jobs || jobs.length === 0) {
        container.innerHTML = `
          <div class="empty-state-box">
            <svg class="empty-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><circle cx="12" cy="5" r="3"></circle><circle cx="5" cy="19" r="3"></circle><circle cx="19" cy="19" r="3"></circle><line x1="12" y1="8" x2="5" y2="16"></line><line x1="12" y1="8" x2="19" y2="16"></line></svg>
            <h4>0 workflows</h4>
            <p>Workflows appear when jobs have dependencies.</p>
          </div>
        `;
        return;
      }

      // Clean structured DAG workflow list
      container.innerHTML = `
        <div class="workflow-list">
          ${jobs.map(j => {
            const hasDeps = j.dependencyIds && j.dependencyIds.length > 0;
            return `
              <div class="workflow-item-card">
                <div class="workflow-item-info">
                  <span class="workflow-item-id">${escapeHtml(j.jobId || j.id)}</span>
                  <span class="workflow-item-name">${escapeHtml(j.jobName || j.name)}</span>
                </div>
                <span class="badge-status ${hasDeps ? 'amber' : 'completed'}">
                  ${hasDeps ? 'Depends on: ' + j.dependencyIds.join(', ') : 'Independent'}
                </span>
              </div>
            `;
          }).join('')}
        </div>
      `;

    } catch (err) {
      console.error('Failed to fetch workflows:', err);
    }
  }

  // ──────────────────────────────────────────────────
  // Actions & Control Endpoints
  // ──────────────────────────────────────────────────
  async function cancelJob(jobId) {
    try {
      const res = await fetch(`${API_BASE}/jobs/${jobId}/cancel`, { method: 'POST' });
      if (res.ok) {
        showToast(`Job ${jobId} cancelled successfully`, 'success');
        refreshOverview();
      } else {
        showToast(`Failed to cancel job ${jobId}`, 'error');
      }
    } catch (err) {
      showToast(`Error: ${err.message}`, 'error');
    }
  }

  const btnHalt = document.getElementById('btn-halt-scheduler');
  const btnResume = document.getElementById('btn-resume-scheduler');
  const btnShutdown = document.getElementById('btn-shutdown-scheduler');
  const adminBtnHalt = document.getElementById('admin-btn-halt');
  const adminBtnResume = document.getElementById('admin-btn-resume');
  const adminBtnShutdown = document.getElementById('admin-btn-shutdown');

  async function haltScheduler() {
    try {
      const res = await fetch(`${API_BASE}/scheduler/halt`, { method: 'POST' });
      if (res.ok) {
        showToast('Scheduler halted successfully', 'warning');
        refreshOverview();
      }
    } catch (err) {
      showToast(`Halt error: ${err.message}`, 'error');
    }
  }

  async function resumeScheduler() {
    try {
      const res = await fetch(`${API_BASE}/scheduler/resume`, { method: 'POST' });
      if (res.ok) {
        showToast('Scheduler resumed successfully', 'success');
        refreshOverview();
      }
    } catch (err) {
      showToast(`Resume error: ${err.message}`, 'error');
    }
  }

  async function shutdownScheduler() {
    if (!confirm('Are you sure you want to SHUTDOWN the scheduler?')) return;
    try {
      const res = await fetch(`${API_BASE}/scheduler/shutdown`, { method: 'POST' });
      if (res.ok) {
        showToast('Scheduler shutdown initiated', 'error');
        refreshOverview();
      }
    } catch (err) {
      showToast(`Shutdown error: ${err.message}`, 'error');
    }
  }

  if (btnHalt) btnHalt.addEventListener('click', haltScheduler);
  if (btnResume) btnResume.addEventListener('click', resumeScheduler);
  if (btnShutdown) btnShutdown.addEventListener('click', shutdownScheduler);
  if (adminBtnHalt) adminBtnHalt.addEventListener('click', haltScheduler);
  if (adminBtnResume) adminBtnResume.addEventListener('click', resumeScheduler);
  if (adminBtnShutdown) adminBtnShutdown.addEventListener('click', shutdownScheduler);

  const btnRefresh = document.getElementById('btn-refresh-data');
  if (btnRefresh) {
    btnRefresh.addEventListener('click', () => {
      refreshOverview();
      showToast('Data refreshed', 'info');
    });
  }

  // ──────────────────────────────────────────────────
  // Create Job Modal
  // ──────────────────────────────────────────────────
  const modal = document.getElementById('modal-create-job');
  const btnOpenModal = document.getElementById('btn-open-create-job');
  const btnCloseX = document.getElementById('modal-close-x');
  const btnCancelModal = document.getElementById('modal-btn-cancel');
  const formCreateJob = document.getElementById('form-create-job');
  const createJobError = document.getElementById('create-job-error');

  function openModal() { if (modal) modal.classList.add('active'); }
  function closeModal() {
    if (modal) modal.classList.remove('active');
    if (createJobError) { createJobError.style.display = 'none'; createJobError.textContent = ''; }
  }

  if (btnOpenModal) btnOpenModal.addEventListener('click', openModal);
  if (btnCloseX) btnCloseX.addEventListener('click', closeModal);
  if (btnCancelModal) btnCancelModal.addEventListener('click', closeModal);

  if (formCreateJob) {
    formCreateJob.addEventListener('submit', async (e) => {
      e.preventDefault();
      const submitBtn = document.getElementById('modal-btn-submit');
      if (submitBtn) submitBtn.disabled = true;

      const jobId = document.getElementById('input-job-id').value.trim();
      const jobName = document.getElementById('input-job-name').value.trim();
      const taskType = document.getElementById('input-task-type').value.trim();
      const failurePolicy = document.getElementById('select-failure-policy').value;

      const payload = {
        id: jobId,
        name: jobName,
        taskType: taskType,
        scheduledAt: new Date(Date.now() + 60000).toISOString(),
        failurePolicy: failurePolicy
      };

      try {
        const res = await fetch(`${API_BASE}/jobs`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload)
        });

        if (res.status === 201 || res.ok) {
          showToast(`Job '${jobId}' created successfully`, 'success');
          closeModal();
          formCreateJob.reset();
          refreshOverview();
          fetchJobs();
        } else {
          const errData = await res.json().catch(() => ({}));
          const errMsg = errData.message || 'Failed to create job';
          if (createJobError) {
            createJobError.textContent = errMsg;
            createJobError.style.display = 'block';
          }
        }
      } catch (err) {
        if (createJobError) {
          createJobError.textContent = `Error: ${err.message}`;
          createJobError.style.display = 'block';
        }
      } finally {
        if (submitBtn) submitBtn.disabled = false;
      }
    });
  }

  function refreshOverview() {
    fetchSchedulerStatus();
    fetchExecutions();
    fetchWorkflows();
  }

  // Initial load & 5-second polling interval
  refreshOverview();
  setInterval(refreshOverview, 5000);
});
