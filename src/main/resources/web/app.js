/**
 * Job Scheduler Console - Vanilla JS Controller
 * Version: 8.0.0
 *
 * All operational data is fetched from the V8 REST API.
 * Zero hardcoded/dummy scheduler, job, or execution data.
 */

document.addEventListener('DOMContentLoaded', () => {
  const API_BASE = '/api/v1';

  // ──────────────────────────────────────────────────
  // Navigation Logic
  // ──────────────────────────────────────────────────
  const navItems = document.querySelectorAll('.nav-item');
  const viewPanels = document.querySelectorAll('.view-panel');

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
      switchView(view);
      window.location.hash = view;
    });
  });

  // View-all links in overview panels
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

  // Handle Hash Routing
  const currentHash = window.location.hash.replace('#', '') || 'overview';
  switchView(currentHash);

  // ──────────────────────────────────────────────────
  // Greeting (time-of-day based, no weather)
  // ──────────────────────────────────────────────────
  function updateGreeting() {
    const hour = new Date().getHours();
    const el = document.getElementById('greeting-text');
    if (!el) return;
    if (hour < 12) el.textContent = 'Good morning,';
    else if (hour < 17) el.textContent = 'Good afternoon,';
    else el.textContent = 'Good evening,';
  }
  updateGreeting();

  // ──────────────────────────────────────────────────
  // Toast Notification System (replaces alert())
  // ──────────────────────────────────────────────────
  function showToast(message, type = 'info') {
    const container = document.getElementById('toast-container');
    if (!container) return;
    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    toast.textContent = message;
    container.appendChild(toast);
    requestAnimationFrame(() => toast.classList.add('show'));
    setTimeout(() => {
      toast.classList.remove('show');
      setTimeout(() => toast.remove(), 300);
    }, 4000);
  }

  // ──────────────────────────────────────────────────
  // Connection state tracking
  // ──────────────────────────────────────────────────
  let isConnected = false;

  function setConnectionState(connected) {
    isConnected = connected;
    const dot = document.getElementById('sidebar-status-dot');
    const text = document.getElementById('sidebar-status-text');
    if (dot) {
      dot.className = 'status-dot ' + (connected ? 'green' : 'red');
    }
    if (text) {
      text.textContent = connected ? 'Connected' : 'Disconnected';
    }
  }

  // ──────────────────────────────────────────────────
  // Scheduler Status Fetch
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

      // The DTO field is "status" (not "state")
      const status = data.status || 'UNKNOWN';

      // Topbar status pill
      const pillText = document.getElementById('topbar-status-text');
      const pill = document.getElementById('topbar-status-pill');
      if (pillText) pillText.textContent = `Scheduler ${status}`;
      if (pill) pill.className = `scheduler-pill ${status.toLowerCase()}`;

      // Sidebar version
      const versionEl = document.getElementById('sidebar-version-text');
      if (versionEl && data.version) versionEl.textContent = `${data.version} (${data.environment || 'dev'})`;

      // Overview control panel
      const ctrlTitle = document.getElementById('ctrl-status-title');
      const ctrlDot = document.getElementById('ctrl-dot');
      const ctrlSub = document.getElementById('ctrl-status-sub');
      const btnHalt = document.getElementById('btn-halt-scheduler');
      const btnResume = document.getElementById('btn-resume-scheduler');

      if (ctrlTitle) ctrlTitle.textContent = status;
      if (ctrlDot) {
        ctrlDot.className = 'dot-lg ' + getStatusDotClass(status);
      }
      if (ctrlSub) {
        if (status === 'RUNNING') ctrlSub.textContent = 'Jobs are being dispatched and executed.';
        else if (status === 'HALTED') ctrlSub.textContent = 'Scheduler is halted. New jobs will not be dispatched.';
        else if (status === 'STOPPED') ctrlSub.textContent = 'Scheduler has been shut down.';
        else ctrlSub.textContent = `Current state: ${status}`;
      }

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

      // Specs
      setTextById('ctrl-threads', `${data.activeWorkerThreads} active / ${data.totalWorkerThreads}`);
      setTextById('ctrl-queue', `${data.pendingQueueSize} pending`);
      setTextById('ctrl-uptime', data.uptime || '—');

      // Overview metrics from scheduler status
      setTextById('stat-total-jobs', data.totalJobs);
      setTextById('stat-running-exec', data.runningExecutions);
      setTextById('stat-scheduled-exec', data.scheduledExecutions);
      setTextById('stat-blocked-exec', data.blockedExecutions);
      setTextById('stat-failed-exec', data.failedExecutions);

      // Banner headline
      const headline = document.getElementById('banner-headline');
      if (headline) {
        if (status === 'RUNNING') {
          headline.textContent = 'Scheduler Overview';
        } else if (status === 'HALTED') {
          headline.textContent = 'Scheduler Halted';
        } else if (status === 'STOPPED') {
          headline.textContent = 'Scheduler Stopped';
        } else {
          headline.textContent = `Scheduler: ${status}`;
        }
      }

      // System widget (replaces weather widget)
      setTextById('widget-env', data.environment || '—');
      setTextById('widget-db', data.dbStatus ? `${data.dbStatus} (${data.dbName || ''})` : '—');
      setTextById('widget-uptime', data.uptime || '—');

      // Donut chart data
      updateDonutChart(data);

      // Info cards
      setTextById('info-completed-count', data.completedExecutions);
      const dbStatusEl = document.getElementById('info-db-status');
      if (dbStatusEl) {
        dbStatusEl.innerHTML = `MongoDB <strong>${escapeHtml(data.dbStatus || 'Unknown')}</strong> (${escapeHtml(data.dbName || '—')})`;
      }

      // Scheduler admin view
      const adminDot = document.getElementById('admin-dot');
      const adminHeading = document.getElementById('admin-status-heading');
      const adminDesc = document.getElementById('admin-status-desc');
      if (adminDot) adminDot.className = 'dot-lg ' + getStatusDotClass(status);
      if (adminHeading) adminHeading.textContent = `State: ${status}`;
      if (adminDesc) {
        if (status === 'RUNNING') adminDesc.textContent = 'The scheduler is actively polling the PriorityQueue and dispatching jobs to worker threads.';
        else if (status === 'HALTED') adminDesc.textContent = 'The scheduler has been halted. No new jobs will be dispatched until resumed.';
        else if (status === 'STOPPED') adminDesc.textContent = 'The scheduler has been shut down completely.';
        else adminDesc.textContent = `Scheduler is in ${status} state.`;
      }

      setTextById('admin-spec-status', status);
      setTextById('admin-spec-threads', `${data.activeWorkerThreads} / ${data.totalWorkerThreads}`);
      setTextById('admin-spec-queue', `${data.pendingQueueSize}`);
      setTextById('admin-spec-jobs', `${data.totalJobs}`);
      setTextById('admin-spec-active', `${data.activeExecutions}`);
      setTextById('admin-spec-completed', `${data.completedExecutions}`);
      setTextById('admin-spec-failed', `${data.failedExecutions}`);
      setTextById('admin-spec-uptime', data.uptime || '—');
      setTextById('admin-spec-version', data.version || '—');
      setTextById('admin-spec-env', data.environment || '—');
      setTextById('admin-spec-db', data.dbStatus ? `${data.dbStatus} (${data.dbName || ''})` : '—');

      // Admin button visibility
      const adminBtnHalt = document.getElementById('admin-btn-halt');
      const adminBtnResume = document.getElementById('admin-btn-resume');
      const adminBtnShutdown = document.getElementById('admin-btn-shutdown');
      if (adminBtnHalt) adminBtnHalt.disabled = (status !== 'RUNNING');
      if (adminBtnResume) adminBtnResume.disabled = (status !== 'HALTED');
      if (adminBtnShutdown) adminBtnShutdown.disabled = (status === 'STOPPED');

      return data;
    } catch (err) {
      console.error('Failed to fetch scheduler status:', err);
      setConnectionState(false);
      return null;
    }
  }

  // ──────────────────────────────────────────────────
  // Donut Chart (driven by scheduler status data)
  // ──────────────────────────────────────────────────
  function updateDonutChart(data) {
    const total = data.activeExecutions + data.completedExecutions + data.failedExecutions;
    setTextById('donut-total', total);

    const svg = document.getElementById('donut-svg');
    if (!svg) return;

    // Remove old arcs
    svg.querySelectorAll('.circle-arc').forEach(el => el.remove());

    const segments = [
      { value: data.runningExecutions, cls: 'green' },
      { value: data.completedExecutions, cls: 'blue' },
      { value: data.scheduledExecutions, cls: 'blue-alt' },
      { value: data.blockedExecutions, cls: 'amber' },
      { value: data.failedExecutions, cls: 'red' },
    ];

    let offset = 0;
    segments.forEach(seg => {
      const pct = total > 0 ? (seg.value / total) * 100 : 0;
      if (pct > 0) {
        const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
        path.setAttribute('class', `circle-arc circle-${seg.cls}`);
        path.setAttribute('d', 'M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831');
        path.setAttribute('fill', 'none');
        path.setAttribute('stroke-width', '3.8');
        path.setAttribute('stroke-linecap', 'round');
        path.setAttribute('stroke-dasharray', `${pct}, 100`);
        path.setAttribute('stroke-dashoffset', `-${offset}`);
        svg.appendChild(path);
      }
      offset += pct;
    });

    // Update legend
    setTextById('legend-running', data.runningExecutions);
    setTextById('legend-completed', data.completedExecutions);
    setTextById('legend-scheduled', data.scheduledExecutions);
    setTextById('legend-blocked', data.blockedExecutions);
    setTextById('legend-failed', data.failedExecutions);
  }

  // ──────────────────────────────────────────────────
  // Jobs Fetch
  // ──────────────────────────────────────────────────
  async function fetchJobs() {
    const jobsTbody = document.getElementById('table-jobs-body');
    if (jobsTbody) jobsTbody.innerHTML = '<tr><td colspan="6" class="empty-state"><span class="loading-spinner"></span> Loading jobs…</td></tr>';

    try {
      const res = await fetch(`${API_BASE}/jobs`);
      if (!res.ok) {
        if (jobsTbody) jobsTbody.innerHTML = '<tr><td colspan="6" class="empty-state error-state">Failed to load jobs.</td></tr>';
        return;
      }
      const jobs = await res.json();

      if (jobsTbody) {
        if (jobs.length === 0) {
          jobsTbody.innerHTML = '<tr><td colspan="6" class="empty-state">No jobs registered yet. Click "Create Job" to add one.</td></tr>';
          return;
        }

        jobsTbody.innerHTML = jobs.map(job => `
          <tr>
            <td class="code">${escapeHtml(job.jobId || job.id)}</td>
            <td>${escapeHtml(job.jobName || job.name || job.jobId || job.id)}</td>
            <td>${escapeHtml(job.taskType)}</td>
            <td>${formatDateTime(job.scheduledAt)}</td>
            <td>${job.isRecurring && job.recurrence ? `<span class="type-tag recurring">↻ ${formatInterval(job.recurrence.intervalMs)}</span>` : '<span class="type-tag one-time">⊙ One-time</span>'}</td>
            <td>
              <button class="btn btn-secondary btn-sm cancel-job-btn" data-id="${escapeHtml(job.jobId || job.id)}">Cancel</button>
            </td>
          </tr>
        `).join('');

        // Attach cancel listeners
        document.querySelectorAll('.cancel-job-btn').forEach(btn => {
          btn.addEventListener('click', async () => {
            const jobId = btn.getAttribute('data-id');
            if (confirm(`Are you sure you want to cancel job "${jobId}"?`)) {
              await cancelJob(jobId);
            }
          });
        });
      }
    } catch (err) {
      console.error('Failed to fetch jobs:', err);
      if (jobsTbody) jobsTbody.innerHTML = '<tr><td colspan="6" class="empty-state error-state">Error loading jobs. Check connection.</td></tr>';
    }
  }

  async function cancelJob(jobId) {
    try {
      const res = await fetch(`${API_BASE}/jobs/${encodeURIComponent(jobId)}/cancel`, { method: 'POST' });
      if (res.ok) {
        showToast(`Job "${jobId}" cancelled successfully.`, 'success');
        fetchJobs();
        fetchSchedulerStatus();
      } else {
        const err = await res.json().catch(() => ({ message: 'Unknown error' }));
        showToast(`Failed to cancel job: ${err.message || 'Unknown error'}`, 'error');
      }
    } catch (err) {
      showToast(`Error cancelling job: ${err.message}`, 'error');
    }
  }

  // ──────────────────────────────────────────────────
  // Executions Fetch (with pagination)
  // ──────────────────────────────────────────────────
  let currentExecPage = 0;
  const EXEC_PAGE_SIZE = 20;

  async function fetchExecutions(page = 0) {
    currentExecPage = page;
    const execTbody = document.getElementById('table-executions-body');
    if (execTbody) execTbody.innerHTML = '<tr><td colspan="7" class="empty-state"><span class="loading-spinner"></span> Loading executions…</td></tr>';

    try {
      const res = await fetch(`${API_BASE}/executions?page=${page}&size=${EXEC_PAGE_SIZE}`);
      if (!res.ok) {
        if (execTbody) execTbody.innerHTML = '<tr><td colspan="7" class="empty-state error-state">Failed to load executions.</td></tr>';
        return;
      }
      const pageData = await res.json();
      // DTO field is "items" (not "content")
      const executions = pageData.items || [];

      if (execTbody) {
        if (executions.length === 0 && pageData.totalElements === 0) {
          execTbody.innerHTML = '<tr><td colspan="7" class="empty-state">No executions recorded yet.</td></tr>';
        } else {
          execTbody.innerHTML = executions.map(ex => {
            const duration = ex.durationMillis != null
              ? formatDuration(ex.durationMillis)
              : ex.status === 'RUNNING' ? 'Running…' : '—';

            return `
              <tr>
                <td class="code">${escapeHtml(ex.executionId)}</td>
                <td class="code">${escapeHtml(ex.jobId)}</td>
                <td>#${ex.occurrenceNumber}</td>
                <td>${formatDateTime(ex.scheduledAt)}</td>
                <td>${duration}</td>
                <td><span class="badge-status ${ex.status.toLowerCase()}">${ex.status}</span></td>
                <td>${ex.attemptCount} attempt(s)</td>
              </tr>
            `;
          }).join('');
        }
      }

      // Render pagination controls
      renderPagination(pageData);
    } catch (err) {
      console.error('Failed to fetch executions:', err);
      if (execTbody) execTbody.innerHTML = '<tr><td colspan="7" class="empty-state error-state">Error loading executions. Check connection.</td></tr>';
    }
  }

  function renderPagination(pageData) {
    const container = document.getElementById('exec-pagination');
    if (!container) return;

    if (pageData.totalPages <= 1) {
      container.innerHTML = pageData.totalElements > 0
        ? `<span class="pagination-info">${pageData.totalElements} execution(s)</span>`
        : '';
      return;
    }

    let html = `<span class="pagination-info">Page ${pageData.page + 1} of ${pageData.totalPages} (${pageData.totalElements} total)</span>`;
    html += '<div class="pagination-btns">';
    html += `<button class="btn btn-secondary btn-sm" ${pageData.page === 0 ? 'disabled' : ''} data-page="${pageData.page - 1}">← Prev</button>`;
    html += `<button class="btn btn-secondary btn-sm" ${pageData.page >= pageData.totalPages - 1 ? 'disabled' : ''} data-page="${pageData.page + 1}">Next →</button>`;
    html += '</div>';
    container.innerHTML = html;

    container.querySelectorAll('[data-page]').forEach(btn => {
      btn.addEventListener('click', () => {
        const p = parseInt(btn.getAttribute('data-page'), 10);
        if (!isNaN(p)) fetchExecutions(p);
      });
    });
  }

  // ──────────────────────────────────────────────────
  // Overview: Active & Recent Executions
  // ──────────────────────────────────────────────────
  async function fetchOverviewExecutions() {
    try {
      const res = await fetch(`${API_BASE}/executions?page=0&size=50`);
      if (!res.ok) return;
      const pageData = await res.json();
      const executions = pageData.items || [];

      // Active (Running) executions
      const activeTbody = document.getElementById('table-active-executions');
      if (activeTbody) {
        const running = executions.filter(e => e.status === 'RUNNING');
        if (running.length === 0) {
          activeTbody.innerHTML = '<tr><td colspan="5" class="empty-state">No active executions.</td></tr>';
        } else {
          activeTbody.innerHTML = running.map(ex => `
            <tr>
              <td class="code">${escapeHtml(ex.jobId)}</td>
              <td>${escapeHtml(ex.jobName || ex.jobId)}</td>
              <td>${formatDateTime(ex.startedAt || ex.scheduledAt)}</td>
              <td>${ex.durationMillis != null ? formatDuration(ex.durationMillis) : 'Running…'}</td>
              <td><span class="badge-status running">RUNNING</span></td>
            </tr>
          `).join('');
        }
      }

      // Recent executions (most recent, any status)
      const recentTbody = document.getElementById('table-recent-executions');
      if (recentTbody) {
        const recent = executions.slice(0, 5);
        if (recent.length === 0) {
          recentTbody.innerHTML = '<tr><td colspan="3" class="empty-state">No executions yet.</td></tr>';
        } else {
          recentTbody.innerHTML = recent.map(ex => {
            const duration = ex.durationMillis != null
              ? formatDuration(ex.durationMillis)
              : ex.status === 'RUNNING' ? 'Running…' : '—';
            return `
              <tr>
                <td class="code">${escapeHtml(ex.jobId)}</td>
                <td><span class="badge-status ${ex.status.toLowerCase()}">${ex.status}</span></td>
                <td>${duration}</td>
              </tr>
            `;
          }).join('');
        }
      }

      // Activity feed
      const activityFeed = document.getElementById('activity-feed');
      if (activityFeed) {
        if (executions.length === 0) {
          activityFeed.innerHTML = '<div class="empty-state-block">No recent activity. Jobs will appear here once executed.</div>';
        } else {
          const feedItems = executions.slice(0, 6);
          activityFeed.innerHTML = feedItems.map(ex => {
            const dotClass = ex.status === 'COMPLETED' ? 'green' : ex.status === 'RUNNING' ? 'blue' : ex.status === 'FAILED' ? 'red' : 'amber';
            const label = ex.status === 'COMPLETED' ? 'Job completed' : ex.status === 'RUNNING' ? 'Job started' : ex.status === 'FAILED' ? 'Job failed' : `Job ${ex.status.toLowerCase()}`;
            return `
              <div class="feed-item">
                <span class="feed-dot ${dotClass}"></span>
                <div class="feed-content">
                  <strong>${label}</strong>
                  <span>${escapeHtml(ex.jobId)}</span>
                </div>
                <span class="time">${formatDateTime(ex.completedAt || ex.startedAt || ex.scheduledAt)}</span>
              </div>
            `;
          }).join('');
        }
      }
    } catch (err) {
      console.error('Failed to fetch overview executions:', err);
      const activeTbody = document.getElementById('table-active-executions');
      if (activeTbody) activeTbody.innerHTML = '<tr><td colspan="5" class="empty-state error-state">Error loading data.</td></tr>';
      const recentTbody = document.getElementById('table-recent-executions');
      if (recentTbody) recentTbody.innerHTML = '<tr><td colspan="3" class="empty-state error-state">Error loading data.</td></tr>';
    }
  }

  // ──────────────────────────────────────────────────
  // Workflows Fetch
  // ──────────────────────────────────────────────────
  async function fetchWorkflows() {
    const container = document.getElementById('workflow-graph-container');
    if (!container) return;

    container.innerHTML = '<div class="empty-state"><span class="loading-spinner"></span> Loading workflow DAG…</div>';

    try {
      const res = await fetch(`${API_BASE}/jobs`);
      if (!res.ok) {
        container.innerHTML = '<div class="empty-state">Failed to load workflow data.</div>';
        return;
      }
      const jobs = await res.json();

      if (!jobs || jobs.length === 0) {
        container.innerHTML = '<div class="empty-state">No jobs registered. Workflows will appear when jobs are created.</div>';
        return;
      }

      // Map prerequisite dependencies and dependent jobs
      const depMap = new Map(); // jobId -> Array of prerequisite jobIds
      const dependentMap = new Map(); // jobId -> Set of jobIds depending on this job

      jobs.forEach(j => {
        const id = j.jobId || j.id;
        const deps = j.dependencyIds || [];
        depMap.set(id, deps);
        deps.forEach(depId => {
          if (!dependentMap.has(depId)) dependentMap.set(depId, new Set());
          dependentMap.get(depId).add(id);
        });
      });

      const independentJobs = [];
      const dependentJobs = [];

      jobs.forEach(j => {
        const id = j.jobId || j.id;
        const hasPrereqs = (depMap.get(id) && depMap.get(id).length > 0);
        const hasDependents = (dependentMap.has(id) && dependentMap.get(id).size > 0);

        if (!hasPrereqs && !hasDependents) {
          independentJobs.push(j);
        } else {
          dependentJobs.push(j);
        }
      });

      let html = '';

      // Render Dependency Chains (e.g. A -> B)
      if (dependentJobs.length > 0) {
        html += '<div class="dag-group"><div class="dag-section-title">Dependency DAG Workflows</div><div class="dag-list">';

        // Find root nodes of chains (jobs with no prerequisites but having dependents)
        const roots = dependentJobs.filter(j => {
          const id = j.jobId || j.id;
          const deps = depMap.get(id) || [];
          return deps.length === 0;
        });

        if (roots.length > 0) {
          roots.forEach(rootJob => {
            const rootId = rootJob.jobId || rootJob.id;
            const dependents = Array.from(dependentMap.get(rootId) || []);

            dependents.forEach(depId => {
              const childJob = jobs.find(j => (j.jobId || j.id) === depId);
              html += `
                <div class="dag-chain">
                  <div class="dag-node">
                    <span class="code">${escapeHtml(rootId)}</span>
                    <span class="dep-label">Prerequisite (${escapeHtml(rootJob.name || rootId)})</span>
                  </div>
                  <span class="dag-arrow">→</span>
                  <div class="dag-node">
                    <span class="code">${escapeHtml(depId)}</span>
                    <span class="dep-label">Dependent (${escapeHtml(childJob ? (childJob.name || depId) : depId)})</span>
                  </div>
                </div>
              `;
            });
          });
        } else {
          // Fallback for non-root dependency items
          dependentJobs.forEach(j => {
            const id = j.jobId || j.id;
            const deps = j.dependencyIds || [];
            html += `
              <div class="dag-chain">
                <div class="dag-node">
                  <span class="code">${escapeHtml(deps.join(', '))}</span>
                  <span class="dep-label">Prerequisite</span>
                </div>
                <span class="dag-arrow">→</span>
                <div class="dag-node">
                  <span class="code">${escapeHtml(id)}</span>
                  <span class="dep-label">Dependent</span>
                </div>
              </div>
            `;
          });
        }

        html += '</div></div>';
      }

      // Render Independent Jobs (e.g. R1)
      if (independentJobs.length > 0) {
        html += '<div class="dag-group" style="margin-top: 16px;"><div class="dag-section-title">Independent Schedules</div><div class="dag-list">';
        independentJobs.forEach(j => {
          const id = j.jobId || j.id;
          const recText = j.recurrencePolicy ? `${j.recurrencePolicy.type || 'RECURRING'}` : 'One-time';
          html += `
            <div class="dag-node">
              <span class="code">${escapeHtml(id)}</span>
              <span class="dep-label">${escapeHtml(j.name || id)} (${recText})</span>
            </div>
          `;
        });
        html += '</div></div>';
      }

      container.innerHTML = html;

    } catch (err) {
      container.innerHTML = `<div class="empty-state">Error loading workflows: ${escapeHtml(err.message)}</div>`;
    }
  }

  // ──────────────────────────────────────────────────
  // Create Job Modal Logic
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

  // Close modal on backdrop click
  if (modal) {
    modal.addEventListener('click', (e) => {
      if (e.target === modal) closeModal();
    });
  }

  if (formCreateJob) {
    formCreateJob.addEventListener('submit', async (e) => {
      e.preventDefault();
      const submitBtn = document.getElementById('modal-btn-submit');
      if (submitBtn) submitBtn.disabled = true;

      const jobId = document.getElementById('input-job-id').value.trim();
      const jobName = document.getElementById('input-job-name').value.trim();
      const taskType = document.getElementById('input-task-type').value.trim();
      const failurePolicy = document.getElementById('select-failure-policy').value;

      // CreateJobRequest DTO uses "id" and "name" (not "jobId" and "jobName")
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

        if (res.ok) {
          closeModal();
          formCreateJob.reset();
          showToast(`Job "${jobId}" created successfully.`, 'success');
          fetchJobs();
          fetchSchedulerStatus();
        } else {
          const err = await res.json().catch(() => ({ message: 'Unknown failure' }));
          if (createJobError) {
            createJobError.textContent = `Error: ${err.message || 'Unknown failure'}`;
            createJobError.style.display = 'block';
          }
        }
      } catch (err) {
        if (createJobError) {
          createJobError.textContent = `Network error: ${err.message}`;
          createJobError.style.display = 'block';
        }
      } finally {
        if (submitBtn) submitBtn.disabled = false;
      }
    });
  }

  // ──────────────────────────────────────────────────
  // Scheduler Administrative Controls
  // ──────────────────────────────────────────────────
  async function haltScheduler() {
    if (!confirm('Are you sure you want to HALT the scheduler? New job dispatch will be paused.')) return;
    try {
      const res = await fetch(`${API_BASE}/scheduler/halt`, { method: 'POST' });
      if (res.ok) {
        showToast('Scheduler halted.', 'warning');
        fetchSchedulerStatus();
      } else {
        showToast('Failed to halt scheduler.', 'error');
      }
    } catch (err) {
      showToast(`Error: ${err.message}`, 'error');
    }
  }

  async function resumeScheduler() {
    try {
      const res = await fetch(`${API_BASE}/scheduler/resume`, { method: 'POST' });
      if (res.ok) {
        showToast('Scheduler resumed.', 'success');
        fetchSchedulerStatus();
      } else {
        showToast('Failed to resume scheduler.', 'error');
      }
    } catch (err) {
      showToast(`Error: ${err.message}`, 'error');
    }
  }

  async function shutdownScheduler() {
    if (!confirm('CAUTION: Are you sure you want to SHUTDOWN the scheduler service? This cannot be undone from the console.')) return;
    try {
      const res = await fetch(`${API_BASE}/scheduler/shutdown`, { method: 'POST' });
      if (res.ok) {
        showToast('Scheduler shutdown initiated.', 'warning');
        fetchSchedulerStatus();
      } else {
        showToast('Failed to shutdown scheduler.', 'error');
      }
    } catch (err) {
      showToast(`Error: ${err.message}`, 'error');
    }
  }

  // Bind all control buttons
  const btnHalt = document.getElementById('btn-halt-scheduler');
  const btnResume = document.getElementById('btn-resume-scheduler');
  const adminBtnHalt = document.getElementById('admin-btn-halt');
  const adminBtnResume = document.getElementById('admin-btn-resume');
  const adminBtnShutdown = document.getElementById('admin-btn-shutdown');
  const btnRefresh = document.getElementById('btn-refresh-data');

  if (btnHalt) btnHalt.addEventListener('click', haltScheduler);
  if (btnResume) btnResume.addEventListener('click', resumeScheduler);
  if (adminBtnHalt) adminBtnHalt.addEventListener('click', haltScheduler);
  if (adminBtnResume) adminBtnResume.addEventListener('click', resumeScheduler);
  if (adminBtnShutdown) adminBtnShutdown.addEventListener('click', shutdownScheduler);
  if (btnRefresh) btnRefresh.addEventListener('click', () => {
    showToast('Refreshing…', 'info');
    refreshAll();
  });

  // ──────────────────────────────────────────────────
  // Global Search (client-side filter for jobs table)
  // ──────────────────────────────────────────────────
  const searchInput = document.getElementById('global-search');
  if (searchInput) {
    searchInput.addEventListener('input', () => {
      const query = searchInput.value.toLowerCase().trim();
      const rows = document.querySelectorAll('#table-jobs-body tr');
      rows.forEach(row => {
        if (!query) { row.style.display = ''; return; }
        row.style.display = row.textContent.toLowerCase().includes(query) ? '' : 'none';
      });
    });

    // Ctrl+K focus shortcut
    document.addEventListener('keydown', (e) => {
      if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
        e.preventDefault();
        searchInput.focus();
      }
    });
  }

  // ──────────────────────────────────────────────────
  // Utility Functions
  // ──────────────────────────────────────────────────
  function escapeHtml(str) {
    if (str == null) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  function setTextById(id, text) {
    const el = document.getElementById(id);
    if (el) el.textContent = text != null ? String(text) : '—';
  }

  function getStatusDotClass(status) {
    if (status === 'RUNNING') return 'green';
    if (status === 'HALTED') return 'amber';
    return 'red';
  }

  function formatDateTime(isoString) {
    if (!isoString) return '—';
    try {
      const d = new Date(isoString);
      if (isNaN(d.getTime())) return isoString;
      return d.toLocaleString();
    } catch {
      return isoString;
    }
  }

  function formatDuration(ms) {
    if (ms == null) return '—';
    if (ms < 1000) return `${ms}ms`;
    const s = Math.round(ms / 1000);
    if (s < 60) return `${s}s`;
    const m = Math.floor(s / 60);
    const rem = s % 60;
    return `${m}m ${rem}s`;
  }

  function formatInterval(ms) {
    if (!ms) return '—';
    const s = ms / 1000;
    if (s < 60) return `${s}s`;
    const m = s / 60;
    if (m < 60) return `${m}m`;
    const h = m / 60;
    return `${h}h`;
  }

  // ──────────────────────────────────────────────────
  // Refresh Functions
  // ──────────────────────────────────────────────────
  function refreshOverview() {
    fetchSchedulerStatus();
    fetchOverviewExecutions();
  }

  function refreshAll() {
    fetchSchedulerStatus();
    fetchOverviewExecutions();
    fetchJobs();
    fetchExecutions(currentExecPage);
  }

  // ──────────────────────────────────────────────────
  // Initial Load & Regular Polling
  // ──────────────────────────────────────────────────
  fetchSchedulerStatus();
  fetchOverviewExecutions();
  fetchJobs();

  // Poll scheduler status every 5 seconds
  setInterval(() => {
    fetchSchedulerStatus();
  }, 5000);

  // Poll overview executions every 10 seconds (when on overview)
  setInterval(() => {
    const overviewPanel = document.getElementById('view-overview');
    if (overviewPanel && overviewPanel.classList.contains('active')) {
      fetchOverviewExecutions();
    }
  }, 10000);
});
