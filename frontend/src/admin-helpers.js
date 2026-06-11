export function parseRelatedPeople(text = '') {
  if (Array.isArray(text)) return text;

  return String(text || '')
    .replaceAll('\\n', '\n')
    .split(/\r?\n|;/)
    .map((line) => line.trim())
    .filter((line) => line && line !== '无' && line !== '未填写')
    .map((line) => {
      const parts = line
        .split(/[,，、\t]/)
        .map((part) => part.trim())
        .filter(Boolean);
      const relation = parts[0] || '';
      const name = parts[1] || '';
      const rest = parts.slice(2);
      const idNumber = rest.find((part) => /^\d{15,18}[0-9Xx]?$/.test(part)) || '';
      const phone = rest.find((part) => /^1\d{10}$/.test(part)) || '';
      const occupation = rest.find((part) => part !== idNumber && part !== phone && !/^1\d{10}$/.test(part)) || '';

      return {
        relation,
        name,
        idNumber,
        phone,
        occupation,
      };
    });
}

export function buildAdminDetailRows(person = {}, options = {}) {
  const value = (key, fallback = '无') => {
    const raw = person[key];
    return raw === undefined || raw === null || raw === '' ? fallback : String(raw);
  };
  const optionalDetailRows = [
    optionalDetailRow(person, 'criminalRecord', 'excel_34', '前科累计情况'),
    optionalDetailRow(person, 'zrDisposal', 'excel_64', 'ZR被处置打击人员'),
  ].filter(Boolean);
  const investmentAmount = displayInvestmentAmount(person);
  const hiddenInvestorValue = options.hasFundHiddenInvestor ? '有' : '无';

  const rows = [
    { icon: '▥', label: '投资金额', value: investmentAmount, highlight: true },
    { icon: '◉', label: '其他投资', value: value('otherInvestment', '中植') },
    { icon: '▣', label: '背后是否存在隐性投资人', value: hiddenInvestorValue, highlight: true, wide: true },
    { icon: '⚡', label: '突出行为', value: value('behavior'), wide: true },
    { icon: '⌖', label: '到访情况', value: value('visitDetail', `到访${person.visits || 0}次`), wide: true },
    { icon: '▤', label: '网络发声数据', value: value('onlineSpeech', '无'), wide: true },
    { icon: '☎', label: '联系电话', value: value('phone'), wide: true },
    { icon: '▣', label: '职业', value: value('occupation') },
    { icon: '▤', label: '车辆信息', value: value('vehicle', '无') },
    { icon: '▤', label: '是否在库', value: value('libraryStatus', value('risk', '未分级')), highlight: true },
    { icon: '⚠', label: '公安预警', value: value('policeWarning', '无'), highlight: true },
    ...optionalDetailRows,
    { icon: '⌂', label: '户籍地', value: value('address'), wide: true },
    { icon: '⌖', label: '现住址', value: value('currentAddress', value('address')), wide: true },
    { icon: '☷', label: '关联人', value: value('relatedPerson', value('relatedPersonInfo', '未填写')), wide: true },
    { icon: '▦', label: '属地派出所', value: value('policeStation'), wide: true },
    { icon: '▣', label: '民警', value: value('policeContact', '未填写') },
    { icon: '▦', label: '社区', value: value('community', '未填写') },
    { icon: '▤', label: '就诊情况', value: value('latestNote'), wide: true },
  ];
  if (isMeaningfulDetail(String(person.responsiblePerson ?? '').trim())) {
    rows.splice(15, 0, { icon: '☷', label: '包保责任人', value: String(person.responsiblePerson).trim(), wide: true });
  }
  return rows;
}

function displayInvestmentAmount(person = {}) {
  return person.trustShareText || person.amount || '0万';
}

export function buildPhotoPreview(person = {}) {
  const name = person.name || '人员';
  const idNumber = person.idNumber || '';

  return {
    title: `${name}照片`,
    url: person.photoUrl || '',
    placeholder: name.slice(0, 1),
    name,
    idNumber,
  };
}

export function normalizeAdminColumns(columns = []) {
  return columns
    .filter((column) => column && column.key && column.label)
    .map((column) => ({
      key: String(column.key),
      label: String(column.label),
      width: Number(column.width) || adminColumnWidth(String(column.label)),
      fixed: column.fixed || (column.label === '姓名' ? 'left' : false),
    }));
}

export function adminCellText(row = {}, column = {}) {
  const fields = row.excelFields || {};
  const value = fields[column.key];
  if (value === undefined || value === null || value === '') return '';
  return String(value);
}

function adminColumnWidth(label) {
  if (label === '身份证号') return 210;
  if (label === '姓名') return 110;
  if (['性别', '年龄', '序号'].includes(label)) return 80;
  if (label.length >= 18) return 240;
  if (label.length >= 10) return 190;
  return 140;
}

function optionalDetailRow(person, propertyKey, excelKey, label) {
  const value = firstMeaningful(person[propertyKey], person.excelFields?.[excelKey]);
  if (!value) return null;
  return {
    icon: '⚠',
    label,
    value,
    highlight: true,
    wide: true,
  };
}

function firstMeaningful(...values) {
  for (const value of values) {
    const text = String(value ?? '').trim();
    if (!isMeaningfulDetail(text)) continue;
    return text;
  }
  return '';
}

function isMeaningfulDetail(value) {
  return value !== ''
    && !['无', '未填写', '0', '0.0', '否', '无相关情况'].includes(value);
}
