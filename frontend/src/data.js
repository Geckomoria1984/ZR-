export const groups = {
  organizers: {
    title: '组织串联人员',
    count: 21,
    subtitle: '网上串联、现场组织或到场40次以上',
    tone: 'red',
    summary: '南岗12人，道里7人，道外2人，松北2人，香坊1人，平房1人，呼兰1人，齐齐哈尔市1人，佳木斯市1人，黑河市1人',
  },
  responders: {
    title: '积极响应人员',
    count: 29,
    subtitle: '到场20次以上、40次以下；群内响应、发表过极端言论或意见领袖',
    tone: 'yellow',
    summary: '南岗7人，道里5人，香坊4人，松北2人，道外2人，平房1人',
  },
  general: {
    title: '一般参与人员',
    count: 130,
    subtitle: '有到场行为',
    tone: 'blue',
  },
  watch: {
    title: '密切关注人员',
    count: 26,
    subtitle: '有过极端言论或意见领袖，未到场或仅群内响应',
    tone: 'teal',
  },
  arrived: {
    title: '到场非投资人',
    count: 120,
    subtitle: '户籍地分布',
    tone: 'blue',
  },
  hidden: {
    title: '隐名投资人',
    count: 72,
    subtitle: '户籍地分布',
    tone: 'teal',
  },
};

