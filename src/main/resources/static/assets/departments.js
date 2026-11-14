'use strict';

(() => {
  const byId = (id) => document.getElementById(id);
  const state = { departments: [], available: false, loading: false, busy: false, editing: null, deleting: null };
  const formDialog = byId('department-dialog');
  const deleteDialog = byId('delete-dialog');
  const form = byId('department-form');
  let toastTimer;

  async function request(path, options = {}) {
    let response;
    try {
      response = await fetch(path, { ...options, headers: { 'Content-Type': 'application/json', ...options.headers } });
    } catch { throw new Error('无法连接系统，请确认项目正在运行后重试。'); }
    let payload;
    try { payload = await response.json(); } catch { throw new Error('服务器响应异常，请稍后刷新重试。'); }
    if (!response.ok || payload.code !== 0) {
      throw new Error(response.status === 404 && path === '/api/departments'
        ? '当前未提供科室接口，请以 mysql 模式启动项目后重试。'
        : payload.message || '操作失败，请稍后重试。');
    }
    return payload.data;
  }

  function showToast(message, error = false) {
    clearTimeout(toastTimer);
    const toast = byId('toast');
    toast.textContent = message;
    toast.classList.toggle('error', error);
    toast.hidden = false;
    toastTimer = setTimeout(() => { toast.hidden = true; }, 4500);
  }

  function setError(element, message = '') {
    element.textContent = message;
    element.hidden = !message;
  }

  function updateButtons() {
    const locked = state.busy || state.loading;
    byId('create-button').disabled = locked || !state.available;
    byId('refresh-button').disabled = locked;
    document.querySelectorAll('.row-actions button').forEach((button) => { button.disabled = locked; });
    ['save-button', 'cancel-form', 'close-form', 'cancel-delete', 'confirm-delete'].forEach((id) => {
      byId(id).disabled = state.busy;
    });
    byId('save-button').textContent = state.busy && formDialog.open ? '正在保存…' : '保存科室';
    byId('confirm-delete').textContent = state.busy && deleteDialog.open ? '正在删除…' : '确认删除';
  }

  function textCell(text, className) {
    const cell = document.createElement('td');
    cell.textContent = text;
    if (className) cell.className = className;
    return cell;
  }

  function render() {
    const keyword = byId('search-input').value.trim().toLocaleLowerCase();
    const departments = state.departments.filter((item) =>
      item.name.toLocaleLowerCase().includes(keyword) || item.code.toLocaleLowerCase().includes(keyword));
    byId('total-count').textContent = state.available ? String(state.departments.length) : '—';
    byId('list-count').textContent = String(state.departments.length);
    byId('footer-count').textContent = String(state.departments.length);
    byId('result-count').textContent = state.loading ? '正在加载科室' : '显示 ' + departments.length + ' 个科室';
    const body = byId('department-rows');
    body.replaceChildren();
    if (departments.length === 0) {
      const row = document.createElement('tr');
      const cell = textCell(state.loading ? '正在加载科室…' : !state.available ? '科室暂不可用，请查看上方提示并刷新重试。' : keyword ? '没有找到匹配的科室，请尝试其他名称或编码。' : '暂无科室，点击“新增科室”创建第一条记录。', 'empty-cell');
      cell.colSpan = 5;
      row.append(cell);
      body.append(row);
    }
    departments.forEach((department, index) => {
      const row = document.createElement('tr');
      row.append(textCell(String(index + 1).padStart(2, '0')), textCell(department.name, 'department-name'));
      const codeCell = document.createElement('td');
      const code = document.createElement('span');
      code.className = 'code-badge';
      code.textContent = department.code;
      codeCell.append(code);
      row.append(codeCell, textCell(department.description || '暂无简介', 'description-cell'));
      const actions = document.createElement('td');
      const group = document.createElement('div');
      group.className = 'row-actions';
      const edit = document.createElement('button');
      edit.type = 'button';
      edit.className = 'text-button';
      edit.textContent = '编辑';
      edit.setAttribute('aria-label', '编辑' + department.name);
      edit.addEventListener('click', () => openForm(department));
      const divider = document.createElement('span');
      divider.className = 'action-divider';
      divider.setAttribute('aria-hidden', 'true');
      const remove = document.createElement('button');
      remove.type = 'button';
      remove.className = 'text-button delete';
      remove.textContent = '删除';
      remove.setAttribute('aria-label', '删除' + department.name);
      remove.addEventListener('click', () => openDelete(department));
      group.append(edit, divider, remove);
      actions.append(group);
      row.append(actions);
      body.append(row);
    });
    updateButtons();
  }

  async function checkDatabase() {
    try {
      const result = await request('/api/system/database');
      if (!result.connected) throw new Error('数据库连接未确认');
      byId('database-status').textContent = '已连接';
      byId('database-caption').textContent = 'MySQL · 连接正常';
    } catch {
      byId('database-status').textContent = '未连接';
      byId('database-caption').textContent = '请检查运行模式或数据库配置';
    }
  }

  async function loadDepartments() {
    state.loading = true;
    render();
    setError(byId('load-error'));
    try {
      state.departments = await request('/api/departments');
      state.available = true;
      byId('refresh-time').textContent = new Date().toLocaleTimeString('zh-CN', { hour12: false });
      return true;
    } catch (error) {
      state.available = false;
      state.departments = [];
      setError(byId('load-error'), error.message);
      return false;
    } finally {
      state.loading = false;
      render();
    }
  }

  function openForm(department = null) {
    if (state.busy || state.loading) return;
    state.editing = department;
    form.reset();
    setError(byId('form-error'));
    byId('dialog-title').textContent = department ? '编辑科室' : '新增科室';
    byId('department-name').value = department ? department.name : '';
    byId('department-code').value = department ? department.code : '';
    byId('department-description').value = department ? department.description || '' : '';
    formDialog.showModal();
    byId('department-name').focus();
  }

  function closeForm() { if (!state.busy) formDialog.close(); }
  function closeDelete() { if (!state.busy) deleteDialog.close(); }

  function openDelete(department) {
    if (state.busy || state.loading) return;
    state.deleting = department;
    setError(byId('delete-error'));
    byId('delete-description').textContent = '即将删除“' + department.name + '”（' + department.code + '）。';
    deleteDialog.showModal();
    byId('cancel-delete').focus();
  }

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    if (state.busy) return;
    const payload = {
      name: byId('department-name').value.trim(),
      code: byId('department-code').value.trim().toUpperCase(),
      description: byId('department-description').value.trim()
    };
    if (!payload.name) {
      setError(byId('form-error'), '请输入科室名称，不能只输入空格。');
      byId('department-name').focus();
      return;
    }
    if (!form.reportValidity()) return;
    state.busy = true;
    updateButtons();
    setError(byId('form-error'));
    const editing = state.editing;
    try {
      await request('/api/departments' + (editing ? '/' + editing.id : ''), {
        method: editing ? 'PUT' : 'POST', body: JSON.stringify(payload)
      });
      formDialog.close();
      const refreshed = await loadDepartments();
      showToast(refreshed ? (editing ? '科室信息已更新' : '科室已新增') : '已保存，但列表刷新失败，请点击刷新列表。', !refreshed);
    } catch (error) { setError(byId('form-error'), error.message); }
    finally { state.busy = false; updateButtons(); }
  });

  byId('confirm-delete').addEventListener('click', async () => {
    if (state.busy || !state.deleting) return;
    state.busy = true;
    updateButtons();
    setError(byId('delete-error'));
    try {
      await request('/api/departments/' + state.deleting.id, { method: 'DELETE' });
      deleteDialog.close();
      const refreshed = await loadDepartments();
      showToast(refreshed ? '科室已删除' : '已删除，但列表刷新失败，请点击刷新列表。', !refreshed);
    } catch (error) { setError(byId('delete-error'), error.message); }
    finally { state.busy = false; updateButtons(); }
  });

  byId('create-button').addEventListener('click', () => openForm());
  byId('refresh-button').addEventListener('click', () => { loadDepartments(); checkDatabase(); });
  byId('search-input').addEventListener('input', render);
  byId('cancel-form').addEventListener('click', closeForm);
  byId('close-form').addEventListener('click', closeForm);
  byId('cancel-delete').addEventListener('click', closeDelete);
  byId('department-code').addEventListener('blur', () => {
    byId('department-code').value = byId('department-code').value.trim().toUpperCase();
  });
  [formDialog, deleteDialog].forEach((dialog) => {
    dialog.addEventListener('cancel', (event) => { if (state.busy) event.preventDefault(); });
  });
  loadDepartments();
  checkDatabase();
})();
