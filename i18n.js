/* 官网页面的简繁切换。
 * 简体中文是唯一来源，繁体由运行时转换；
 * 版本号统一用 #V# 占位，发版时无需改动本文件。
 * 归一化 = 把 a.b.c 形式的版本号替换成 #V#。
 */
window.CDESK_I18N = {
  KEY: 'cdesk_lang',
  TABLE: {},
  norm: function (s) { return String(s).replace(/\d+\.\d+\.\d+/g, '#V#'); },
  TRAD: {
    '简': '簡', '体': '體', '语': '語', '开': '開', '关': '關', '闭': '閉', '网': '網',
    '络': '絡', '传': '傳', '输': '輸', '设': '設', '应': '應', '纯': '純', '净': '淨',
    '车': '車', '机': '機', '电': '電', '视': '視', '无': '無', '广': '廣', '荐': '薦',
    '内': '內', '置': '置', '纸': '紙', '悬': '懸', '浮': '浮', '区': '區', '与': '與',
    '馈': '饋', '问': '問', '题': '題', '号': '號', '复': '複', '制': '製', '发': '發',
    '测': '測', '试': '試', '载': '載', '轻': '輕', '量': '量', '全': '全', '功': '功',
    '清': '清', '理': '理', '管': '管', '激': '激', '活': '活', '敏': '敏', '后': '後',
    '执': '執', '装': '裝', '见': '見', '数': '數', '据': '據', '权': '權', '审': '審',
    '计': '計', '隐': '隱', '声': '聲', '显': '顯', '长': '長', '备': '備', '级': '級',
    '独': '獨', '动': '動', '态': '態', '规': '規', '则': '則', '联': '聯', '续': '續',
    '发': '發', '现': '現', '样': '樣', '为': '為', '这': '這', '个': '個', '从': '從',
    '进': '進', '间': '間', '来': '來', '时': '時', '场': '場', '将': '將', '过': '過',
    '还': '還', '种': '種', '专': '專', '项': '項', '当': '當', '华': '華', '国': '國',
    '东': '東', '门': '門', '页': '頁', '对': '對', '错': '錯', '认': '認', '识': '識',
    '词': '詞', '义': '義', '说': '說', '读': '讀', '写': '寫', '学': '學', '习': '習',
    '导': '導', '导': '導', '馆': '館', '图': '圖', '画': '畫', '码': '碼', '扫': '掃',
    '项': '項', '条': '條', '优': '優', '势': '勢', '处': '處', '备': '備', '认': '認',
    '许': '許', '证': '證', '签': '簽', '启': '啟', '动': '動', '级': '級', '变': '變',
    '换': '換', '旧': '舊', '统': '統', '软': '軟', '块': '塊', '线': '線', '库': '庫',
    '错': '錯', '帮': '幫', '须': '須', '须': '須', '处': '處', '复': '複', '并': '並'
  },
  toTrad: function (s) {
    var phrases = {
      '局域网': '區域網路', '传文件': '傳檔案', '局域网传输': '區域網路傳輸',
      '悬浮窗': '懸浮視窗', '轻享版': '輕享版', '全功能版': '全功能版',
      '简体中文': '簡體中文', '繁体中文': '繁體中文', '卸载': '解除安裝', '安装': '安裝',
      '设置': '設定', '应用程序': '應用程式', '应用管理': '應用管理', '应用': '應用',
      '数据': '資料', '设备': '裝置', '文件': '檔案', '浏览器': '瀏覽器', '天气': '天氣',
      '音乐': '音樂', '视频': '影片', '影视': '影視', '记录': '紀錄', '图标': '圖示',
      '功能': '功能', '内容': '內容', '问题': '問題', '反馈': '回饋', '赞赏': '贊助',
      '打赏': '打賞', '复制': '複製', '失败': '失敗', '成功': '成功', '确认': '確認',
      '支持': '支援', '版本': '版本', '下载': '下載', '更新': '更新', '测试': '測試',
      '权限': '權限', '隐私': '隱私', '声明': '聲明', '免责声明': '免責聲明',
      '签名': '簽章', '证书': '憑證', '默认': '預設', '自动': '自動', '推荐': '推薦',
      '发现': '發現', '开发': '開發', '关闭': '關閉', '开启': '開啟', '后台': '背景',
      '页面': '頁面', '常见': '常見', '指南': '指南', '启动': '啟動', '存储': '儲存',
      '缓存': '快取', '遥控': '遙控', '方向键': '方向鍵', '伙伴': '夥伴', '联系': '聯絡',
      '免费': '免費', '广告': '廣告', '无广告': '無廣告', '无推荐流': '無推薦動態',
      '车机': '車機', '电视': '電視', '语言': '語言', '切换': '切換', '使用': '使用',
      '直接': '直接', '之后': '之後', '以后': '以後', '这个': '這個', '这样': '這樣',
      '所有': '所有', '其它': '其他', '其他': '其他', '完整': '完整', '服务': '服務',
      '自适配': '自動適配', '并': '並', '与': '與', '为': '為', '这': '這', '个': '個',
      '从': '從', '后': '後', '发': '發', '开': '開', '关': '關', '网': '網', '络': '絡',
      '传': '傳', '输': '輸', '设': '設', '应': '應', '纯': '純', '净': '淨', '车': '車',
      '机': '機', '电': '電', '视': '視', '无': '無', '广': '廣', '荐': '薦', '内': '內',
      '纸': '紙', '悬': '懸', '区': '區', '馈': '饋', '问': '問', '题': '題', '号': '號',
      '复': '複', '测': '測', '试': '試', '载': '載', '轻': '輕', '数': '數', '据': '據',
      '权': '權', '审': '審', '计': '計', '隐': '隱', '声': '聲', '显': '顯', '长': '長',
      '备': '備', '级': '級', '独': '獨', '动': '動', '态': '態', '规': '規', '则': '則',
      '联': '聯', '续': '續', '现': '現', '样': '樣', '进': '進', '间': '間', '来': '來',
      '时': '時', '场': '場', '将': '將', '过': '過', '还': '還', '种': '種', '专': '專',
      '项': '項', '当': '當', '华': '華', '国': '國', '东': '東', '门': '門', '页': '頁',
      '对': '對', '错': '錯', '认': '認', '识': '識', '词': '詞', '义': '義', '说': '說',
      '读': '讀', '写': '寫', '学': '學', '习': '習', '导': '導', '馆': '館', '图': '圖',
      '画': '畫', '码': '碼', '扫': '掃', '优': '優', '势': '勢', '处': '處', '许': '許',
      '证': '證', '签': '簽', '启': '啟', '变': '變', '换': '換', '旧': '舊', '统': '統',
      '软': '軟', '块': '塊', '线': '線', '库': '庫', '帮': '幫', '须': '須'
    };
    var keys = Object.keys(phrases).sort(function (a, b) { return b.length - a.length; });
    var text = String(s);
    for (var i = 0; i < keys.length; i++) text = text.split(keys[i]).join(phrases[keys[i]]);
    return text;
  }
};


