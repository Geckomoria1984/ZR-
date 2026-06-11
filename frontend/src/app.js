import {
  people as fallbackPeople,
  groups as fallbackGroups,
  regionStats as fallbackRegionStats,
  regionRows as fallbackRegionRows,
  riskBars as fallbackRiskBars,
  clinicBars as fallbackClinicBars,
} from './data.js';
import { apiUrl, normalizeApiPerson } from './api.js';

export const SECTION_PAGE_SIZE = 5;
const AMOUNT_BUCKET_COLORS = ['#ff6e63', '#27d6ed', '#f35cae', '#32d2a3', '#ffd05d', '#8d7bff', '#60e6a8'];
const REGION_CHART_COLORS = ['#6f66cc', '#2fd583', '#ff735f', '#23cbdc', '#ef66b4', '#9be05d', '#ffd05d', '#8a7cff'];
const printableGroupKeys = ['organizers', 'responders', 'general', 'watch'];
const compactDrawerGroupKeys = ['general', 'watch'];
const HIDDEN_DASHBOARD_URL = 'index.html?scope=hidden&v=keep-chart-dialog-stack-20260611';
const VISIBLE_DASHBOARD_URL = 'index.html?v=keep-chart-dialog-stack-20260611';

export function getPersonById(id) {
  return fallbackPeople.find((person) => person.id === id) || null;
}

export function buildProfileRows(person) {
  if (!person) return [];
  const investmentAmount = displayInvestmentAmount(person);

  return [
    ['姓名', person.name],
    ['性别', person.gender],
    ['年龄', `${person.age}岁`],
    ['身份证号', person.idNumber],
    ['联系电话', person.phone],
    ['投资金额', investmentAmount],
    ['职业', person.occupation],
    ['突出行为', person.behavior],
    ['到访次数', `${person.visits}次`],
    ['风险等级', person.risk],
    ['所属地区', person.district],
    ['属地单位', person.policeStation],
    ['户籍类型', person.locality],
    ['登记地址', person.address],
    ['备注', person.latestNote],
  ].map(([label, value]) => ({ label, value }));
}

export function buildDetailRows(person, options = {}) {
  if (!person) return [];

  const value = (key, fallback = '无') => person[key] || fallback;
  const investmentAmount = displayInvestmentAmount(person);
  const hiddenInvestorValue = options.hasFundHiddenInvestor ? '有' : '无';
  const rows = [
    { icon: '▥', label: '投资金额', value: investmentAmount, highlight: true },
    { icon: '◉', label: '其他投资', value: value('otherInvestment', '中植') },
    { icon: '▣', label: '背后是否存在隐性投资人', value: hiddenInvestorValue, highlight: true, wide: true },
    { icon: '⚡', label: '突出行为', value: value('behavior'), wide: true },
    { icon: '⌖', label: '到访情况', value: `${value('visitDetail', `到访${person.visits || 0}次`)}`, wide: true },
    { icon: '▤', label: '网络发声数据', value: value('onlineSpeech', '无'), wide: true },
    { icon: '▰', label: '社交平台账号', value: value('socialAccount', '未填写'), wide: true },
    { icon: '☎', label: '联系电话', value: value('phone'), wide: true },
    { icon: '▣', label: '职业', value: value('occupation') },
    { icon: '▤', label: '车辆信息', value: value('vehicle', '无') },
    { icon: '▤', label: '是否在库', value: value('libraryStatus', `${value('risk')}，${value('otherInvestment', '中植')}投资`), highlight: true },
    { icon: '⚠', label: '公安预警', value: value('policeWarning', '有'), highlight: true },
    { icon: '⌂', label: '户籍地', value: value('address'), wide: true },
    { icon: '⌖', label: '现住址', value: value('currentAddress', value('address')), wide: true },
    { icon: '☷', label: '关联人', value: value('relatedPerson', '未填写'), wide: true },
    { icon: '▦', label: '属地派出所', value: value('policeStation'), wide: true },
    { icon: '▣', label: '民警', value: value('policeContact', '未填写') },
    { icon: '▦', label: '社区', value: value('community', '未填写') },
    { icon: '▤', label: '就诊情况', value: value('latestNote'), wide: true },
  ];
  if (isMeaningfulDetail(person.responsiblePerson)) {
    rows.splice(16, 0, { icon: '☷', label: '包保责任人', value: person.responsiblePerson, wide: true });
  }
  return rows;
}

export function filterPeople(sourcePeople, filters = {}) {
  const query = normalize(filters.query);
  const idQuery = normalize(filters.idQuery);
  const locality = filters.locality || 'all';

  return sourcePeople.filter((person) => {
    const keywordText = normalize([
      person.name,
      person.gender,
      displayInvestmentAmount(person),
      person.occupation,
      person.behavior,
      person.policeStation,
      person.district,
      person.risk,
    ].join(' '));
    const idText = normalize(person.idNumber);
    const matchesQuery = !query || keywordText.includes(query);
    const matchesId = !idQuery || idText.includes(idQuery);
    const matchesLocality = locality === 'all' || person.locality === locality;
    return matchesQuery && matchesId && matchesLocality;
  });
}

export function paginatePeople(sourcePeople, requestedPage = 0, pageSize = SECTION_PAGE_SIZE) {
  const safePageSize = Math.max(1, Number(pageSize) || SECTION_PAGE_SIZE);
  const pageCount = Math.max(1, Math.ceil(sourcePeople.length / safePageSize));
  const page = normalizePage(requestedPage, pageCount);
  const start = page * safePageSize;

  return {
    items: sourcePeople.slice(start, start + safePageSize),
    page,
    pageCount,
    pageSize: safePageSize,
    total: sourcePeople.length,
  };
}

export function chunkPeople(sourcePeople, pageSize = SECTION_PAGE_SIZE) {
  if (!sourcePeople.length) return [[]];

  const pageCount = Math.ceil(sourcePeople.length / pageSize);
  return Array.from({ length: pageCount }, (_, pageIndex) => {
    return Array.from({ length: pageSize }, (_, itemIndex) => {
      const sourceIndex = (pageIndex * pageSize + itemIndex) % sourcePeople.length;
      return sourcePeople[sourceIndex];
    });
  });
}

