import { people, groups, regionStats } from './data.js';

const concepts = [
  {
    id: 'command',
    name: '01 指挥矩阵',
    title: '省级指挥矩阵',
    note: '保留大屏气势，强化分组、重点人员和属地态势。',
    className: 'theme-command',
  },
  {
    id: 'intel',
    name: '02 情报研判',
    title: '情报研判工作台',
    note: '左侧条件，右侧详情，中间列表，适合日常查人。',
    className: 'theme-intel',
  },
  {
    id: 'map',
    name: '03 地图联动',
    title: '区域地图态势',
    note: '先看属地，再看人员，适合判断区域压力。',
    className: 'theme-map',
  },
  {
    id: 'alert',
    name: '04 预警处置',
    title: '分级预警处置台',
    note: '按风险优先级组织首页，突出先处理谁。',
    className: 'theme-alert',
  },
  {
    id: 'civic',
    name: '05 政务驾驶舱',
    title: '白蓝政务驾驶舱',
    note: '更清爽克制，适合办公、截图汇报和长时间查看。',
    className: 'theme-civic',
  },
  {
    id: 'compact',
    name: '06 密集作战',
    title: '密集作战看板',
    note: '提高同屏信息量，适合快速扫描大量人员。',
    className: 'theme-compact',
  },
];

const state = {
  conceptId: concepts[0].id,
  group: 'all',
  locality: 'all',
  risk: 'all',
  query: '',
  region: 'all',
  selectedPerson: people[0],
};

const app = document.querySelector('#six-ui-app');
const tabs = document.querySelector('.concept-tabs');
const stage = document.querySelector('.concept-stage');
const panel = document.querySelector('.profile-panel');
const panelBody = document.querySelector('.profile-body');

function currentConcept() {
  return concepts.find((concept) => concept.id === state.conceptId) || concepts[0];
}

function filteredPeople() {
  const query = state.query.trim().toLowerCase();
  return people.filter((person) => {
    const groupMatch = state.group === 'all' || person.group === state.group;
    const localityMatch = state.locality === 'all' || person.locality === state.locality;
    const riskMatch = state.risk === 'all' || person.risk === state.risk;
    const regionMatch = state.region === 'all' || person.district === state.region || person.policeStation.includes(state.region);
    const text = [person.name, person.gender, person.amount, person.occupation, person.behavior, person.policeStation, person.district, person.risk].join(' ').toLowerCase();
    const queryMatch = !query || text.includes(query);
    return groupMatch && localityMatch && riskMatch && regionMatch && queryMatch;
  });
}

function groupCount(key) {
  return people.filter((person) => person.group === key).length;
}

function riskCount(risk) {
  return filteredPeople().filter((person) => person.risk === risk).length;
}

function renderTabs() {
  tabs.innerHTML = concepts.map((concept) => `
    <button class="${concept.id === state.conceptId ? 'active' : ''}" type="button" data-concept="${concept.id}">
      ${concept.name}
    </button>
  `).join('');
}

function render() {
  const concept = currentConcept();
  app.className = `six-app ${concept.className}`;
  renderTabs();
  stage.innerHTML = conceptTemplate(concept);
  bindStage();
}

