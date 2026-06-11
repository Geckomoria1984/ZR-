import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { readFileSync } from 'node:fs';
import {
  buildAdminDetailRows,
  buildPhotoPreview,
  parseRelatedPeople,
} from '../frontend/src/admin-helpers.js';

describe('admin personal photo migration', () => {
  it('parses related person text into table rows', () => {
    const rows = parseRelatedPeople('同户籍,张三,230100199001010011,13900000000\\n配偶、李四、230100199202020022、13800000000、教师');

    assert.deepEqual(rows[0], {
      relation: '同户籍',
      name: '张三',
      idNumber: '230100199001010011',
      phone: '13900000000',
      occupation: '',
    });
    assert.equal(rows[1].relation, '配偶');
    assert.equal(rows[1].name, '李四');
    assert.equal(rows[1].occupation, '教师');
  });

  it('builds detail rows for the admin personal page', () => {
    const rows = buildAdminDetailRows({
      name: '测试人员',
      amount: '300万',
      trustShareText: '1,200万',
      otherInvestment: '中植',
      relatedPerson: '配偶,李四,230100199202020022,13800000000',
      policeStation: '南岗派出所',
      responsiblePerson: '张三 13900000000',
      latestNote: '未就诊',
    });

    assert.ok(rows.some((row) => row.label === '投资金额' && row.value === '1,200万'));
    assert.ok(rows.some((row) => row.label === '关联人' && row.value.includes('李四')));
    assert.ok(rows.some((row) => row.label === '属地派出所' && row.value === '南岗派出所'));
    assert.ok(rows.some((row) => row.label === '包保责任人' && row.value === '张三 13900000000'));
  });

  it('hides empty responsible person rows in admin personal details', () => {
    const rows = buildAdminDetailRows({
      responsiblePerson: '未填写',
    });

    assert.ok(!rows.some((row) => row.label === '包保责任人'));
  });

  it('marks admin hidden investor field as present when fund graph has lower-level people', () => {
    const rows = buildAdminDetailRows({
      hiddenInvestor: '无',
    }, { hasFundHiddenInvestor: true });

    assert.ok(rows.some((row) => row.label === '背后是否存在隐性投资人' && row.value === '有'));
  });

  it('marks admin hidden investor field absent when fund graph has no lower-level people', () => {
    const rows = buildAdminDetailRows({
      hiddenInvestor: '有',
    }, { hasFundHiddenInvestor: false });

    assert.ok(rows.some((row) => row.label === '背后是否存在隐性投资人' && row.value === '无'));
  });

  it('shows criminal and ZR disposal fields only when present', () => {
    const rows = buildAdminDetailRows({
      excelFields: {
        excel_34: '盗窃前科1次',
        excel_64: '已处置打击',
      },
    });

    assert.ok(rows.some((row) => row.label === '前科累计情况' && row.value === '盗窃前科1次'));
    assert.ok(rows.some((row) => row.label === 'ZR被处置打击人员' && row.value === '已处置打击'));

    const hiddenRows = buildAdminDetailRows({
      criminalRecord: '无',
      zrDisposal: '否',
      excelFields: {
        excel_34: '0',
        excel_64: '',
      },
    });

    assert.ok(!hiddenRows.some((row) => row.label === '前科累计情况'));
    assert.ok(!hiddenRows.some((row) => row.label === 'ZR被处置打击人员'));
  });

  it('builds a photo preview model from person photo data', () => {
    const preview = buildPhotoPreview({
      name: '测试人员',
      idNumber: '230100199001010011',
      photoUrl: '/api/photos/230100199001010011',
    });

    assert.equal(preview.title, '测试人员照片');
    assert.equal(preview.url, '/api/photos/230100199001010011');
    assert.equal(preview.placeholder, '测');
  });

  it('renders migrated dialogs and actions in the admin page', () => {
    const html = readFileSync(new URL('../frontend/admin.html', import.meta.url), 'utf8');

    assert.match(html, /@click="openFunds\(row\)"/);
    assert.match(html, /@click="openRelations\(row\)"/);
    assert.match(html, /@click="openPhoto\(row\)"/);
    assert.match(html, /@click="openPhotoImport"/);
    assert.match(html, /@click="openFundImport"/);
    assert.match(html, /导入关联人/);
    assert.match(html, /导入隐名投资人/);
    assert.match(html, /导入增加人员/);
    assert.match(html, /v-for="column in tableColumns"/);
    assert.match(html, /class="admin-person-detail"/);
    assert.match(html, /class="admin-photo-preview"/);
    assert.match(html, /class="admin-photo-import"/);
    assert.match(html, /class="admin-relation-table"/);
    assert.match(html, /class="fund-relation-table"/);
  });

  it('wires data import to the Excel upload endpoint', () => {
    for (const { html, script } of adminArtifacts()) {
      assert.match(html, /@click="openDataImport"/);
      assert.match(html, /v-model="dataImportOpen"/);
      assert.match(html, /accept="\.xlsx,\.xls"/);
      assert.match(script, /FormData/);
      assert.match(script, /\/api\/admin\/people\/import-excel/);
      assert.match(script, /this\.loadPeople\(\)/);
    }
  });

  it('wires fund relation import and graph lookup to backend endpoints', () => {
    for (const { html, script } of adminArtifacts()) {
      assert.match(html, /@click="openFundImport"/);
      assert.match(html, /v-model="fundImportOpen"/);
      assert.match(html, /accept="\.xlsx,\.xls"/);
      assert.match(html, /v-for="header in fundRelationHeaders"/);
      assert.match(html, /v-for="node in fundGraphNodes"/);
      assert.match(html, /v-for="edge in fundGraphEdges"/);
      assert.doesNotMatch(html, /<circle\b/);
      assert.doesNotMatch(html, /四级到一级资金关系图谱/);
      assert.match(html, /<rect x="-76" y="-31" width="152" height="62" rx="7"/);
      assert.match(script, /fundNodeAmount/);
      assert.match(script, /fundNodeLevelRank/);
      assert.match(script, /fundGraphEdges\.length > 0/);
      assert.match(script, /向上层投资金额：/);
      assert.match(script, /Math\.round\(amountInWan\)/);
      assert.match(script, /\/api\/admin\/fund-relations\/import-excel/);
      assert.match(script, /\/api\/admin\/fund-relations\/person\/\$\{(?:row|person)\.id\}/);
      assert.match(script, /fundRelationRows/);
      assert.match(script, /fundRelationGraph/);
    }
  });

  it('wires related, hidden investor, and added people imports to backend endpoints', () => {
    for (const { html, script } of adminArtifacts()) {
      assert.match(html, /@click="openExcelStoreImport\('relatedPeople'\)"/);
      assert.match(html, /@click="openExcelStoreImport\('hiddenInvestors'\)"/);
      assert.match(html, /@click="openExcelStoreImport\('addedPeople'\)"/);
      assert.match(html, /v-model="excelStoreImportOpen"/);
      assert.match(html, /:title="excelStoreImportConfig\.title"/);
      assert.match(script, /\/api\/admin\/imports\/related-people\/import-excel/);
      assert.match(script, /\/api\/admin\/imports\/hidden-investors\/import-excel/);
      assert.match(script, /\/api\/admin\/imports\/added-people\/import-excel/);
      assert.match(script, /confirmExcelStoreImport/);
      assert.match(script, /excelStoreImporting/);
    }
  });
});

function adminArtifacts() {
  return [
    {
      html: readFileSync(new URL('../frontend/admin.html', import.meta.url), 'utf8'),
      script: readFileSync(new URL('../frontend/src/admin.js', import.meta.url), 'utf8'),
    },
  ];
}