const seedPeople = [
  person('p001', '尹中川', '男', 61, '1250万', '市建筑三公司', '网上串联', 32, '道里分局安静派出所', '道里', '本市', 'organizers', '一级', 0),
  person('p002', '陈春云', '女', 55, '330万', '哈尔滨盖枚产品工业公司', '网上串联、现场组织、到场40次以上', 43, '道里分局丽江路派出所', '道里', '本市', 'organizers', '一级', 1),
  person('p003', '孙守都', '男', 54, '900万', '哈尔滨和平国际旅行社有限公司', '网上串联', 10, '道里分局爱建路派出所', '道里', '本市', 'organizers', '二级', 2),
  person('p004', '李威', '女', 53, '900万', '哈尔滨中庆燃气有限责任公司输气分公司', '网上串联', 29, '南岗分局邮政派出所', '南岗', '本市', 'organizers', '一级', 3),
  person('p005', '吴丽梅', '女', 64, '330万', '哈尔滨市肿瘤医院', '网上串联', 3, '道里分局丽江路派出所', '道里', '本市', 'organizers', '三级', 4),
  person('p006', '李敏', '女', 60, '400万', '哈尔滨市电信局', '网上串联', 21, '松北分局船口派出所', '松北', '本市', 'organizers', '二级', 5),
  person('p007', '褚业东', '男', 60, '600万', '哈尔滨套夯饲粮库', '现场组织、到场40次以上', 49, '南岗分局清滨派出所', '南岗', '本市', 'organizers', '一级', 6),
  person('p008', '孙琦', '男', 48, '300万', '普正公益理公司', '网上串联', 3, '南岗分局学府路派出所', '南岗', '本市', 'organizers', '二级', 7),
  person('p009', '刁正弘', '男', 45, '300万', '未填写', '网上串联', 25, '道外分局南直派出所', '道外', '本市', 'organizers', '二级', 8),
  person('p010', '刘忠诚', '男', 69, '600万', '铁十三局四处', '网上串联', 13, '道里分局丽江路派出所', '道里', '本市', 'organizers', '三级', 9),
  person('p011', '路春英', '女', 53, '400万', '无业', '发表过极端言论或意见领袖，有过到场', 3, '南岗分局文化派出所', '南岗', '本市', 'responders', '二级', 10),
  person('p012', '董爱红', '女', 47, '800万', '未填写', '到场10次以上，并群内响应', 18, '南岗分局新建派出所', '南岗', '本市', 'responders', '二级', 11),
  person('p013', '唐利香', '女', 73, '440万', '市松江电机厂', '到场10次以上，并群内响应', 12, '道里分局建国派出所', '道里', '本市', 'responders', '二级', 12),
  person('p014', '崔杰民', '女', 66, '500万', '龙工仪表厂', '发表过极端言论或意见领袖，有过到场', 18, '南岗分局花园派出所', '南岗', '本市', 'responders', '二级', 13),
  person('p015', '石树魏', '女', 55, '3600万', '市第一百货商店幼儿园', '到场10次以上，并群内响应', 15, '道里分局兆麟派出所', '道里', '本市', 'responders', '一级', 14),
  person('p016', '任晓图', '女', 57, '300万', '无业', '网上串联', 9, '南岗分局巴山派出所', '南岗', '本市', 'responders', '三级', 15),
  person('p017', '朱浩要', '女', 61, '600万', '黑龙江省农场局种子公司', '网上串联', 16, '香坊分局铁东街派出所', '香坊', '本市', 'responders', '二级', 16),
  person('p018', '张影', '女', 53, '2750万', '哈尔滨煤气公司', '网上串联', 33, '南岗分局新春派出所', '南岗', '本市', 'responders', '一级', 17),
  person('p019', '刘学英', '女', 71, '1500万', '未填写', '网上串联', 39, '道外分局大兴派出所', '道外', '本市', 'responders', '一级', 18),
  person('p020', '张守娟', '女', 60, '920万', '黑龙江大学', '网上串联', 23, '南岗分局和兴派出所', '南岗', '本市', 'responders', '二级', 19),
  person('p021', '朴学成', '男', 41, '300万', '自由职业', '网上串联', 2, '松北分局松北派出所', '松北', '本市', 'general', '三级', 20),
  person('p022', '沈立忠', '男', 55, '400万', '公园民品设计所', '网上串联、现场组织、到场40次以上', 47, '南岗分局奋斗派出所', '南岗', '本市', 'general', '一级', 21),
  person('p023', '秦丽君', '女', 52, '600万', '中国银行黑龙江分行', '网上串联、现场组织、到场40次以上', 48, '南岗分局荣市派出所', '南岗', '本市', 'watch', '一级', 22),
  person('p024', '王林萍', '女', 52, '1650万', '哈尔滨旅游局', '网上串联、现场组织、到场40次以上', 51, '南岗分局芦家派出所', '南岗', '本市', 'watch', '一级', 23),
  person('p025', '陈丽芬', '女', 48, '1200万', '自由职业', '网上串联', 32, '香坊分局红旗大街派出所', '香坊', '本市', 'watch', '二级', 24),
  person('p026', '赵广明', '男', 58, '260万', '个体经营', '群内响应', 1, '齐齐哈尔市龙沙分局', '齐齐哈尔', '外市', 'watch', '三级', 25),
  person('p027', '马晓红', '女', 49, '180万', '退休', '意见领袖', 0, '佳木斯市前进分局', '佳木斯', '外市', 'watch', '二级', 26),
  person('p028', '高海涛', '男', 46, '220万', '运输从业人员', '到场10次以上', 11, '黑河市爱辉分局', '黑河', '外市', 'general', '三级', 27),
];

export const people = [
  ...seedPeople,
  ...createSyntheticPeople('organizers', 11, 100),
  ...createSyntheticPeople('responders', 19, 200),
  ...createSyntheticPeople('general', 127, 300),
  ...createSyntheticPeople('watch', 21, 500),
];

export const regionStats = [
  ['通河分局', 0],
  ['巴彦分局', 1],
  ['宾县分局', 1],
  ['木兰分局', 1],
  ['尚志市局', 2],
  ['依兰分局', 2],
  ['阿城分局', 3],
  ['双城分局', 3],
  ['五常市局', 7],
  ['平房分局', 10],
  ['呼兰分局', 11],
  ['松北分局', 32],
  ['道外分局', 49],
  ['香坊分局', 91],
  ['道里分局', 215],
  ['南岗分局', 282],
  ['大兴安岭', 0],
  ['七台河', 3],
  ['鹤岗', 4],
  ['双鸭山', 5],
  ['伊春', 5],
  ['黑河', 6],
  ['鸡西', 6],
  ['佳木斯', 9],
  ['牡丹江', 11],
  ['齐齐哈尔', 14],
  ['绥化', 19],
  ['大庆', 38],
];