function conceptTemplate(concept) {
  const source = filteredPeople();
  const primary = source.filter((person) => person.group === 'organizers' || person.group === 'responders').slice(0, concept.id === 'compact' ? 18 : 10);
  const secondary = source.filter((person) => person.group === 'general' || person.group === 'watch').slice(0, concept.id === 'compact' ? 16 : 8);
  const regions = regionStats.slice(-10).toReversed();

  return `
    <section class="screen-shell ${concept.id}">
      <header class="screen-head">
        <div>
          <span>${concept.note}</span>
          <h2>${concept.title} <b>${people.length}人</b></h2>
        </div>
        <div class="screen-actions">
          <button type="button" data-action="reset">重置</button>
          <button type="button" data-group="organizers">重点人员</button>
          <button type="button" data-group="watch">密切关注</button>
        </div>
      </header>

      ${filtersTemplate()}

      <section class="metric-row">
        ${metric('组织串联', groupCount('organizers'), 'organizers')}
        ${metric('积极响应', groupCount('responders'), 'responders')}
        ${metric('一般参与', groupCount('general'), 'general')}
        ${metric('密切关注', groupCount('watch'), 'watch')}
      </section>

      <section class="screen-grid">
        ${leftRailTemplate(concept, regions)}
        <div class="main-board">
          <div class="board-title">
            <strong>重点人员</strong>
            <span>当前筛选 ${source.length} 人</span>
          </div>
          <div class="people-grid">
            ${primary.map(personCard).join('') || emptyState()}
          </div>
          <div class="board-title secondary-title">
            <strong>补充人员</strong>
            <span>点击卡片查看档案摘要</span>
          </div>
          <div class="people-grid secondary-grid">
            ${secondary.map(personCard).join('') || emptyState()}
          </div>
        </div>
        ${rightRailTemplate(concept)}
      </section>
    </section>
  `;
}

function filtersTemplate() {
  return `
    <section class="filters">
      <label>
        <span>检索</span>
        <input type="search" value="${escapeHtml(state.query)}" placeholder="姓名 / 属地 / 行为" data-filter="query">
      </label>
      <div class="segmented" aria-label="人员类型">
        ${filterButton('all', '全部', 'group')}
        ${filterButton('organizers', '组织串联', 'group')}
        ${filterButton('responders', '积极响应', 'group')}
        ${filterButton('general', '一般参与', 'group')}
        ${filterButton('watch', '密切关注', 'group')}
      </div>
      <div class="segmented compact-seg" aria-label="户籍类型">
        ${filterButton('all', '全域', 'locality')}
        ${filterButton('本市', '本市', 'locality')}
        ${filterButton('外市', '外市', 'locality')}
      </div>
      <div class="segmented compact-seg" aria-label="风险等级">
        ${filterButton('all', '全部风险', 'risk')}
        ${filterButton('一级', '一级', 'risk')}
        ${filterButton('二级', '二级', 'risk')}
        ${filterButton('三级', '三级', 'risk')}
      </div>
    </section>
  `;
}

function filterButton(value, label, type) {
  const active = state[type] === value ? 'active' : '';
  return `<button class="${active}" type="button" data-${type}="${value}">${label}</button>`;
}

function metric(label, value, group) {
  return `
    <button class="metric-card" type="button" data-group="${group}">
      <span>${label}</span>
      <b>${value}</b>
      <i>点击筛选</i>
    </button>
  `;
}

function leftRailTemplate(concept, regions) {
  if (concept.id === 'map') {
    return `
      <aside class="side-rail map-rail">
        <strong>区域态势</strong>
        <div class="mini-map">
          ${regions.slice(0, 6).map(([name, count], index) => `<button class="map-bubble b${index}" data-region="${regionKey(name)}">${regionKey(name)} ${count}</button>`).join('')}
        </div>
      </aside>
    `;
  }

  return `
    <aside class="side-rail">
      <strong>属地排行</strong>
      ${regions.slice(0, 7).map(([name, count]) => `
        <button class="rank-row" type="button" data-region="${regionKey(name)}">
          <span>${regionKey(name)}</span>
          <i style="--w:${Math.max(12, Math.min(100, count / 3))}%"></i>
          <b>${count}</b>
        </button>
      `).join('')}
    </aside>
  `;
}

function rightRailTemplate(concept) {
  const risks = ['一级', '二级', '三级'];
  if (concept.id === 'alert') {
    return `
      <aside class="side-rail alert-rail">
        <strong>预警队列</strong>
        ${risks.map((risk) => `<button class="warning warning-${risk}" type="button" data-risk="${risk}"><span>${risk}预警</span><b>${riskCount(risk)}</b></button>`).join('')}
        <p>点风险级别后，中间列表会实时收敛。</p>
      </aside>
    `;
  }

  return `
    <aside class="side-rail chart-rail">
      <strong>风险 / 金额 / 到访</strong>
      <button class="donut" type="button" data-risk="一级"><span>${riskCount('一级')}</span></button>
      <div class="bar-set">
        ${risks.map((risk) => `<button type="button" data-risk="${risk}"><span>${risk}</span><i style="--w:${Math.max(10, riskCount(risk) * 4)}%"></i></button>`).join('')}
      </div>
      <button class="summary-button" type="button" data-locality="外市">只看外市人员</button>
    </aside>
  `;
}

