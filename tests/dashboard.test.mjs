import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { readFileSync } from 'node:fs';
import { people, groups, regionStats, regionRows } from '../frontend/src/data.js';
import {
  SECTION_PAGE_SIZE,
  getPersonById,
  buildProfileRows,
  buildDetailRows,
  graphHasLowerInvestor,
  graphHasVisibleInvestor,
  fundRelationPath,
  profileUsesHiddenInvestorFields,
  categoryGroupKeysForScope,
  shouldOpenVisibleProfileFromGraphNode,
  filterPeople,
  paginatePeople,
  chunkPeople,
} from '../frontend/src/app.js';

describe('dashboard data contract', () => {
  it('contains personnel groups and regional summary data', () => {
    assert.ok(people.length >= 12);
    assert.equal(groups.organizers.title, '组织串联人员');
    assert.equal(groups.responders.title, '积极响应人员');
    assert.ok(regionStats.length >= 10);
    assert.equal(regionRows.length, 2);
  });

  it('keeps homepage region rows sorted by count from small to large', () => {
    for (const row of regionRows) {
      const counts = row.map(([, count]) => count);
      assert.deepEqual(counts, counts.toSorted((left, right) => left - right));
    }
  });

  it('builds profile rows for the clicked person', () => {
    const person = {
      ...getPersonById(people[0].id),
      amount: '300万',
      trustShareText: '1,200万',
    };
    const rows = buildProfileRows(person);

    assert.equal(rows[0].label, '姓名');
    assert.equal(rows[0].value, person.name);
    assert.ok(rows.some((row) => row.label === '投资金额' && row.value === '1,200万'));
    assert.ok(rows.some((row) => row.label === '突出行为'));
  });

  it('hides empty responsible person rows in personal details', () => {
    const hiddenRows = buildDetailRows({
      ...getPersonById(people[0].id),
      responsiblePerson: '未填写',
    });
    const visibleRows = buildDetailRows({
      ...getPersonById(people[0].id),
      responsiblePerson: '张三 13900000000',
    });

    assert.ok(!hiddenRows.some((row) => row.label === '包保责任人'));
    assert.ok(visibleRows.some((row) => row.label === '包保责任人' && row.value === '张三 13900000000'));
  });

  it('marks hidden investor as present when fund graph has lower-level people', () => {
    const rows = buildDetailRows({
      ...getPersonById(people[0].id),
      hiddenInvestor: '无',
    }, { hasFundHiddenInvestor: true });

    assert.ok(rows.some((row) => row.label === '背后是否存在隐性投资人' && row.value === '有'));
  });

  it('does not mark hidden investor as present without fund graph lower-level people', () => {
    const rows = buildDetailRows({
      ...getPersonById(people[0].id),
      hiddenInvestor: '有',
    }, { hasFundHiddenInvestor: false });

    assert.ok(rows.some((row) => row.label === '背后是否存在隐性投资人' && row.value === '无'));
  });

  it('checks hidden investors behind the current graph person rather than any graph edge', () => {
    const graph = {
      nodes: [
        { id: 'id-visible', fullLabel: '显名投资人', primary: true },
        { id: 'id-hidden-current', fullLabel: '当前隐名', primary: false },
        { id: 'id-hidden-lower', fullLabel: '下层隐名', primary: false },
      ],
      edges: [
        { source: 'id-hidden-current', target: 'id-visible' },
        { source: 'id-hidden-lower', target: 'id-hidden-current' },
      ],
    };

    assert.equal(graphHasLowerInvestor(graph, { idNumber: 'hidden-current', name: '当前隐名' }), true);
    assert.equal(graphHasLowerInvestor({
      ...graph,
      edges: [{ source: 'id-hidden-current', target: 'id-visible' }],
    }, { idNumber: 'hidden-current', name: '当前隐名' }), false);
    assert.equal(graphHasLowerInvestor(graph, { idNumber: 'visible', name: '显名投资人' }), true);
  });

  it('shows whether a hidden investor has a visible investor', () => {
    const rows = buildDetailRows({
      ...getPersonById(people[0].id),
      fundVisibleInvestor: true,
    }, { isHiddenScope: true, hasVisibleInvestor: true });

    assert.ok(rows.some((row) => row.label === '是否存在显性投资人' && row.value === '有'));
    assert.ok(!rows.some((row) => row.label === '背后是否存在隐性投资人'));
  });

  it('detects a visible investor node for hidden investor fund graphs', () => {
    const hiddenPerson = { idNumber: 'hidden-current', name: '当前隐名' };
    assert.equal(graphHasVisibleInvestor({
      nodes: [
        { id: 'id-visible', fullLabel: '显名投资人', level: '显名投资人', primary: true },
        { id: 'id-hidden-current', fullLabel: '当前隐名', level: '一层', primary: false },
      ],
      edges: [{ source: 'id-hidden-current', target: 'id-visible' }],
    }, hiddenPerson), true);
    assert.equal(graphHasVisibleInvestor({
      nodes: [
        { id: 'id-hidden-current', fullLabel: '当前隐名', level: '一层', primary: true },
        { id: 'id-hidden-lower', fullLabel: '下层隐名', level: '二层', primary: false },
      ],
      edges: [{ source: 'id-hidden-lower', target: 'id-hidden-current' }],
    }, hiddenPerson), false);
  });

  it('opens matched visible personal details from visible investor graph nodes in hidden scope', () => {
    assert.equal(shouldOpenVisibleProfileFromGraphNode({
      id: 'id-23010219520801342X',
      fullLabel: '唐利香',
      level: '显名投资人',
    }, true), true);
    assert.equal(shouldOpenVisibleProfileFromGraphNode({
      id: 'id-230102197810141327',
      fullLabel: '杨馨嘉',
      level: '一层',
    }, true), false);
    assert.equal(shouldOpenVisibleProfileFromGraphNode({
      id: 'id-23010219520801342X',
      fullLabel: '唐利香',
      level: '显名投资人',
    }, false), false);
    assert.equal(profileUsesHiddenInvestorFields({ id: 'hidden-230102197810141327' }, true), true);
    assert.equal(profileUsesHiddenInvestorFields({ id: 'p18' }, true), false);
  });

  it('passes hidden imported visible investor identity into fund graph lookup', () => {
    const path = fundRelationPath({
      id: 'hidden-230104196812133126',
      fundIdentity: { idNumber: '230104196812133126', name: '李晓丹', visibleName: '李琳琳' },
      visibleInvestorName: '李琳琳',
      visibleInvestorIdNumber: '',
    });

    assert.match(path, /\/api\/admin\/fund-relations\/identity\?/);
    assert.match(path, /idNumber=230104196812133126/);
    assert.match(path, /visibleName=%E6%9D%8E%E7%90%B3%E7%90%B3/);
  });

  it('filters people by query and locality', () => {
    const target = people.find((person) => person.locality === '本市');
    const filtered = filterPeople(people, {
      query: target.name.slice(0, 2),
      locality: '本市',
    });

    assert.ok(filtered.length >= 1);
    assert.ok(filtered.every((person) => person.locality === '本市'));
    assert.ok(filtered.some((person) => person.id === target.id));
  });

  it('paginates each primary carousel through every person and wraps around', () => {
    for (const groupKey of ['organizers', 'responders']) {
      const groupPeople = people.filter((person) => person.group === groupKey);
      const firstPage = paginatePeople(groupPeople, 0);
      const secondPage = paginatePeople(groupPeople, 1);
      const wrappedPage = paginatePeople(groupPeople, firstPage.pageCount);
      const seenIds = new Set();

      for (let page = 0; page < firstPage.pageCount; page += 1) {
        paginatePeople(groupPeople, page).items.forEach((person) => seenIds.add(person.id));
      }

      assert.equal(firstPage.items.length, SECTION_PAGE_SIZE);
      assert.notEqual(firstPage.items[0].id, secondPage.items[0].id);
      assert.equal(wrappedPage.page, 0);
      assert.equal(seenIds.size, groupPeople.length);
    }
  });

  it('fills the final carousel page by wrapping to the start', () => {
    const organizerPeople = people.filter((person) => person.group === 'organizers');
    const pages = chunkPeople(organizerPeople);
    const lastPage = pages.at(-1);
    const originalIds = new Set(organizerPeople.map((person) => person.id));

    assert.equal(lastPage.length, SECTION_PAGE_SIZE);
    assert.equal(lastPage[0].id, organizerPeople.at(-1).id);
    assert.equal(lastPage[1].id, organizerPeople[0].id);
    assert.ok(pages.flat().some((person) => originalIds.has(person.id)));
  });

  it('uses Vue and Element Plus components for the interactive page', () => {
    const html = readFileSync(new URL('../frontend/index.html', import.meta.url), 'utf8');
    const styles = readFileSync(new URL('../frontend/src/styles.css', import.meta.url), 'utf8');

    assert.match(html, /vue\.global\.prod\.js/);
    assert.match(html, /element-plus/);
    assert.match(html, /<el-carousel/);
    assert.match(html, /<el-dialog/);
    assert.doesNotMatch(html, /<el-drawer/);
    assert.doesNotMatch(html, /direction="btt"/);
    assert.match(html, /class="list-dialog-panel"/);
    assert.match(styles, /grid-template-columns: repeat\(2, minmax\(0, 1fr\)\)/);
    assert.match(styles, /\.category-tile small[\s\S]*white-space: nowrap/);
  });

  it('uses the backend fund relation graph in homepage personal details', () => {
    const html = readFileSync(new URL('../frontend/index.html', import.meta.url), 'utf8');
    const script = readFileSync(new URL('../frontend/src/app.js', import.meta.url), 'utf8');
    const styles = readFileSync(new URL('../frontend/src/styles.css', import.meta.url), 'utf8');

    assert.match(html, /资金关系图谱预览/);
    assert.match(html, /v-for="node in fundGraphNodes"/);
    assert.match(html, /v-for="edge in fundGraphEdges"/);
    assert.match(html, /fund-node-amount/);
    assert.match(html, /fund-node-layer-/);
    assert.doesNotMatch(html, /通信图谱关系/);
    assert.doesNotMatch(html, /四级到一级资金关系图谱/);
    assert.doesNotMatch(html, /<text[^>]*>给付<\/text>/);
    assert.match(script, /\/api\/admin\/fund-relations\/person\/\$\{person\.id\}/);
    assert.match(script, /loadFundRelations/);
    assert.match(script, /fundNodeLevelRank/);
    assert.match(script, /向上层投资金额：/);
    assert.match(script, /Math\.round\(amountInWan\)/);
    assert.match(styles, /\.fund-node-html \.fund-node-amount\s*\{[\s\S]*font-size:\s*inherit/);
  });

  it('links from the homepage into the admin management page', () => {
    const html = readFileSync(new URL('../frontend/index.html', import.meta.url), 'utf8');

    assert.match(html, /href="admin\.html"/);
    assert.match(html, />后台管理</);
  });

  it('links visible homepage into the hidden investor dashboard scope', () => {
    const html = readFileSync(new URL('../frontend/index.html', import.meta.url), 'utf8');
    const script = readFileSync(new URL('../frontend/src/app.js', import.meta.url), 'utf8');

    assert.match(html, /<title>黑龙江省ZR群体架构图<\/title>/);
    assert.match(script, /VISIBLE_DASHBOARD_TITLE = '黑龙江省ZR群体架构图'/);
    assert.match(script, /VISIBLE_DASHBOARD_TOTAL = 1041/);
    assert.match(script, /: VISIBLE_DASHBOARD_TOTAL/);
    assert.match(html, /goHiddenDashboard/);
    assert.match(html, /进入隐名首页/);
    assert.match(html, /返回显名首页/);
    assert.match(html, /dashboardTitle/);
    assert.match(script, /dashboardScope/);
    assert.match(script, /scope=hidden/);
    assert.match(script, /hiddenFallbackGroups/);
    assert.match(script, /people:\s*initialScope === 'hidden' \? \[\] : fallbackPeople/);
    assert.match(script, /groups:\s*initialScope === 'hidden' \? hiddenFallbackGroups : fallbackGroups/);
    assert.match(script, /\/api\/dashboard\/hidden-investors/);
    assert.match(script, /\/api\/dashboard\/hidden-investors\/people/);
    assert.match(script, /localDashboardPeople/);
    assert.match(script, /personMatchesAmountBucket/);
  });

  it('keeps the hidden dashboard category strip focused on third and fourth level people', () => {
    assert.deepEqual(categoryGroupKeysForScope(false), ['general', 'watch']);
    assert.deepEqual(categoryGroupKeysForScope(true), ['general', 'watch']);
  });

  it('renders clickable investment amount buckets as a table drawer', () => {
    for (const { html, script } of homeArtifacts()) {
      assert.match(html, /<svg[^>]*class="amount-donut-svg"/);
      assert.match(html, /v-for="segment in amountBucketSegments"/);
      assert.match(html, /<path[\s\S]*class="amount-pie-segment"/);
      assert.match(html, /:d="segment\.path"/);
      assert.match(html, /class="chart-card amount-chart-card"[\s\S]*@click="openAmountStats"/);
      assert.match(html, /@click="openAmountStats"/);
      assert.match(html, /v-model="amountStatsOpen"/);
      assert.match(script, /仅统计黑龙江省人员/);
      assert.match(html, /class="amount-stats-row"/);
      assert.match(html, /@click="openAmountBucket\(bucket\)"/);
      assert.doesNotMatch(html, /amount-bucket-list/);
      assert.match(html, /<el-table[^>]*:data="amountBucketRows"/);
      assert.match(html, /v-for="header in amountBucketHeaders"/);
      assert.match(html, /:prop="`excelFields\.\$\{header\.key\}`"/);
      assert.match(script, /amountStatsOpen: false/);
      assert.match(script, /openAmountStats\(\)/);
      assert.match(script, /amountBucketPercent\(bucket\)/);
      assert.match(script, /function pieSegments\(buckets\)/);
      assert.match(script, /describePieSlice/);
      assert.match(script, /describePieSlice\(60, 60, 56/);
      assert.match(script, /amountBucket=/);
      assert.match(script, /province=本省/);
      assert.match(script, /amountBucketHeaders/);
      assert.match(script, /amountBuckets/);
    }
  });

  it('renders clickable Harbin district pie slices and province city pie rows', () => {
    for (const { html, script } of homeArtifacts()) {
      assert.match(html, /本市区县分布/);
      assert.match(html, /省内外地市户籍分布/);
      assert.match(html, /各省人员户籍分布/);
      assert.match(html, /v-for="segment in cityDistrictSegments"/);
      assert.match(html, /class="district-pie-segment"/);
      assert.match(html, /:d="segment\.path"/);
      assert.doesNotMatch(html, /cityDistrictSegments[\s\S]*:stroke-dasharray="segment\.dash"/);
      assert.match(html, /@click="openCityDistrictStats"/);
      assert.match(html, /v-model="cityDistrictStatsOpen"/);
      assert.match(html, /哈尔滨全口径统计/);
      assert.match(html, /openCityDistrictRegion\(segment\.bucket\.label\)/);
      assert.match(html, /v-for="segment in cityDistrictLabelSegments"/);
      assert.match(html, /v-for="row in cityDistrictRows"/);
      assert.match(html, /v-for="segment in provinceCitySegments"/);
      assert.match(html, /v-for="segment in outsideProvinceSegments"/);
      assert.match(html, /openProvinceCityStats/);
      assert.match(html, /openOutsideProvinceStats/);
      assert.match(html, /openProvinceCityRegion\(segment\.bucket\.label\)/);
      assert.match(html, /openOutsideProvinceRegion\(segment\.bucket\.label\)/);
      assert.doesNotMatch(html, /donut-stock/);
      assert.match(html, /@click="openDrawerForRegion\(name\)"/);
      assert.match(html, /v-loading="regionLoading \|\| groupLoading"/);
      assert.match(script, /regionRows\[0\]/);
      assert.match(script, /regionRows\[1\]/);
      assert.match(script, /harbinRegionFullRows: \[\]/);
      assert.match(script, /payload\.harbinRegionFullRows/);
      assert.match(script, /this\.harbinRegionFullRows\.length \? this\.harbinRegionFullRows/);
      assert.match(script, /cityDistrictStatsOpen: false/);
      assert.match(script, /cityDistrictRows\(\)/);
      assert.match(script, /cityDistrictLabelSegments\(\)/);
      assert.match(script, /openCityDistrictStats\(\)/);
      assert.match(script, /openDrawerForRegion\(region, '', \{ fullScope: true \}\)/);
      assert.match(script, /cityDistrictPercent\(row\)/);
      assert.match(script, /provinceCityLabelTransform/);
      assert.match(script, /outsideProvinceLabelTransform/);
      assert.match(script, /drawerMode = 'region'/);
      assert.match(script, /region,/);
      assert.match(script, /\/api\/admin\/people\?\$\{params\.toString\(\)\}/);
      assert.match(script, /startAngle/);
      assert.match(script, /describePieSlice\(60, 60, 56/);
    }
  });

  it('uses direct dialogs for homepage people lists', () => {
    for (const { html } of homeArtifacts()) {
      assert.match(html, /<el-dialog v-model="drawerOpen"/);
      assert.doesNotMatch(html, /<el-drawer/);
      assert.doesNotMatch(html, /direction="btt"/);
    }
  });

  it('keeps portrait photos in their original colors', () => {
    for (const stylesheet of homeStylesheets()) {
      assert.doesNotMatch(stylesheet, /portrait-\d+\s*\{[^}]*hue-rotate/);
    }
  });

  it('exports the four primary group drawers as A3 color PDF print pages with full-size cards', () => {
    const html = readFileSync(new URL('../frontend/index.html', import.meta.url), 'utf8');
    const script = readFileSync(new URL('../frontend/src/app.js', import.meta.url), 'utf8');

    assert.match(html, /@click="exportDrawerPdf"/);
    assert.match(html, /personCardToneClass\(drawerTone\)/);
    assert.match(script, /printableGroupKeys/);
    assert.match(script, /organizers/);
    assert.match(script, /responders/);
    assert.match(script, /general/);
    assert.match(script, /watch/);
    assert.match(script, /printToneClass/);
    assert.match(script, /green/);
    assert.match(script, /blue/);
    assert.match(script, /@page \{ size: A3 landscape;/);
    assert.match(script, /print-color-adjust: exact/);
    assert.match(script, /printLayoutForCount/);
    assert.match(script, /columns: 14/);
    assert.match(script, /compactDrawerGroupKeys = \[\]/);
    assert.doesNotMatch(script, /drawer-grid-watch-optimal/);
    assert.match(script, /groupSize: 1000/);
    assert.match(html, /list-dialog-compact/);
    assert.match(script, /person\.photoUrl/);
    assert.match(script, /<img src="\$\{escapeHtml\(person\.photoUrl\)\}"/);
    assert.match(script, /printWindow\.print\(\)/);
  });
});

function homeArtifacts() {
  return [
    {
      html: readFileSync(new URL('../frontend/index.html', import.meta.url), 'utf8'),
      script: readFileSync(new URL('../frontend/src/app.js', import.meta.url), 'utf8'),
    },
  ];
}

function homeStylesheets() {
  return [
    readFileSync(new URL('../frontend/src/styles.css', import.meta.url), 'utf8'),
  ];
}
