import { people, groups, regionStats } from './data.js';

const state = {
  mode: 'overview',
  group: 'all',
  risk: 'all',
  region: 'all',
  query: '',
  scan: false,
};

const groupKeys = ['all', 'organizers', 'responders', 'general', 'watch'];
const groupLabels = {
  all: '全部',
  organizers: '组织串联',
  responders: '积极响应',
  general: '一般参与',
  watch: '密切关注',
};
const riskKeys = ['all', '一级', '二级', '三级'];
const modeLabels = {
  overview: '态势总览',
  network: '关系图谱',
  response: '处置队列',
};

const app = document.querySelector('#tech-ui');
const totalNode = document.querySelector('h1 b');
const search = document.querySelector('.search-box input');
const groupBox = document.querySelector('.filter-group');
const riskBox = document.querySelector('.risk-group');
const scanToggle = document.querySelector('.scan-toggle');
const drawer = document.querySelector('.person-drawer');
const drawerContent = document.querySelector('.drawer-content');

function filteredPeople() {
  const query = state.query.trim().toLowerCase();
  return people.filter((person) => {
    const groupMatch = state.group === 'all' || person.group === state.group;
    const riskMatch = state.risk === 'all' || person.risk === state.risk;
    const regionMatch = state.region === 'all' || person.district === state.region || person.policeStation.includes(state.region);
    const text = [
      person.name,
      person.gender,
      person.amount,
      person.occupation,
      person.behavior,
      person.policeStation,
      person.district,
      person.risk,
    ].join(' ').toLowerCase();
    return groupMatch && riskMatch && regionMatch && (!query || text.includes(query));
  });
}

function render() {
  const current = filteredPeople();
  app.dataset.mode = state.mode;
  app.classList.toggle('scan-active', state.scan);
  totalNode.textContent = `${people.length}人`;
  renderFilters();
  renderLeftDeck(current);
  renderStage(current);
  renderRightDeck(current);
  renderPeopleDock(current);
}

function renderFilters() {
  groupBox.innerHTML = groupKeys.map((key) => `
    <button class="${state.group === key ? 'active' : ''}" type="button" data-group="${key}">
      ${groupLabels[key]}
    </button>
  `).join('');

  riskBox.innerHTML = riskKeys.map((key) => `
    <button class="${state.risk === key ? 'active' : ''}" type="button" data-risk="${key}">
      ${key === 'all' ? '全部风险' : key}
    </button>
  `).join('');

  scanToggle.classList.toggle('active', state.scan);
}

function renderLeftDeck(current) {
  document.querySelector('.total-count').textContent = current.length;
  document.querySelector('.group-matrix').innerHTML = `
    <h2>人员分类矩阵</h2>
    ${['organizers', 'responders', 'general', 'watch'].map((key) => {
      const count = current.filter((person) => person.group === key).length;
      return `
        <button class="matrix-row ${state.group === key ? 'active' : ''}" type="button" data-group="${key}">
          <span>${groups[key].title}</span>
          <i style="--w:${Math.max(8, Math.min(100, count * 2.8))}%"></i>
          <b>${count}</b>
        </button>
      `;
    }).join('')}
  `;

  const hotRegions = regionStats.slice(-8).toReversed();
  document.querySelector('.region-radar').innerHTML = `
    <h2>属地热力</h2>
    ${hotRegions.map(([name, count]) => {
      const key = normalizeRegion(name);
      return `
        <button class="region-row ${state.region === key ? 'active' : ''}" type="button" data-region="${key}">
          <span>${key}</span>
          <i style="--w:${Math.max(10, Math.min(100, count / 2.8))}%"></i>
          <b>${count}</b>
        </button>
      `;
    }).join('')}
  `;
}

function renderStage(current) {
  const hotRegions = regionStats.slice(-7).toReversed();
  document.querySelector('.region-nodes').innerHTML = hotRegions.map(([name, count], index) => {
    const key = normalizeRegion(name);
    return `
      <button class="region-node n${index} ${state.region === key ? 'active' : ''}" type="button" data-region="${key}">
        <span>${key}</span>
        <b>${count}</b>
      </button>
    `;
  }).join('');

  document.querySelector('.network-layer').innerHTML = people.slice(0, 18).map((person, index) => `
    <button class="network-dot d${index % 18} risk-${person.risk}" type="button" data-person="${person.id}" title="${person.name}">
      <span></span>
    </button>
  `).join('');

  const priority = current
    .filter((person) => person.risk === '一级' || person.group === 'organizers')
    .slice(0, 4);
  document.querySelector('.priority-row').innerHTML = priority.map((person) => `
    <button class="priority-card" type="button" data-person="${person.id}">
      <span>${person.risk}</span>
      <strong>${person.name}</strong>
      <small>${person.behavior}</small>
    </button>
  `).join('');
}

