import {
  adminCellText,
  buildAdminDetailRows,
  buildPhotoPreview,
  normalizeAdminColumns,
  parseRelatedPeople,
} from './admin-helpers.js';
import { apiUrl, normalizeApiPerson } from './api.js';

const { createApp } = window.Vue;

const emptyForm = () => ({
  task: '人员架构',
  name: '',
  idNumber: '',
  gender: '男',
  age: 18,
  district: '',
  locality: '',
  risk: '一般参与',
  address: '',
  visits: 0,
  phone: '',
  amount: '',
  occupation: '',
  behavior: '',
  otherInvestment: '',
  relatedPerson: '',
  policeWarning: '无',
  visitDetail: '',
  latestNote: '',
});

createApp({
  data() {
    return {
      filters: {
        task: '人员架构',
        name: '',
        idNumber: '',
        gender: '',
        age: undefined,
      },
      people: [],
      columns: [],
      total: 0,
      page: 1,
      size: 20,
      loading: false,
      loadError: '',
      formOpen: false,
      editingId: '',
      form: emptyForm(),
      profileOpen: false,
      relationOpen: false,
      photoOpen: false,
      dataImportOpen: false,
      dataImportFile: null,
      dataImporting: false,
      fundImportOpen: false,
      fundImportFile: null,
      fundImporting: false,
      photoImportOpen: false,
      photoImportFile: null,
      fundGraphOpen: false,
      fundRelationLoading: false,
      fundRelationHeaders: [],
      fundRelationRows: [],
      fundRelationTotal: 0,
      fundRelationGraph: { nodes: [], edges: [], width: 760, height: 380 },
      activePerson: null,
    };
  },
  computed: {
    detailRows() {
      return buildAdminDetailRows(this.activePerson || {}, {
        hasFundHiddenInvestor: this.activePerson?.fundHiddenInvestor === true || this.hasFundHiddenInvestor,
      });
    },
    relationRows() {
      const source = this.activePerson?.relatedPerson || this.activePerson?.relatedPersonInfo || '';
      return parseRelatedPeople(source);
    },
    photoPreview() {
      return buildPhotoPreview(this.activePerson || {});
    },
    tableColumns() {
      return this.columns;
    },
    fundGraphViewBox() {
      const graph = this.fundRelationGraph || {};
      const nodes = graph.nodes || [];
      const maxX = nodes.reduce((value, node) => Math.max(value, Number(node.x || 0) + 60), Number(graph.width || 760));
      const maxY = nodes.reduce((value, node) => Math.max(value, Number(node.y || 0) + 60), Number(graph.height || 380));
      return `0 0 ${Math.max(760, maxX)} ${Math.max(380, maxY)}`;
    },
    fundGraphNodes() {
      return this.fundRelationGraph?.nodes || [];
    },
    fundGraphEdges() {
      return this.fundRelationGraph?.edges || [];
    },
    hasFundHiddenInvestor() {
      return this.fundGraphEdges.length > 0
        || this.fundGraphNodes.some((node) => String(node?.primary) !== 'true');
    },
  },
  mounted() {
    this.loadPeople();
  },
  methods: {
    async loadPeople(options = {}) {
      this.loading = true;
      this.loadError = '';
      const params = new URLSearchParams({
        page: String(this.page),
        size: String(this.size),
      });
      for (const [key, value] of Object.entries(this.filters)) {
        if (value !== undefined && value !== null && value !== '') params.set(key, value);
      }
      try {
        const response = await this.fetchWithFallback(`/api/admin/people?${params.toString()}`);
        if (!response.ok) throw new Error(`人员接口返回 ${response.status}`);
        const payload = await response.json();
        this.people = (payload.rows || []).map(normalizeApiPerson);
        this.columns = normalizeAdminColumns(payload.headers || []);
        this.total = payload.total || 0;
        if (!this.people.length && !options.retrying && this.hasActiveFilters()) {
          this.filters = { task: '', name: '', idNumber: '', gender: '', age: undefined };
          this.page = 1;
          await this.loadPeople({ retrying: true });
        }
      } catch (error) {
        this.loadError = error?.message || '人员数据加载失败';
        this.people = [];
        this.total = 0;
        window.ElementPlus?.ElMessage?.error(`人员数据加载失败：${this.loadError}`);
      } finally {
        this.loading = false;
      }
    },
    async fetchWithFallback(path, options = {}) {
      const primary = apiUrl(path);
      try {
        return await fetch(primary, { cache: 'no-store', ...options });
      } catch (error) {
        if (!primary.includes('localhost')) throw error;
        const fallback = primary.replace('http://localhost:', 'http://127.0.0.1:');
        return fetch(fallback, { cache: 'no-store', ...options });
      }
    },
    hasActiveFilters() {
      return Object.values(this.filters).some((value) => value !== undefined && value !== null && value !== '');
    },
    resetFilters() {
      this.filters = {
        task: '人员架构',
        name: '',
        idNumber: '',
        gender: '',
        age: undefined,
      };
      this.page = 1;
      this.loadPeople();
    },
    openCreate() {
      this.editingId = '';
      this.form = emptyForm();
      this.formOpen = true;
    },
    openEdit(row) {
      this.editingId = row.id;
      this.form = { ...emptyForm(), ...row, task: row.task || '人员架构' };
      this.formOpen = true;
    },
    async savePerson() {
      const url = this.editingId ? `/api/admin/people/${this.editingId}` : '/api/admin/people';
      const method = this.editingId ? 'PUT' : 'POST';
      const response = await fetch(apiUrl(url), {
        method,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(this.form),
      });
      if (!response.ok) {
        window.ElementPlus.ElMessage.error('保存失败');
        return;
      }
      window.ElementPlus.ElMessage.success('保存成功');
      this.formOpen = false;
      this.loadPeople();
    },
    async removePerson(row) {
      try {
        await window.ElementPlus.ElMessageBox.confirm(`确认删除 ${row.name}？`, '删除人员', {
          confirmButtonText: '删除',
          cancelButtonText: '取消',
          type: 'warning',
        });
      } catch {
        return;
      }
      const response = await fetch(apiUrl(`/api/admin/people/${row.id}`), { method: 'DELETE' });
      if (!response.ok) {
        window.ElementPlus.ElMessage.error('删除失败');
        return;
      }
      window.ElementPlus.ElMessage.success('删除成功');
      this.loadPeople();
    },
    openProfile(row) {
      this.openFunds(row);
    },
    async openFunds(row) {
      this.activePerson = row;
      this.profileOpen = true;
      this.fundGraphOpen = false;
      await this.loadFundRelations(row);
    },
    openRelations(row) {
      this.activePerson = row;
      this.relationOpen = true;
    },
    openPhoto(row) {
      this.activePerson = row;
      this.photoOpen = true;
    },
    async openGraphNodeProfile(node) {
      const identity = graphNodeIdentity(node);
      if (!identity.name && !identity.idNumber) return;
      const matchedPerson = await this.findPersonByGraphIdentity(identity);
      const person = matchedPerson || graphNodePerson(node, identity);
      await this.openFunds(person);
    },
    async findPersonByGraphIdentity(identity) {
      const params = new URLSearchParams({ page: '1', size: '1' });
      if (identity.idNumber) params.set('idNumber', identity.idNumber);
      else if (identity.name) params.set('name', identity.name);
      try {
        const response = await fetch(apiUrl(`/api/admin/people?${params.toString()}`), { cache: 'no-store' });
        if (!response.ok) return null;
        const payload = await response.json();
        const row = (payload.rows || [])[0];
        return row ? normalizeApiPerson(row) : null;
      } catch {
        return null;
      }
    },
    cellText(row, column) {
      return adminCellText(row, column);
    },
    openDataImport() {
      this.dataImportFile = null;
      this.dataImportOpen = true;
    },
    handleDataImportChange(file) {
      this.dataImportFile = file?.raw || file || null;
    },
    clearDataImport() {
      this.dataImportFile = null;
    },
    async confirmDataImport() {
      if (!this.dataImportFile) {
        window.ElementPlus.ElMessage.info('请选择 Excel 文件');
        return;
      }
      const body = new FormData();
      body.append('file', this.dataImportFile);
      this.dataImporting = true;
      try {
        const response = await fetch(apiUrl('/api/admin/people/import-excel'), {
          method: 'POST',
          body,
        });
        if (!response.ok) {
          window.ElementPlus.ElMessage.error('导入失败');
          return;
        }
        const result = await response.json();
        window.ElementPlus.ElMessage.success(`导入成功，更新 ${result.imported || 0} 条人员数据`);
        this.dataImportOpen = false;
        this.page = 1;
        this.loadPeople();
      } finally {
        this.dataImporting = false;
      }
    },
    openFundImport() {
      this.fundImportFile = null;
      this.fundImportOpen = true;
    },
    handleFundImportChange(file) {
      this.fundImportFile = file?.raw || file || null;
    },
    clearFundImport() {
      this.fundImportFile = null;
    },
    async confirmFundImport() {
      if (!this.fundImportFile) {
        window.ElementPlus.ElMessage.info('请选择资金关系 Excel 文件');
        return;
      }
      const body = new FormData();
      body.append('file', this.fundImportFile);
      this.fundImporting = true;
      try {
        const response = await fetch(apiUrl('/api/admin/fund-relations/import-excel'), {
          method: 'POST',
          body,
        });
        if (!response.ok) {
          window.ElementPlus.ElMessage.error('资金关系导入失败');
          return;
        }
        const result = await response.json();
        window.ElementPlus.ElMessage.success(`导入成功，更新 ${result.imported || 0} 条资金关系`);
        this.fundImportOpen = false;
        if (this.activePerson) await this.loadFundRelations(this.activePerson);
      } finally {
        this.fundImporting = false;
      }
    },
    openPhotoImport() {
      this.photoImportFile = null;
      this.photoImportOpen = true;
    },
    handlePhotoImportChange(file) {
      this.photoImportFile = file?.raw || file || null;
    },
    clearPhotoImport() {
      this.photoImportFile = null;
    },
    confirmPhotoImport() {
      if (!this.photoImportFile) {
        window.ElementPlus.ElMessage.info('请选择照片压缩包');
        return;
      }
      window.ElementPlus.ElMessage.info('照片导入接口待接入，当前照片由配置目录自动读取');
      this.photoImportOpen = false;
    },
    async loadFundRelations(row) {
      if (!row?.id) {
        this.fundRelationHeaders = [];
        this.fundRelationRows = [];
        this.fundRelationTotal = 0;
        this.fundRelationGraph = { nodes: [], edges: [], width: 760, height: 380 };
        return;
      }
      this.fundRelationLoading = true;
      try {
        const response = await fetch(apiUrl(fundRelationPath(row)), { cache: 'no-store' });
        if (!response.ok) {
          this.fundRelationHeaders = [];
          this.fundRelationRows = [];
          this.fundRelationTotal = 0;
          this.fundRelationGraph = { nodes: [], edges: [], width: 760, height: 380 };
          return;
        }
        const payload = await response.json();
        this.fundRelationHeaders = payload.headers || [];
        this.fundRelationRows = payload.rows || [];
        this.fundRelationTotal = payload.total || 0;
        this.fundRelationGraph = payload.graph || { nodes: [], edges: [], width: 760, height: 380 };
        this.applyFundHiddenInvestor(row, graphHasLowerInvestor(this.fundRelationGraph));
      } finally {
        this.fundRelationLoading = false;
      }
    },
    applyFundHiddenInvestor(row, hasLowerInvestor) {
      if (!row?.id || this.activePerson?.id !== row.id) return;
      this.activePerson = {
        ...this.activePerson,
        fundHiddenInvestor: hasLowerInvestor,
        hiddenInvestor: hasLowerInvestor ? '有' : '无',
      };
    },
    fundNodeTransform(node) {
      return `translate(${node.x || 0} ${node.y || 0})`;
    },
    fundNodeAmount(node) {
      const sourceEdge = this.fundGraphEdges.find((edge) => edge.source === node.id);
      return formatFundNodeAmount(sourceEdge?.amount);
    },
    fundNodeLevelRank(node) {
      return fundNodeLevelRank(node);
    },
    fundEdgePath(edge) {
      const source = this.fundGraphNodes.find((node) => node.id === edge.source);
      const target = this.fundGraphNodes.find((node) => node.id === edge.target);
      if (!source || !target) return '';
      const midY = Math.round(((source.y || 0) + (target.y || 0)) / 2);
      return `M ${source.x} ${source.y - 28} C ${source.x} ${midY}, ${target.x} ${midY}, ${target.x} ${target.y + 34}`;
    },
    async openFundGraph(row = this.activePerson) {
      this.activePerson = row;
      this.fundGraphOpen = true;
      await this.loadFundRelations(row);
    },
    goDashboard() {
      window.location.href = 'index.html';
    },
  },
}).use(window.ElementPlus).mount('#adminApp');