export const regionRows = [
  regionStats.slice(0, 16),
  regionStats.slice(16),
];

export const riskBars = [
  { label: '一级', values: [92, 12, 9, 7] },
  { label: '二级', values: [26, 8, 3, 2] },
  { label: '三级', values: [15, 9, 4, 3] },
];

export const clinicBars = [
  94, 76, 70, 64, 58, 52, 47, 42, 38, 34, 30, 27, 24, 22, 19, 17,
  15, 13, 11, 10, 8, 7, 6, 5,
];

function person(id, name, gender, age, amount, occupation, behavior, visits, policeStation, district, locality, group, risk, avatarIndex) {
  return {
    id,
    name,
    gender,
    age,
    amount,
    occupation,
    behavior,
    visits,
    policeStation,
    district,
    locality,
    group,
    risk,
    avatarIndex,
    idNumber: `2301********${String(avatarIndex + 11).padStart(2, '0')}`,
    phone: `138****${String(3200 + avatarIndex).padStart(4, '0')}`,
    address: `${district}辖区登记地址`,
    latestNote: '个人信息字段待补充',
  };
}

function createSyntheticPeople(group, count, startIndex) {
  const surnames = ['周', '郑', '王', '冯', '陈', '蒋', '韩', '杨', '赵', '钱', '许', '宋', '何', '罗', '梁', '谢'];
  const givenNames = ['明', '华', '军', '芳', '勇', '霞', '平', '敏', '强', '艳', '红', '杰', '宁', '琳', '涛', '颖'];
  const districts = [
    ['南岗', '南岗分局奋斗派出所'],
    ['道里', '道里分局丽江路派出所'],
    ['香坊', '香坊分局红旗大街派出所'],
    ['道外', '道外分局南直派出所'],
    ['松北', '松北分局松北派出所'],
    ['齐齐哈尔', '齐齐哈尔市龙沙分局'],
    ['佳木斯', '佳木斯市前进分局'],
    ['黑河', '黑河市爱辉分局'],
  ];
  const occupations = ['退休', '个体经营', '自由职业', '企业职工', '无业', '运输从业人员', '商业服务人员'];
  const behaviors = {
    organizers: ['网上串联', '现场组织、到场40次以上', '网上串联、现场组织'],
    responders: ['群内响应', '发表过极端言论或意见领袖', '到场10次以上，并群内响应'],
    general: ['有到场行为', '到场登记', '群内响应后到场'],
    watch: ['意见领袖', '群内响应', '发表过极端言论或意见领袖'],
  };
  const risks = ['一级', '二级', '三级'];

  return Array.from({ length: count }, (_, index) => {
    const sourceIndex = startIndex + index;
    const gender = index % 3 === 0 ? '男' : '女';
    const district = districts[index % districts.length];
    const locality = index % 7 === 5 || index % 7 === 6 ? '外市' : '本市';
    const name = `${surnames[index % surnames.length]}${givenNames[(index * 3) % givenNames.length]}${givenNames[(index * 5 + 2) % givenNames.length]}`;
    const amount = `${180 + (index % 12) * 70}万`;
    const visits = group === 'organizers'
      ? 40 + (index % 16)
      : group === 'responders'
        ? 10 + (index % 22)
        : index % 18;

    return person(
      `p${sourceIndex}`,
      name,
      gender,
      38 + (index % 34),
      amount,
      occupations[index % occupations.length],
      behaviors[group][index % behaviors[group].length],
      visits,
      district[1],
      district[0],
      locality,
      group,
      risks[index % risks.length],
      sourceIndex,
    );
  });
}