function chunkPeopleNoWrap(sourcePeople, pageSize = SECTION_PAGE_SIZE) {
  if (!sourcePeople.length) return [[]];

  const pageCount = Math.ceil(sourcePeople.length / pageSize);
  return Array.from({ length: pageCount }, (_, pageIndex) => {
    const start = pageIndex * pageSize;
    return sourcePeople.slice(start, start + pageSize);
  });
}

function normalize(value = '') {
  return String(value).trim().toLowerCase();
}

function displayInvestmentAmount(person = {}) {
  return person.trustShareText || person.amount || '0万';
}

function isMeaningfulDetail(value) {
  const text = String(value ?? '').trim();
  return text !== '' && !['无', '未填写', '0', '0.0', '否', '无相关情况'].includes(text);
}

function normalizePage(page, pageCount) {
  const numericPage = Number(page);
  const integerPage = Number.isFinite(numericPage) ? Math.trunc(numericPage) : 0;
  return ((integerPage % pageCount) + pageCount) % pageCount;
}

if (typeof document !== 'undefined' && window.Vue && window.ElementPlus) {
  const { createApp } = window.Vue;

  createApp({
    data() {
      const initialScope = new URLSearchParams(window.location.search).get('scope') === 'hidden'
        ? 'hidden'
        : 'visible';
      return {
        dashboardScope: initialScope,
        dashboardTitle: initialScope === 'hidden' ? '隐名投资人架构图' : '黑龙江省群体架构图',
        dashboardTotal: initialScope === 'hidden' ? 0 : 1041,
        people: fallbackPeople,
        groups: fallbackGroups,
        regionStats: fallbackRegionStats,
        regionRows: fallbackRegionRows,
        riskBars: fallbackRiskBars,
        amountBuckets: [],
        provinceCityFullRows: [],
        outsideProvinceRows: [],
        excelColumns: [],
        clinicBars: fallbackClinicBars,
        primaryGroups: ['organizers', 'responders'],
        sectionFilters: {
          organizers: 'all',
          responders: 'all',
        },
        sectionCurrentPages: {
          organizers: 0,
          responders: 0,
        },
        drawerOpen: false,
        drawerMode: 'group',
        drawerGroup: 'organizers',
        drawerLocality: 'all',
        drawerQuery: '',
        drawerIdQuery: '',
        groupPeople: [],
        groupTotal: 0,
        groupPage: 1,
        groupSize: 1000,
        groupLoading: false,
        selectedRegion: null,
        selectedOutsideProvince: null,
        regionAmountBucket: null,
        regionFullScope: false,
        regionPeople: [],
        regionTotal: 0,
        regionLoading: false,
        amountStatsOpen: false,
        provinceCityStatsOpen: false,
        outsideProvinceStatsOpen: false,
        selectedAmountBucket: null,
        amountBucketHeaders: [],
        amountBucketRows: [],
        amountBucketTotal: 0,
        amountBucketPage: 1,
        amountBucketSize: 20,
        amountBucketLoading: false,
        profileOpen: false,
        fundGraphOpen: false,
        fundRelationLoading: false,
        fundRelationGraph: { nodes: [], edges: [], width: 760, height: 380 },
        activePerson: null,
        privacyMode: false,
        apiLoaded: false,
      };
    },
    computed: {
      isHiddenScope() {
        return this.dashboardScope === 'hidden';
      },
      amountStatsSubtitle() {
        const prefix = this.isHiddenScope ? '隐名投资人' : '仅统计黑龙江省人员';
        return `${prefix}，共 ${this.amountBucketStatsTotal} 人`;
      },
      drawerGroupInfo() {
        if (this.drawerMode === 'amount') {
          return {
            title: this.selectedAmountBucket?.label || '投资金额分档',
            count: this.amountBucketTotal,
            subtitle: '按持有中融信托产品份额总数筛选',
            tone: 'blue',
          };
        }
        if (this.drawerMode === 'region') {
          const bucketHint = this.regionAmountBucket?.label ? `（${this.regionAmountBucket.label}）` : '';
          return {
            title: `${this.selectedRegion || '地区'}人员${bucketHint}`,
            count: this.regionTotal || this.regionPeople.length,
            subtitle: this.regionAmountBucket?.label
              ? `按户籍地/属地筛选 + ${this.regionAmountBucket.label}`
              : '按户籍地/属地筛选',
            tone: 'teal',
          };
        }
        return this.groups[this.drawerGroup] || {
          title: '全部人员',
          count: this.groupTotal || this.people.length,
          subtitle: '按地区或关键词筛选',
          tone: 'red',
        };
      },
      otherPeopleCount() {
        return (this.groups.arrived?.count || 0) + (this.groups.hidden?.count || 0);
      },
      drawerTitle() {
        return this.drawerGroupInfo.title;
      },
      drawerSubtitle() {
        return this.drawerGroupInfo.subtitle;
      },
      drawerTone() {
        return this.drawerGroupInfo.tone;
      },
      isCompactGroupDrawer() {
        return this.drawerMode === 'group' && compactDrawerGroupKeys.includes(this.drawerGroup);
      },
      drawerGridClass() {
        return {
          'drawer-grid-compact': this.isCompactGroupDrawer,
          'drawer-grid-report-blue': this.drawerGroup === 'general',
          'drawer-grid-report-green': this.drawerGroup === 'watch',
          'drawer-grid-watch-optimal': this.drawerGroup === 'watch',
        };
      },
      drawerDisplayCount() {
        if (this.drawerMode === 'amount') return this.amountBucketTotal;
        if (this.drawerMode === 'region') return this.regionTotal || this.regionPeople.length;
        return this.groupTotal || this.drawerPeople.length;
      },
      drawerPeople() {
        const sourcePeople = this.drawerMode === 'region'
          ? this.regionPeople
          : this.drawerMode === 'group'
          ? this.groupPeople
          : this.drawerGroup === 'all'
          ? this.people
          : this.people.filter((person) => person.group === this.drawerGroup);

        return filterPeople(sourcePeople, {
          query: this.drawerQuery,
          idQuery: this.drawerIdQuery,
          locality: this.drawerLocality,
        });
      },
      activeProfileRows() {
        return buildProfileRows(this.activePerson);
      },
      detailRows() {
        return buildDetailRows(this.activePerson, {
          hasFundHiddenInvestor: this.activePerson?.fundHiddenInvestor === true || this.hasFundHiddenInvestor,
        });
      },
      primaryAmountBucket() {
        return this.amountBuckets.find((bucket) => bucket.count > 0) || this.amountBuckets[0] || null;
      },
      amountBucketSegments() {
        return pieSegments(this.amountBuckets);
      },
      amountBucketStatsTotal() {
        return this.amountBuckets.reduce((sum, bucket) => sum + Number(bucket.count || 0), 0);
      },
      cityDistrictSegments() {
        return regionSliceSegments(this.regionRows[0] || []);
      },
      provinceCityRows() {
        return (this.provinceCityFullRows.length ? this.provinceCityFullRows : (this.regionRows[1] || []))
          .filter(([name]) => !this.isRegionTotalChip(name))
          .map(([name, count], index) => ({
            key: `${name}-${index}`,
            label: String(name || '').trim(),
            count: Number(count || 0),
            color: REGION_CHART_COLORS[index % REGION_CHART_COLORS.length],
          }))
          .filter((row) => row.label && row.count > 0);
      },
      provinceCityTotal() {
        return this.provinceCityRows.reduce((sum, row) => sum + Number(row.count || 0), 0);
      },
      provinceCitySegments() {
        return regionSliceSegments(this.provinceCityRows.map((row) => [row.label, row.count]));
      },
      outsideProvinceChartRows() {
        return (this.outsideProvinceRows || [])
          .filter(([name]) => !this.isRegionTotalChip(name))
          .map(([name, count], index) => ({
            key: `${name}-${index}`,
            label: String(name || '').trim(),
            count: Number(count || 0),
            color: REGION_CHART_COLORS[index % REGION_CHART_COLORS.length],
          }))
          .filter((row) => row.label && row.count > 0);
      },
      outsideProvinceTotal() {
        return this.outsideProvinceChartRows.reduce((sum, row) => sum + Number(row.count || 0), 0);
      },
      outsideProvinceSegments() {
        return regionSliceSegments(this.outsideProvinceChartRows.map((row) => [row.label, row.count]));
      },
      cityPieStyle() {
        return regionPieStyle(this.regionRows[0] || []);
      },
      provinceCityPieStyle() {
        return regionPieStyle(this.regionRows[1] || []);
      },
      canExportDrawerPdf() {
        return this.drawerMode !== 'amount' && printableGroupKeys.includes(this.drawerGroup);
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
      this.loadDashboard();
    },
    watch: {
      drawerLocality() {
        this.refreshGroupPeople();
      },
      drawerQuery() {
        this.refreshGroupPeople();
      },
      drawerIdQuery() {
        this.refreshGroupPeople();
      },
    },
    methods: {
      displayInvestmentAmount,
      async loadDashboard() {
        try {
          const endpoint = this.isHiddenScope ? '/api/dashboard/hidden-investors' : '/api/dashboard';
          const response = await fetch(apiUrl(endpoint), { cache: 'no-store' });
          if (!response.ok) return;

          const payload = await response.json();
          this.people = (payload.people || this.people).map(normalizeApiPerson);
          this.dashboardTitle = payload.title || this.dashboardTitle;
          document.title = this.dashboardTitle;
          const sourceTotal = Number(payload.source?.riskPeople ?? payload.source?.totalRows ?? payload.total ?? 0);
          this.dashboardTotal = sourceTotal || this.people.length || this.dashboardTotal;
          this.groups = payload.groups || this.groups;
          this.regionStats = payload.regionStats || this.regionStats;
          this.regionRows = payload.regionRows || this.regionRows;
          this.provinceCityFullRows = payload.provinceCityFullRows || [];
          this.outsideProvinceRows = payload.outsideProvinceRows || [];
          this.riskBars = payload.riskBars || this.riskBars;
          this.amountBuckets = normalizeAmountBuckets(payload.amountBuckets || []);
          this.excelColumns = payload.excelColumns || [];
          this.clinicBars = payload.clinicBars || this.clinicBars;
          this.apiLoaded = true;
        } catch {
          this.apiLoaded = false;
        }
      },
      sectionPeople(groupKey) {
        const sourcePeople = this.people.filter((person) => person.group === groupKey);
        return filterPeople(sourcePeople, {
          locality: this.sectionFilters[groupKey],
        });
      },
      sectionPages(groupKey) {
        const rows = this.sectionPeople(groupKey);
        return this.isHiddenScope ? chunkPeopleNoWrap(rows) : chunkPeople(rows);
      },
      sectionStatus(groupKey) {
        const filtered = this.sectionPeople(groupKey);
        const pageData = paginatePeople(filtered, this.sectionCurrentPages[groupKey]);
        return filtered.length
          ? `第 ${pageData.page + 1}/${pageData.pageCount} 页 · 共 ${filtered.length} 人`
          : '暂无匹配人员';
      },
      setSectionPage(groupKey, pageIndex) {
        this.sectionCurrentPages[groupKey] = pageIndex;
      },
      resetSectionPage(groupKey) {
        this.sectionCurrentPages[groupKey] = 0;
        this.$nextTick(() => {
          const carousel = this.carouselForGroup(groupKey);
          if (carousel) carousel.setActiveItem(0);
        });
      },
      carouselForGroup(groupKey) {
        const index = this.primaryGroups.indexOf(groupKey);
        return Array.isArray(this.$refs.sectionCarouselRefs)
          ? this.$refs.sectionCarouselRefs[index]
          : null;
      },
      async openDrawer(groupKey) {
        this.drawerMode = 'group';
        this.drawerGroup = groupKey || 'organizers';
        this.drawerLocality = 'all';
        this.drawerQuery = '';
        this.drawerIdQuery = '';
        this.groupPage = 1;
        this.groupPeople = [];
        this.groupTotal = this.groups[this.drawerGroup]?.count || 0;
        this.drawerOpen = true;
        await this.loadGroupPeople();
      },
      async loadGroupPeople() {
        if (this.drawerMode !== 'group') return;
        this.groupLoading = true;
        try {
          if (this.isHiddenScope) {
            const params = new URLSearchParams({
              page: String(this.groupPage),
              size: String(this.groupSize),
              group: this.drawerGroup,
            });
            if (this.drawerLocality !== 'all') params.set('locality', this.drawerLocality);
            if (this.drawerQuery) params.set('name', this.drawerQuery);
            if (this.drawerIdQuery) params.set('idNumber', this.drawerIdQuery);
            const response = await fetch(apiUrl(`/api/dashboard/hidden-investors/people?${params.toString()}`), { cache: 'no-store' });
            if (!response.ok) return;
            const payload = await response.json();
            this.groupPeople = (payload.rows || []).map(normalizeApiPerson);
            this.groupTotal = payload.total || 0;
            return;
          }
          const params = new URLSearchParams({
            page: String(this.groupPage),
            size: String(this.groupSize),
          });
          const risk = groupRiskLabel(this.drawerGroup);
          if (risk) params.set('risk', risk);
          if (this.drawerLocality !== 'all') params.set('locality', this.drawerLocality);
          if (this.drawerQuery) params.set('name', this.drawerQuery);
          if (this.drawerIdQuery) params.set('idNumber', this.drawerIdQuery);
          const response = await fetch(apiUrl(`/api/admin/people?${params.toString()}`), { cache: 'no-store' });
          if (!response.ok) return;
          const payload = await response.json();
          this.groupPeople = (payload.rows || []).map(normalizeApiPerson);
          this.groupTotal = payload.total || 0;
        } finally {
          this.groupLoading = false;
        }
      },
      refreshGroupPeople() {
        if (this.drawerMode !== 'group') return;
        this.groupPage = 1;
        this.loadGroupPeople();
      },
      openDrawerForRegion(region, amountBucket = '', options = {}) {
        if (!region) return;
        this.drawerMode = 'region';
        this.drawerGroup = 'all';
        this.drawerLocality = 'all';
        this.drawerQuery = '';
        this.drawerIdQuery = '';
        this.selectedRegion = region;
        this.selectedOutsideProvince = null;
        this.regionAmountBucket = amountBucket && typeof amountBucket === 'object' && 'key' in amountBucket
          ? amountBucket
          : null;
        this.regionFullScope = options.fullScope === true;
        this.regionPeople = [];
        this.regionTotal = 0;
        this.drawerOpen = true;
        this.loadRegionPeople(region, amountBucket);
      },
      openProvinceCityRegion(region) {
        this.openDrawerForRegion(region, '', { fullScope: true });
      },
      openOutsideProvinceRegion(province) {
        if (!province) return;
        this.drawerMode = 'region';
        this.drawerGroup = 'all';
        this.drawerLocality = 'all';
        this.drawerQuery = '';
        this.drawerIdQuery = '';
        this.selectedRegion = province;
        this.selectedOutsideProvince = province;
        this.regionAmountBucket = null;
        this.regionFullScope = true;
        this.regionPeople = [];
        this.regionTotal = 0;
        this.drawerOpen = true;
        this.loadRegionPeople(province);
      },
      handleCityDistrictPieClick(event) {
        const segment = this.findCityDistrictSegmentByPoint(event);
        if (!segment?.bucket?.label) return;
        this.openDrawerForRegion(segment.bucket.label, segment.bucket);
      },
      findCityDistrictSegmentByPoint(event) {
        const svg = event.currentTarget;
        if (!(svg instanceof SVGElement)) return null;
        if (!this.cityDistrictSegments.length) return null;

        const rect = svg.getBoundingClientRect();
        const width = rect.width || 0;
        const height = rect.height || 0;
        if (!width || !height) return null;

        const x = (event.clientX - rect.left) / width * 120 - 60;
        const y = (event.clientY - rect.top) / height * 120 - 60;
        const distance = Math.hypot(x, y);
        if (distance < 24 || distance > 52) return null;

        const angle = (Math.atan2(y, x) * 180) / Math.PI;
        const normalizedAngle = (angle + 450) % 360;
        return this.cityDistrictSegments.find((segment) => isAngleInRange(
          normalizedAngle,
          segment.startAngle,
          segment.endAngle,
        ));
      },
      async loadRegionPeople(region = this.selectedRegion, amountBucket = this.regionAmountBucket) {
        if (!region) return;
        this.regionLoading = true;
        try {
          if (this.isHiddenScope) {
            const params = new URLSearchParams({
              page: '1',
              size: '1000',
            });
            if (this.selectedOutsideProvince) params.set('province', this.selectedOutsideProvince);
            else params.set('region', region);
            if (!this.regionFullScope && !this.selectedOutsideProvince) params.set('group', 'hidden');
            if (amountBucket?.key) params.set('amountBucket', amountBucket.key);
            const response = await fetch(apiUrl(`/api/dashboard/hidden-investors/people?${params.toString()}`), { cache: 'no-store' });
            if (!response.ok) return;
            const payload = await response.json();
            this.regionPeople = (payload.rows || []).map(normalizeApiPerson);
            this.regionTotal = payload.total || this.regionPeople.length;
            return;
          }
          const params = new URLSearchParams({
            ...(amountBucket?.key ? { amountBucket: amountBucket.key } : {}),
            excludeLevelGroups: String(!this.regionFullScope),
            page: '1',
            size: '1000',
          });
          if (this.selectedOutsideProvince) params.set('residenceProvince', this.selectedOutsideProvince);
          else params.set('region', region);
          const response = await fetch(apiUrl(`/api/admin/people?${params.toString()}`), { cache: 'no-store' });
          if (!response.ok) return;
          const payload = await response.json();
          this.regionPeople = (payload.rows || []).map(normalizeApiPerson);
          this.regionTotal = payload.total || this.regionPeople.length;
        } finally {
          this.regionLoading = false;
        }
      },
      async openAmountBucket(bucket) {
        if (!bucket) return;
        this.amountStatsOpen = false;
        this.drawerMode = 'amount';
        this.selectedAmountBucket = bucket;
        this.regionAmountBucket = null;
        this.amountBucketPage = 1;
        this.amountBucketRows = [];
        this.amountBucketTotal = bucket.count || 0;
        this.drawerOpen = true;
        await this.loadAmountBucketPeople();
      },
      openAmountStats() {
        this.amountStatsOpen = true;
      },
      openProvinceCityStats() {
        this.provinceCityStatsOpen = true;
      },
      openOutsideProvinceStats() {
        this.outsideProvinceStatsOpen = true;
      },
      provinceCityPercent(row) {
        const total = this.provinceCityTotal || 0;
        if (!total) return '0%';
        const value = (Number(row?.count || 0) / total) * 100;
        return `${value >= 10 ? value.toFixed(1) : value.toFixed(2)}%`;
      },
      provinceCityLabelTransform(segment) {
        const midAngle = ((Number(segment.startAngle || 0) + Number(segment.endAngle || 0)) / 2 - 90) * Math.PI / 180;
        const radius = 66;
        const x = 60 + Math.cos(midAngle) * radius;
        const y = 60 + Math.sin(midAngle) * radius;
        return `translate(${x.toFixed(2)} ${y.toFixed(2)})`;
      },
      outsideProvincePercent(row) {
        const total = this.outsideProvinceTotal || 0;
        if (!total) return '0%';
        const value = (Number(row?.count || 0) / total) * 100;
        return `${value >= 10 ? value.toFixed(1) : value.toFixed(2)}%`;
      },
      outsideProvinceLabelTransform(segment) {
        return this.provinceCityLabelTransform(segment);
      },
      amountBucketPercent(bucket) {
        const total = this.amountBucketStatsTotal || 0;
        if (!total) return '0%';
        return `${((Number(bucket?.count || 0) / total) * 100).toFixed(1)}%`;
      },
      async loadAmountBucketPeople() {
        if (!this.selectedAmountBucket) return;
        this.amountBucketLoading = true;
        try {
          if (this.isHiddenScope) {
            const params = new URLSearchParams({
              amountBucket: this.selectedAmountBucket.key,
              page: String(this.amountBucketPage),
              size: String(this.amountBucketSize),
            });
            const response = await fetch(apiUrl(`/api/dashboard/hidden-investors/people?${params.toString()}`), { cache: 'no-store' });
            if (!response.ok) return;
            const payload = await response.json();
            this.amountBucketHeaders = payload.headers || this.excelColumns;
            this.amountBucketRows = (payload.rows || []).map(normalizeApiPerson);
            this.amountBucketTotal = payload.total || 0;
            return;
          }
          const params = `amountBucket=${encodeURIComponent(this.selectedAmountBucket.key)}&province=本省&page=${this.amountBucketPage}&size=${this.amountBucketSize}`;
          const response = await fetch(apiUrl(`/api/admin/people?${params.toString()}`), { cache: 'no-store' });
          if (!response.ok) return;
          const payload = await response.json();
          this.amountBucketHeaders = payload.headers || [];
          this.amountBucketRows = (payload.rows || []).map(normalizeApiPerson);
          this.amountBucketTotal = payload.total || 0;
        } finally {
          this.amountBucketLoading = false;
        }
      },
      async exportDrawerPdf() {
        if (!this.canExportDrawerPdf) return;

        const printWindow = window.open('', '_blank', 'width=1600,height=1000');
        if (!printWindow) return;

        const groupInfo = this.drawerGroupInfo;
        const people = await this.drawerPrintPeople();
        const toneClass = this.printToneClass(this.drawerTone);
        printWindow.document.write(renderA3GroupPrintPage({
          title: groupInfo.title,
          subtitle: groupInfo.subtitle,
          count: people.length,
          toneClass,
          people,
        }));
        printWindow.document.close();
        printWindow.focus();
        setTimeout(() => {
          printWindow.print();
        }, 900);
      },
      async drawerPrintPeople() {
        if (this.drawerMode !== 'group') return this.drawerPeople;
        if (this.isHiddenScope) {
          const params = new URLSearchParams({
            page: '1',
            size: String(Math.max(this.groupTotal, this.groupSize, 1)),
            group: this.drawerGroup,
          });
          if (this.drawerLocality !== 'all') params.set('locality', this.drawerLocality);
          if (this.drawerQuery) params.set('name', this.drawerQuery);
          if (this.drawerIdQuery) params.set('idNumber', this.drawerIdQuery);
          try {
            const response = await fetch(apiUrl(`/api/dashboard/hidden-investors/people?${params.toString()}`), { cache: 'no-store' });
            if (!response.ok) return this.drawerPeople;
            const payload = await response.json();
            return (payload.rows || []).map(normalizeApiPerson);
          } catch {
            return this.drawerPeople;
          }
        }
        const params = new URLSearchParams({
          page: '1',
          size: String(Math.max(this.groupTotal, this.groupSize, 1)),
        });
        const risk = groupRiskLabel(this.drawerGroup);
        if (risk) params.set('risk', risk);
        if (this.drawerLocality !== 'all') params.set('locality', this.drawerLocality);
        if (this.drawerQuery) params.set('name', this.drawerQuery);
        if (this.drawerIdQuery) params.set('idNumber', this.drawerIdQuery);
        try {
          const response = await fetch(apiUrl(`/api/admin/people?${params.toString()}`), { cache: 'no-store' });
          if (!response.ok) return this.drawerPeople;
          const payload = await response.json();
          return (payload.rows || []).map(normalizeApiPerson);
        } catch {
          return this.drawerPeople;
        }
      },
      async openProfile(person) {
        this.activePerson = person;
        this.profileOpen = true;
        this.fundGraphOpen = false;
        await this.loadFundRelations(person);
      },
      async openGraphNodeProfile(node) {
        const identity = graphNodeIdentity(node);
        if (!identity.name && !identity.idNumber) return;
        const matchedPerson = await this.findPersonByGraphIdentity(identity);
        const person = matchedPerson || graphNodePerson(node, identity);
        await this.openProfile(person);
      },
      async findPersonByGraphIdentity(identity) {
        if (this.isHiddenScope) {
          const params = new URLSearchParams({ page: '1', size: '1' });
          if (identity.idNumber) params.set('idNumber', identity.idNumber);
          else if (identity.name) params.set('name', identity.name);
          try {
            const response = await fetch(apiUrl(`/api/dashboard/hidden-investors/people?${params.toString()}`), { cache: 'no-store' });
            if (!response.ok) return null;
            const payload = await response.json();
            const row = (payload.rows || [])[0];
            return row ? normalizeApiPerson(row) : null;
          } catch {
            return null;
          }
        }
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
      personCardToneClass(tone) {
        if (tone === 'yellow') return 'person-card-yellow';
        if (tone === 'blue') return 'person-card-blue';
        if (tone === 'teal' || tone === 'green') return 'person-card-green';
        return '';
      },
      policeStationLines(value) {
        return policeStationLines(value);
      },
      isRegionTotalChip(name) {
        return String(name || '').includes('合计') || String(name || '') === '总计';
      },
      displayRegionRow(row) {
        if (!Array.isArray(row) || row.length < 2) return row;
        const detailRows = row.filter(([name]) => !this.isRegionTotalChip(name));
        const total = detailRows.reduce((sum, item) => sum + Number(item?.[1] || 0), 0);
        return [['总计', total], ...detailRows];
    },
    regionTableTotalCount() {
      return (this.regionRows || []).reduce((sum, row) => {
        const displayRow = this.displayRegionRow(row) || [];
        const total = displayRow.find(([name]) => this.isRegionTotalChip(name));
        return sum + Number(total?.[1] || 0);
      }, 0);
    },
    printToneClass(tone) {
        if (tone === 'yellow') return 'yellow';
        if (tone === 'blue') return 'blue';
        if (tone === 'teal' || tone === 'green') return 'green';
        return 'red';
      },
      async loadFundRelations(person = this.activePerson) {
        if (!person?.id) {
          this.fundRelationGraph = { nodes: [], edges: [], width: 760, height: 380 };
          return;
        }
        this.fundRelationLoading = true;
        try {
          const response = await fetch(apiUrl(fundRelationPath(person)), { cache: 'no-store' });
          if (!response.ok) {
            this.fundRelationGraph = { nodes: [], edges: [], width: 760, height: 380 };
            return;
          }
          const payload = await response.json();
          this.fundRelationGraph = payload.graph || { nodes: [], edges: [], width: 760, height: 380 };
          this.applyFundHiddenInvestor(person, graphHasLowerInvestor(this.fundRelationGraph));
        } catch {
          this.fundRelationGraph = { nodes: [], edges: [], width: 760, height: 380 };
          this.applyFundHiddenInvestor(person, false);
        } finally {
          this.fundRelationLoading = false;
        }
      },
      applyFundHiddenInvestor(person, hasLowerInvestor) {
        if (!person?.id || this.activePerson?.id !== person.id) return;
        this.activePerson = {
          ...this.activePerson,
          fundHiddenInvestor: hasLowerInvestor,
          hiddenInvestor: hasLowerInvestor ? '有' : '无',
        };
      },
      async openFundGraph() {
        this.fundGraphOpen = true;
        if (!this.fundGraphEdges.length) await this.loadFundRelations();
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
      togglePrivacy() {
        this.privacyMode = !this.privacyMode;
        document.body.classList.toggle('privacy-mode', this.privacyMode);
      },
      goHiddenDashboard() {
        window.location.href = this.isHiddenScope ? VISIBLE_DASHBOARD_URL : HIDDEN_DASHBOARD_URL;
      },
    },
  }).use(window.ElementPlus).mount('#app');
}

function localDashboardPeople(sourcePeople, filters = {}) {
  let rows = [...sourcePeople];
  if (filters.group && filters.group !== 'all') {
    rows = filters.group === 'hidden'
      ? rows
      : rows.filter((person) => person.group === filters.group);
  }
  if (filters.region) {
    const region = String(filters.region).trim();
    rows = rows.filter((person) => {
      return String(person.district || '').includes(region)
        || String(person.address || '').includes(region)
        || String(person.currentAddress || '').includes(region);
    });
  }
  if (filters.amountBucket?.key) {
    rows = rows.filter((person) => personMatchesAmountBucket(person, filters.amountBucket.key));
  }
  return filterPeople(rows, {
    query: filters.query,
    idQuery: filters.idQuery,
    locality: filters.locality || 'all',
  });
}

function personMatchesAmountBucket(person = {}, bucketKey = '') {
  const amount = personAmountValue(person);
  return {
    gte10000: amount >= 100000000,
    '5000-10000': amount >= 50000000 && amount < 100000000,
    '3000-5000': amount >= 30000000 && amount < 50000000,
    '1000-3000': amount >= 10000000 && amount < 30000000,
    '500-1000': amount >= 5000000 && amount < 10000000,
    '300-500': amount >= 3000000 && amount < 5000000,
    lt300: amount >= 0 && amount < 3000000,
  }[bucketKey] || false;
}

function personAmountValue(person = {}) {
  if (Number.isFinite(Number(person.trustShareAmount))) return Number(person.trustShareAmount);
  return parseAmountText(person.trustShareText || person.amount || 0);
}

function parseAmountText(value = '') {
  const text = String(value).trim().replaceAll(',', '');
  if (!text) return 0;
  const multiplier = text.includes('亿') ? 100000000 : text.includes('万') ? 10000 : 1;
  const numeric = Number(text.replace(/[^\d.-]/g, ''));
  return Number.isFinite(numeric) ? numeric * multiplier : 0;
}

function normalizeAmountBuckets(buckets) {
  return buckets.map((bucket, index) => ({
    ...bucket,
    color: AMOUNT_BUCKET_COLORS[index % AMOUNT_BUCKET_COLORS.length],
  }));
}

function regionSliceSegments(rows = []) {
  const buckets = rows
    .filter((row) => row && row.length >= 2)
    .map((row, index) => {
      const label = String(row[0] || '').trim() || `分区${index + 1}`;
      return {
        key: `${label}-${index}`,
        label,
        count: Number(row[1] || 0),
        color: REGION_CHART_COLORS[index % REGION_CHART_COLORS.length],
      };
    })
    .filter((bucket) => Number.isFinite(bucket.count) && bucket.count > 0);
  const total = buckets.reduce((sum, bucket) => sum + Number(bucket.count || 0), 0);

  if (!total) {
    return [{
      bucket: { key: 'empty', label: '暂无数据', count: 0 },
      color: '#2b3e77',
      path: describePieSlice(60, 60, 56, 0, 359.99),
      labelX: 60,
      labelY: 60,
      displayLabel: '暂无数据',
    }];
  }

  let cursorDeg = 0;

  return buckets.map((bucket) => {
    const count = Number(bucket.count || 0);
    const segmentRatio = count / total;
    const spanDeg = segmentRatio * 360;
    const startDeg = cursorDeg;
    const endDeg = startDeg + spanDeg;
    const labelDeg = cursorDeg + spanDeg / 2;
    const labelAngle = ((labelDeg - 90) * Math.PI) / 180;

    const labelX = 60 + Math.cos(labelAngle) * 28;
    const labelY = 60 + Math.sin(labelAngle) * 28;

    const segment = {
      bucket,
      color: bucket.color,
      path: describePieSlice(60, 60, 56, startDeg, endDeg),
      startAngle: normalizeAngle(startDeg),
      endAngle: normalizeAngle(endDeg),
      labelX,
      labelY,
      displayLabel: `${bucket.label} ${count}人`,
    };

    cursorDeg += spanDeg;
    return segment;
  });
}

function normalizeAngle(value) {
  const normalized = value % 360;
  return normalized < 0 ? normalized + 360 : normalized;
}

function isAngleInRange(angle, startAngle = 0, endAngle = 0) {
  const point = normalizeAngle(angle);
  const start = normalizeAngle(startAngle);
  const end = normalizeAngle(endAngle);

  if (start === end) return true;
  if (start < end) return point >= start && point < end;
  return point >= start || point < end;
}

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

function groupRiskLabel(groupKey) {
  return {
    organizers: '组织串联',
    responders: '活跃响应',
    general: '一般参与',
    watch: '密切关注',
    arrived: '到场非投资人',
    hidden: '隐名投资人',
  }[groupKey] || '';
}

function policeStationLines(value = '') {
  const text = String(value || '未填写').trim() || '未填写';
  const splitAt = text.indexOf('分局');
  if (splitAt >= 0 && splitAt + 2 < text.length) {
    return [text.slice(0, splitAt + 2), text.slice(splitAt + 2)];
  }
  return [text];
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

function regionPieStyle(rows) {
  const total = rows.reduce((sum, row) => sum + Number(row[1] || 0), 0);
  if (!total) return { background: 'conic-gradient(#2b3e77 0 100%)' };
  let cursor = 0;
  const stops = rows.map((row, index) => {
    const start = cursor;
    cursor += (Number(row[1] || 0) / total) * 100;
    return `${REGION_CHART_COLORS[index % REGION_CHART_COLORS.length]} ${start}% ${cursor}%`;
  });
  return { background: `conic-gradient(${stops.join(', ')})` };
}

function pieSegments(buckets) {
  const total = buckets.reduce((sum, bucket) => sum + Number(bucket.count || 0), 0);
  if (!total) {
    return [{
      bucket: { key: 'empty', label: '暂无数据', count: 0 },
      color: '#2b3e77',
      path: describePieSlice(60, 60, 56, 0, 359.99),
    }];
  }
  let startAngle = 0;
  return buckets.filter((bucket) => Number(bucket.count || 0) > 0).map((bucket) => {
    const angle = (Number(bucket.count || 0) / total) * 360;
    const endAngle = startAngle + angle;
    const segment = {
      bucket,
      color: bucket.color,
      path: describePieSlice(60, 60, 56, startAngle, endAngle),
    };
    startAngle = endAngle;
    return segment;
  });
}

function polarPoint(cx, cy, radius, angle) {
  const radians = ((angle - 90) * Math.PI) / 180;
  return {
    x: cx + radius * Math.cos(radians),
    y: cy + radius * Math.sin(radians),
  };
}

function describePieSlice(cx, cy, radius, startAngle, endAngle) {
  const start = polarPoint(cx, cy, radius, startAngle);
  const end = polarPoint(cx, cy, radius, endAngle);
  const largeArcFlag = endAngle - startAngle > 180 ? 1 : 0;
  return [
    `M ${cx} ${cy}`,
    `L ${start.x.toFixed(2)} ${start.y.toFixed(2)}`,
    `A ${radius} ${radius} 0 ${largeArcFlag} 1 ${end.x.toFixed(2)} ${end.y.toFixed(2)}`,
    'Z',
  ].join(' ');
}

function renderA3GroupPrintPage({ title, subtitle, count, toneClass, people }) {
  const layout = printLayoutForCount(people.length);
  const cards = people.map((person) => renderPrintPersonCard(person, toneClass)).join('');
  return `<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <title>${escapeHtml(title)} A3导出</title>
  <style>
    @page { size: A3 landscape; margin: 8mm; }
    * { box-sizing: border-box; }
    html, body {
      margin: 0;
      background: #ffffff;
      color: #071327;
      font-family: "Microsoft YaHei", "PingFang SC", Arial, sans-serif;
      -webkit-print-color-adjust: exact;
      print-color-adjust: exact;
    }
    .print-sheet {
      width: 100%;
      height: 281mm;
      padding: 0;
      overflow: hidden;
    }
    .print-head {
      display: grid;
      grid-template-columns: 1fr auto 1fr;
      align-items: end;
      gap: 4mm;
      height: ${layout.headerHeight}mm;
      margin: 0 0 ${layout.gap}mm;
      padding-bottom: 2mm;
      border-bottom: 2px solid #dfe7f1;
      break-after: avoid;
    }
    .print-head h1 {
      grid-column: 2;
      margin: 0;
      font-size: ${layout.titleSize}pt;
      line-height: 1.1;
      text-align: center;
    }
    .print-head h1 span {
      color: #ff243b;
    }
    .print-head p {
      grid-column: 1;
      margin: 0;
      color: #58667a;
      font-size: ${layout.metaSize}pt;
      font-weight: 700;
    }
    .print-meta {
      grid-column: 3;
      justify-self: end;
      color: #58667a;
      font-size: ${layout.metaSize}pt;
      font-weight: 700;
    }
    .print-grid {
      display: grid;
      grid-template-columns: repeat(${layout.columns}, minmax(0, 1fr));
      grid-template-rows: repeat(${layout.rows}, minmax(0, 1fr));
      gap: ${layout.gap}mm;
      height: calc(281mm - ${layout.headerHeight + layout.gap}mm);
      align-items: stretch;
    }
    .print-card {
      display: grid;
      grid-template-columns: ${layout.photoWidth}mm minmax(0, 1fr);
      gap: ${layout.innerGap}mm;
      min-height: 0;
      padding: ${layout.padding}mm;
      border-radius: 2.2mm;
      color: #071327;
      background: #f65459;
      break-inside: avoid;
      page-break-inside: avoid;
    }
    .print-card.yellow {
      background: #ffe069;
    }
    .print-card.blue {
      color: #f7fbff;
      background: linear-gradient(135deg, #315f9f 0%, #5f91cf 100%);
    }
    .print-card.green {
      color: #f4fff7;
      background: linear-gradient(135deg, #2f7650 0%, #62a979 100%);
    }
    .portrait {
      width: ${layout.photoWidth}mm;
      height: ${layout.photoHeight}mm;
      border: 1mm solid #ffb264;
      border-radius: 1.5mm;
      display: grid;
      place-items: end center;
      padding-bottom: 1mm;
      background:
        radial-gradient(circle at 50% 31%, #ffd9bc 0 5.5mm, transparent 5.8mm),
        linear-gradient(145deg, rgba(5, 29, 61, 0.92) 48%, rgba(240, 248, 255, 0.9) 49% 58%, rgba(8, 45, 88, 0.92) 59%),
        linear-gradient(180deg, #dff2ff, #f8fbff 54%, #c7ddf1);
      overflow: hidden;
      box-shadow: inset 0 0 0 .6mm rgba(255, 255, 255, 0.75);
    }
    .portrait img {
      width: 100%;
      height: 100%;
      object-fit: cover;
      display: block;
    }
    .portrait span {
      width: ${layout.badgeSize}mm;
      height: ${layout.badgeSize}mm;
      display: grid;
      place-items: center;
      border-radius: 999px;
      background: rgba(255,255,255,.9);
      font-size: ${layout.badgeFont}mm;
      font-weight: 900;
    }
    .card-body {
      min-width: 0;
      display: grid;
      align-content: start;
      gap: ${layout.textGap}mm;
      font-weight: 800;
      font-size: ${layout.fontSize}pt;
      line-height: 1.12;
    }
    .card-body strong {
      font-size: ${layout.nameSize}pt;
      line-height: 1.1;
    }
    .card-body span {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  </style>
</head>
<body>
  <main class="print-sheet">
    <header class="print-head">
      <p>${escapeHtml(subtitle)}</p>
      <h1>${escapeHtml(title)} <span>${count}人</span></h1>
      <div class="print-meta">A3横向彩色打印版</div>
    </header>
    <section class="print-grid">${cards || '<p>暂无匹配人员</p>'}</section>
  </main>
</body>
</html>`;
}

function renderPrintPersonCard(person, toneClass) {
  const photo = person.photoUrl
    ? `<img src="${escapeHtml(person.photoUrl)}" alt="${escapeHtml(person.name)}照片">`
    : '<span>人</span>';
  return `<article class="print-card ${toneClass}">
    <div class="portrait">${photo}</div>
    <div class="card-body">
      <strong>${escapeHtml(person.name)} ${escapeHtml(person.gender)} ${escapeHtml(person.age)}岁</strong>
      <span>投资金额:${escapeHtml(displayInvestmentAmount(person))}</span>
      <span>职业:${escapeHtml(person.occupation)}</span>
      <span>突出行为:${escapeHtml(person.behavior)}</span>
      <span>到访次数:${escapeHtml(person.visits)}次</span>
      <span>属地单位:${escapeHtml(person.policeStation)}</span>
    </div>
  </article>`;
}

function printLayoutForCount(count) {
  if (count > 110) {
    return {
      columns: 14,
      rows: Math.ceil(count / 14),
      headerHeight: 12,
      titleSize: 18,
      metaSize: 6.5,
      gap: 1,
      innerGap: .8,
      padding: .8,
      photoWidth: 7.8,
      photoHeight: 12.8,
      badgeSize: 4,
      badgeFont: 2.8,
      fontSize: 4.9,
      nameSize: 6.8,
      textGap: .25,
    };
  }
  if (count > 70) {
    return {
      columns: 10,
      rows: Math.ceil(count / 10),
      headerHeight: 14,
      titleSize: 21,
      metaSize: 8,
      gap: 1.6,
      innerGap: 1.2,
      padding: 1.2,
      photoWidth: 10,
      photoHeight: 16,
      badgeSize: 5,
      badgeFont: 3.2,
      fontSize: 6,
      nameSize: 8,
      textGap: .45,
    };
  }
  return {
    columns: 5,
    rows: Math.max(1, Math.ceil(count / 5)),
    headerHeight: 18,
    titleSize: 26,
    metaSize: 10,
    gap: 4,
    innerGap: 3,
    padding: 2.3,
    photoWidth: 22,
    photoHeight: 34,
    badgeSize: 8.8,
    badgeFont: 6,
    fontSize: 9,
    nameSize: 13,
    textGap: .8,
  };
}

function escapeHtml(value = '') {
  return String(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;');
}