function renderRightDeck(current) {
  const riskCounts = riskKeys.slice(1).map((risk) => ({
    risk,
    count: current.filter((person) => person.risk === risk).length,
  }));
  const total = Math.max(1, riskCounts.reduce((sum, item) => sum + item.count, 0));
  document.querySelector('.risk-core').innerHTML = `
    <h2>风险核心</h2>
    <button class="risk-orbit" type="button" data-risk="一级">
      <span>${riskCounts[0].count}</span>
      <i>一级预警</i>
    </button>
    ${riskCounts.map((item) => `
      <button class="risk-bar ${state.risk === item.risk ? 'active' : ''}" type="button" data-risk="${item.risk}">
        <span>${item.risk}</span>
        <i style="--w:${Math.max(8, Math.round((item.count / total) * 100))}%"></i>
        <b>${item.count}</b>
      </button>
    `).join('')}
  `;

  const actionPeople = current
    .toSorted((a, b) => riskRank(a.risk) - riskRank(b.risk) || b.visits - a.visits)
    .slice(0, 5);
  document.querySelector('.action-queue').innerHTML = `
    <h2>${modeLabels[state.mode]}</h2>
    ${actionPeople.map((person, index) => `
      <button class="queue-row" type="button" data-person="${person.id}">
        <b>${String(index + 1).padStart(2, '0')}</b>
        <span>${person.name}</span>
        <i>${person.risk}</i>
      </button>
    `).join('')}
  `;

  document.querySelector('.mini-log').innerHTML = `
    <h2>系统记录</h2>
    <p>筛选范围：${current.length} 人</p>
    <p>当前模式：${modeLabels[state.mode]}</p>
    <p>属地节点：${state.region === 'all' ? '全域' : state.region}</p>
    <p>风险过滤：${state.risk === 'all' ? '全部' : state.risk}</p>
  `;
}

function renderPeopleDock(current) {
  const dockPeople = current
    .toSorted((a, b) => riskRank(a.risk) - riskRank(b.risk) || b.visits - a.visits)
    .slice(0, 12);

  document.querySelector('.people-dock').innerHTML = `
    <div class="dock-head">
      <strong>重点人员流</strong>
      <span>当前显示 ${dockPeople.length} / ${current.length}</span>
    </div>
    <div class="dock-grid">
      ${dockPeople.map((person) => personCard(person)).join('') || '<p class="empty-state">当前筛选无匹配人员</p>'}
    </div>
  `;
}

function personCard(person) {
  return `
    <button class="person-card ${person.group} risk-${person.risk}" type="button" data-person="${person.id}">
      <span class="avatar"><i>${person.name.slice(0, 1)}</i></span>
      <span class="person-info">
        <strong>${person.name} ${person.gender} ${person.age}岁</strong>
        <small>投资金额:${person.amount}</small>
        <small>行为:${person.behavior}</small>
        <small>属地:${person.policeStation}</small>
      </span>
    </button>
  `;
}

function openPerson(person) {
  drawer.setAttribute('aria-hidden', 'false');
  drawerContent.innerHTML = `
    <div class="drawer-avatar">${person.name.slice(0, 1)}</div>
    <h2>${person.name} ${person.gender} ${person.age}岁</h2>
    <p>${groups[person.group]?.title || '人员'} · ${person.risk}风险</p>
    <dl>
      <dt>投资金额</dt><dd>${person.amount}</dd>
      <dt>职业</dt><dd>${person.occupation}</dd>
      <dt>突出行为</dt><dd>${person.behavior}</dd>
      <dt>到访次数</dt><dd>${person.visits}次</dd>
      <dt>属地单位</dt><dd>${person.policeStation}</dd>
      <dt>户籍类型</dt><dd>${person.locality}</dd>
      <dt>登记地址</dt><dd>${person.address}</dd>
    </dl>
  `;
}

function normalizeRegion(name) {
  return String(name).replace(/分局|市局|市|县/g, '');
}

function riskRank(risk) {
  return { 一级: 1, 二级: 2, 三级: 3 }[risk] || 9;
}

document.querySelector('.mode-tabs').addEventListener('click', (event) => {
  const button = event.target.closest('[data-mode]');
  if (!button) return;
  state.mode = button.dataset.mode;
  document.querySelectorAll('.mode-tabs button').forEach((item) => item.classList.toggle('active', item === button));
  render();
});

document.addEventListener('click', (event) => {
  const groupButton = event.target.closest('[data-group]');
  if (groupButton) {
    state.group = groupButton.dataset.group;
    render();
    return;
  }

  const riskButton = event.target.closest('[data-risk]');
  if (riskButton) {
    state.risk = riskButton.dataset.risk;
    render();
    return;
  }

  const regionButton = event.target.closest('[data-region]');
  if (regionButton) {
    state.region = state.region === regionButton.dataset.region ? 'all' : regionButton.dataset.region;
    render();
    return;
  }

  const personButton = event.target.closest('[data-person]');
  if (personButton) {
    const person = people.find((item) => item.id === personButton.dataset.person);
    if (person) openPerson(person);
  }
});

search.addEventListener('input', (event) => {
  state.query = event.target.value;
  render();
});

scanToggle.addEventListener('click', () => {
  state.scan = !state.scan;
  render();
});

document.querySelector('.drawer-close').addEventListener('click', () => {
  drawer.setAttribute('aria-hidden', 'true');
});

document.addEventListener('keydown', (event) => {
  if (event.key === 'Escape') drawer.setAttribute('aria-hidden', 'true');
});

render();