function formatFundNodeAmount(amount) {
  const text = String(amount ?? '').trim();
  if (!text || text === '未填写') return '';
  if (text.startsWith('向上层投资金额：') && text.endsWith('万')) return text;
  const numeric = Number(text.replaceAll(',', '').replace(/[^\d.-]/g, ''));
  if (!Number.isFinite(numeric)) return `向上层投资金额：${text}`;
  const amountInWan = text.includes('万') ? numeric : numeric / 10000;
  return `向上层投资金额：${Math.round(amountInWan)}万`;
}

function fundNodeLevelRank(node = {}) {
  if (node.primary || node.level === '显名投资人' || node.level === '当前人员') return 0;
  const level = String(node.level || '');
  if (level.includes('一')) return 1;
  if (level.includes('二')) return 2;
  if (level.includes('三')) return 3;
  if (level.includes('四')) return 4;
  return 5;
}

function fundRelationPath(person = {}) {
  if (person.fundIdentity) {
    const params = new URLSearchParams();
    if (person.fundIdentity.idNumber) params.set('idNumber', person.fundIdentity.idNumber);
    if (person.fundIdentity.name) params.set('name', person.fundIdentity.name);
    return `/api/admin/fund-relations/identity?${params.toString()}`;
  }
  return `/api/admin/fund-relations/person/${person.id}`;
}

function graphHasLowerInvestor(graph = {}) {
  const edges = Array.isArray(graph.edges) ? graph.edges : [];
  const nodes = Array.isArray(graph.nodes) ? graph.nodes : [];
  return edges.length > 0 || nodes.some((node) => String(node?.primary) !== 'true');
}

function graphNodeIdentity(node = {}) {
  const id = String(node.id || '');
  return {
    idNumber: id.startsWith('id-') ? id.slice(3) : '',
    name: String(node.fullLabel || node.label || '').trim(),
  };
}

function graphNodePerson(node = {}, identity = graphNodeIdentity(node)) {
  return normalizeApiPerson({
    id: `fund-${identity.idNumber || identity.name || node.id || Date.now()}`,
    name: identity.name || '资金关系人',
    idNumber: identity.idNumber,
    gender: '未填写',
    age: 0,
    nation: '未填写',
    risk: node.level || '资金关系人',
    group: 'hidden',
    amount: '未填写',
    occupation: '未填写',
    behavior: '资金关系图谱人员',
    visits: 0,
    policeStation: '未填写',
    district: '未填写',
    locality: '未填写',
    hiddenInvestor: '无',
    fundIdentity: identity,
  });
}