function personCard(person) {
  return `
    <button class="person-card ${person.group} risk-${person.risk}" type="button" data-person="${person.id}">
      <span class="avatar avatar-${person.avatarIndex % 8}">
        <i>${person.name.slice(0, 1)}</i>
      </span>
      <span class="person-info">
        <strong>${person.name} ${person.gender} ${person.age}岁</strong>
        <small>投资金额:${person.amount}</small>
        <small>职业:${person.occupation}</small>
        <small>行为:${person.behavior}</small>
        <small>属地:${person.policeStation}</small>
      </span>
    </button>
  `;
}

function emptyState() {
  return '<p class="empty-state">当前筛选无匹配人员</p>';
}

function bindStage() {
  stage.querySelectorAll('[data-group]').forEach((button) => {
    button.addEventListener('click', () => {
      state.group = button.dataset.group;
      render();
    });
  });

  stage.querySelectorAll('[data-locality]').forEach((button) => {
    button.addEventListener('click', () => {
      state.locality = button.dataset.locality;
      render();
    });
  });

  stage.querySelectorAll('[data-risk]').forEach((button) => {
    button.addEventListener('click', () => {
      state.risk = button.dataset.risk;
      render();
    });
  });

  stage.querySelectorAll('[data-region]').forEach((button) => {
    button.addEventListener('click', () => {
      state.region = button.dataset.region;
      render();
    });
  });

  stage.querySelectorAll('[data-person]').forEach((button) => {
    button.addEventListener('click', () => {
      const person = people.find((item) => item.id === button.dataset.person);
      if (person) openProfile(person);
    });
  });

  stage.querySelector('[data-filter="query"]')?.addEventListener('input', (event) => {
    state.query = event.target.value;
    render();
  });

  stage.querySelector('[data-action="reset"]')?.addEventListener('click', () => {
    state.group = 'all';
    state.locality = 'all';
    state.risk = 'all';
    state.region = 'all';
    state.query = '';
    render();
  });
}

function openProfile(person) {
  state.selectedPerson = person;
  panel.setAttribute('aria-hidden', 'false');
  panelBody.innerHTML = `
    <div class="profile-avatar">${person.name.slice(0, 1)}</div>
    <h2>${person.name} ${person.gender} ${person.age}岁</h2>
    <p>${groups[person.group]?.title || '人员'} · ${person.risk}风险 · ${person.locality}</p>
    <dl>
      <dt>投资金额</dt><dd>${person.amount}</dd>
      <dt>职业</dt><dd>${person.occupation}</dd>
      <dt>突出行为</dt><dd>${person.behavior}</dd>
      <dt>到访次数</dt><dd>${person.visits}次</dd>
      <dt>属地单位</dt><dd>${person.policeStation}</dd>
      <dt>登记地址</dt><dd>${person.address}</dd>
    </dl>
  `;
}

function closeProfile() {
  panel.setAttribute('aria-hidden', 'true');
}

function regionKey(name) {
  return String(name).replace(/分局|市局|市|县/g, '');
}

function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, (char) => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;',
  }[char]));
}

tabs.addEventListener('click', (event) => {
  const button = event.target.closest('[data-concept]');
  if (!button) return;
  state.conceptId = button.dataset.concept;
  state.group = 'all';
  state.locality = 'all';
  state.risk = 'all';
  state.region = 'all';
  state.query = '';
  render();
});

document.querySelector('.panel-close').addEventListener('click', closeProfile);
document.addEventListener('keydown', (event) => {
  if (event.key === 'Escape') closeProfile();
});

render();