/* ---- 运行时 ---- */
(function () {
  var I = window.CDESK_I18N;
  var KEY = I.KEY;
  var SEL = 'h1,h2,h3,p,li,summary,figcaption,.brand-text,.device-title,.device-tag,'
          + '.btn,.text-link,.link-quiet,nav.site-nav a,.footer-links a';

  var nodes = [];
  var pageVer = '';

  // 版本号以运行时注入的为准（site-version.js 从 version.json 取），
  // 拿不到才回落到 HTML 里写死的那个。
  function currentVer() {
    var v = document.documentElement.getAttribute('data-app-version');
    if (v) return v;
    var h = document.querySelector('#download h2');
    var m = h ? h.textContent.match(/\d+\.\d+\.\d+/) : null;
    return m ? m[0] : '';
  }

  function collect() {
    pageVer = currentVer();

    var list = document.querySelectorAll(SEL);
    for (var i = 0; i < list.length; i++) {
      var el = list[i];
      if (el.closest('[data-no-i18n]')) continue;
      // 群号那行里嵌着「复制」按钮，替换 innerHTML 会把它的点击事件一起丢掉
      nodes.push({ el: el, zh: el.innerHTML, copyBtn: !!el.querySelector('[data-copy]') });
    }
  }

  function translate(zhHtml) {
    return I.toTrad(zhHtml);
  }

  function apply(lang) {
    var trad = lang === 'zh-TW';
    for (var i = 0; i < nodes.length; i++) {
      var n = nodes[i];
      if (!trad) {
        var back = n.zh;
        // 基线里可能存着旧的写死版本号，还原时按当前版本替换
        var v = currentVer();
        if (v) back = back.replace(/\d+\.\d+\.\d+/g, v);
        if (n.el.innerHTML !== back) n.el.innerHTML = back;
        continue;
      }
      if (n.copyBtn) {
        if (n.el.firstChild && n.el.firstChild.nodeType === 3) {
          n.el.firstChild.nodeValue = trad ? '群號 ' : '群号 ';
        }
        continue;
      }
      var t = translate(n.zh);
      if (t !== null && n.el.innerHTML !== t) n.el.innerHTML = t;
    }

    document.documentElement.lang = trad ? 'zh-TW' : 'zh-CN';
    document.title = trad
      ? 'C桌面 CDesk — 車機、平板、盒子、電視的純淨桌面'
      : 'C桌面 CDesk — 车机、平板、盒子、电视的纯净桌面';

    var meta = document.querySelector('meta[name="description"]');
    if (meta) meta.setAttribute('content', trad
      ? 'C桌面（CDesk）是給車機、平板、盒子、電視等安卓設備用的一塊純淨桌面：無廣告、無推薦流、內置 4K 桌布、網速懸浮視窗、區域網路傳檔案。'
      : 'C桌面（CDesk）是给车机、平板、盒子、电视等安卓设备用的一块纯净桌面：无广告、无推荐流、内置 4K 壁纸、网速悬浮窗、局域网传文件。');

    var btns = document.querySelectorAll('.lang-toggle');
    for (var b = 0; b < btns.length; b++) {
      btns[b].textContent = trad ? '简' : '繁';
      btns[b].setAttribute('aria-label', trad ? '切换到简体中文' : '切换到繁体中文');
      btns[b].setAttribute('aria-pressed', trad ? 'true' : 'false');
    }

    // 版本号 / SHA / 下载链接由 site-version.js 注入，
    // 上面的 innerHTML 整体替换会把它们冲掉，这里通知它重填。
    try {
      document.dispatchEvent(new CustomEvent('cdesk:lang', { detail: { lang: trad ? 'zh-TW' : 'zh-CN' } }));
    } catch (e) { /* 老浏览器没有 CustomEvent 就跳过，页面仍可读 */ }
  }

  function stored() { try { return localStorage.getItem(KEY); } catch (e) { return null; } }
  function save(v) { try { localStorage.setItem(KEY, v); } catch (e) {} }

  function fromQuery() {
    var m = location.search.match(/[?&]lang=([^&]+)/);
    if (!m) return null;
    var value = decodeURIComponent(m[1]).toLowerCase();
    return value === 'trad' || value === 'zh-tw' || value === 'zh' ? 'zh-TW' : 'zh-CN';
  }

  // 没有 IP 地理库，也不该为切语言去查 IP：用浏览器语言判断，省一次外部请求
  function guess() {
    var nav = (navigator.languages && navigator.languages[0]) || navigator.language || '';
    return /^zh[-_]TW/i.test(nav) || /^zh[-_]HK/i.test(nav) ? 'zh-TW' : 'zh-CN';
  }

  function init() {
    collect();
    var q = fromQuery();
    var saved = stored();
    apply(q || (saved === 'zh-TW' ? 'zh-TW' : saved === 'zh-CN' ? 'zh-CN' : saved === 'en' ? 'zh-CN' : guess()));
    if (q) save(q);

    var btns = document.querySelectorAll('.lang-toggle');
    for (var i = 0; i < btns.length; i++) {
      btns[i].addEventListener('click', function () {
        var next = document.documentElement.lang === 'zh-TW' ? 'zh-CN' : 'zh-TW';
        save(next);
        apply(next);
      });
    }
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
  else init();
})();
