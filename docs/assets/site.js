/*
 * WaterReminder 官网脚本：只做一件事 —— 把「当前可下载的正式版本」填进页面。
 * 无依赖、无构建。
 *
 * 唯一权威是 GitHub Releases：站点文件（version.json）只提供「期望的版本号」，
 * 版本号对应的 Release / asset 必须真实存在才会被展示。两者对不上时页面显示
 * 「暂无可下载版本」，而不是把清单里的数字和一条会 404 的链接当版本信息发出去。
 * 这一条是刻意设计：Release 被删而清单没跟着改，机器判断不出来，只有人看得见。
 */
(function () {
  'use strict';

  var OWNER = 'os233';
  var REPO = 'WaterReminder';
  var API = 'https://api.github.com/repos/' + OWNER + '/' + REPO;
  var RELEASES_PAGE = 'https://github.com/' + OWNER + '/' + REPO + '/releases';

  /* version.json 与 assets/ 同级。用 site.js 自己的 URL 推路径，这样首页（/）和
     子页面（/download/ 等）都能拿到正确的地址 —— 直接写 'version.json' 的话，
     子页面会把它解析成 /download/version.json。 */
  var VERSION_JSON = (function () {
    var s = document.currentScript;
    if (!s || !s.src) return null;
    try {
      return new URL('../version.json', s.src).href;
    } catch (e) {
      return null;
    }
  })();

  function escapeHtml(text) {
    return String(text)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  function apiGet(path) {
    return fetch(API + path, { headers: { Accept: 'application/vnd.github+json' } })
      .then(function (res) {
        if (res.status === 404) return null;
        if (!res.ok) throw new Error('HTTP ' + res.status);
        return res.json();
      });
  }

  function fetchJson(url) {
    return fetch(url).then(function (res) {
      if (!res.ok) throw new Error('HTTP ' + res.status);
      return res.json();
    });
  }

  function findApk(release) {
    var assets = (release && release.assets) || [];
    for (var i = 0; i < assets.length; i++) {
      if (/\.apk$/i.test(assets[i].name)) return assets[i];
    }
    return null;
  }

  function formatDate(iso) {
    if (!iso) return '';
    var d = new Date(iso);
    if (isNaN(d.getTime())) return '';
    var pad = function (n) { return n < 10 ? '0' + n : String(n); };
    return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate());
  }

  function formatSize(bytes) {
    if (!bytes) return '';
    return (bytes / 1024 / 1024).toFixed(1) + ' MB';
  }

  function cleanVersion(tag) {
    return String(tag || '').replace(/^v/, '');
  }

  /* 更新说明是轻 Markdown。version.json 的 changelog 用中文 + `1. 2. 3.` 编号写，
     Release body 也是它（CI 从 changelog 生成），所以除了 `## 标题` 与 `- 条目`，
     还得认有序列表 —— 否则每条都掉进普通段落分支，看不出列表结构。 */
  function renderNotes(markdown) {
    var lines = String(markdown || '').split('\n');
    var html = '';
    var listTag = null;

    function closeList() {
      if (listTag) { html += '</' + listTag + '>'; listTag = null; }
    }

    function openList(tag) {
      if (listTag === tag) return;
      closeList();
      html += '<' + tag + '>';
      listTag = tag;
    }

    lines.forEach(function (raw) {
      var line = raw.trim();
      if (!line || line.indexOf('<!--') === 0) return;

      if (/^#{1,6}\s/.test(line)) {
        closeList();
        html += '<h4>' + escapeHtml(line.replace(/^#+\s*/, '')) + '</h4>';
        return;
      }

      var bullet = line.match(/^[-*+]\s+(.*)$/);
      if (bullet) {
        openList('ul');
        html += '<li>' + escapeHtml(bullet[1].replace(/\*\*/g, '')) + '</li>';
        return;
      }

      /* 有序列表：`1. xxx` / `1、xxx` / `1) xxx` 都认 */
      var ordered = line.match(/^\d+\s*[.、)]\s*(.*)$/);
      if (ordered) {
        openList('ol');
        html += '<li>' + escapeHtml(ordered[1].replace(/\*\*/g, '')) + '</li>';
        return;
      }

      closeList();
      html += '<p>' + escapeHtml(line.replace(/\*\*/g, '')) + '</p>';
    });

    closeList();
    return html;
  }

  function setText(selector, value) {
    document.querySelectorAll(selector).forEach(function (el) {
      el.textContent = value;
    });
  }

  function linkAll(selector, href) {
    document.querySelectorAll(selector).forEach(function (el) {
      el.href = href;
    });
  }

  function notesHtml(markdown) {
    return renderNotes(markdown) || '<p class="muted">这个版本没有写更新说明。</p>';
  }

  /* 站点清单（version.json）。取不到就返回 null —— 页面按「只能靠线上数据」处理。 */
  function loadManifest() {
    if (!VERSION_JSON) return Promise.resolve(null);
    return fetchJson(VERSION_JSON).catch(function () { return null; });
  }

  /*
   * 按清单里的 versionName 找对应的正式 Release。
   *
   * 走 tags 接口而不是 /releases/latest：latest 完全不看版本号，Release 被删光、
   * 只剩一个预发布时，它会把 beta 的 tag 和它的 asset 当成「最新版」交给首页 ——
   * 那正是本项目发生过的状态。tag 不存在返回 404 → apiGet 给 null，判定为无版本。
   */
  function findStableRelease(expectedTag) {
    return apiGet('/releases/tags/' + encodeURIComponent(expectedTag)).then(function (r) {
      if (!r || r.draft) return null;
      return findApk(r) ? r : null;
    });
  }

  function renderNoRelease(title) {
    setText('[data-latest-version]', title);
    setText('[data-latest-date]', '');
    setText('[data-latest-size]', '');
    setText('[data-latest-sha256]', '');
    linkAll('[data-download]', RELEASES_PAGE);
    document.querySelectorAll('[data-download]').forEach(function (el) {
      el.setAttribute('aria-disabled', 'true');
    });
    linkAll('[data-release-link]', RELEASES_PAGE);
    document.querySelectorAll('[data-latest-notes]').forEach(function (el) {
      el.innerHTML = '<p class="notice">GitHub 上没有可下载的正式版本（预发布版不计入）。' +
        '按钮已指向 <a href="' + RELEASES_PAGE + '">Releases 页面</a>。</p>';
    });
  }

  function renderLatest(apk, meta) {
    var version = meta.version || '';

    setText('[data-latest-version]', version ? 'v' + version : '');
    setText('[data-latest-date]', meta.date ? '发布于 ' + meta.date : '');
    setText('[data-latest-title]', meta.title || ('WaterReminder v' + version));

    linkAll('[data-download]', apk.browser_download_url);
    document.querySelectorAll('[data-download]').forEach(function (el) {
      el.setAttribute('aria-disabled', 'false');
    });
    if (meta.htmlUrl) linkAll('[data-release-link]', meta.htmlUrl);

    setText('[data-latest-size]',
      formatSize(apk.size) + (apk.download_count ? ' · 已下载 ' + apk.download_count + ' 次' : ''));

    var digest = apk.digest ? String(apk.digest).replace(/^sha256:/, '') : (meta.sha256 || '');
    setText('[data-latest-sha256]', digest ? 'SHA-256 ' + digest : '');

    document.querySelectorAll('[data-latest-notes]').forEach(function (el) {
      var html = notesHtml(meta.notes);
      if (meta.stale) {
        html += '<p class="notice">这条版本说明来自本站的版本清单，与线上 Release 的说明可能不同步。</p>';
      }
      el.innerHTML = html;
    });
  }

  function renderUnreachable(title) {
    setText('[data-latest-version]', title);
    setText('[data-latest-date]', '');
    linkAll('[data-download]', RELEASES_PAGE);
    document.querySelectorAll('[data-latest-notes]').forEach(function (el) {
      el.innerHTML = '<p class="notice">暂时取不到版本信息，可以先去 ' +
        '<a href="' + RELEASES_PAGE + '">GitHub Releases</a> 看看。</p>';
    });
  }

  function loadLatest() {
    loadManifest().then(function (manifest) {
      /* 清单缺失 → 没有「期望的版本号」可比对，只能问 GitHub 自己 */
      if (!manifest || !manifest.versionName) {
        return apiGet('/releases/latest').then(function (release) {
          if (!release || release.draft) return renderNoRelease('暂无正式版本');
          var apk = findApk(release);
          if (!apk) return renderNoRelease('暂无正式版本');
          return renderLatest(apk, {
            version: cleanVersion(release.tag_name),
            date: formatDate(release.published_at),
            title: release.name,
            notes: release.body,
            htmlUrl: release.html_url
          });
        });
      }

      var version = cleanVersion(manifest.versionName);
      return findStableRelease('v' + version).then(function (release) {
        /* 这里只写「尚无可下载的安装包」，不带版本号：首页 hero 区在该槽位前面
           已经写死了「最新版本」四个字，拼上 `v0.0.1` 会读成
           「最新版本 v0.0.1 尚无可下载的安装包」—— 既和下方「最新版本」卡片重复，
           也容易被误读成「v0.0.1 是最新版」。 */
        if (!release) return renderNoRelease('尚无可下载的安装包');
        var apk = findApk(release);
        return renderLatest(apk, {
          version: version,
          date: formatDate(release.published_at),
          title: release.name,
          notes: release.body,
          htmlUrl: release.html_url
        });
      });
    }).catch(function () {
      /* 清单读到了、但 GitHub 接口不可用 → 查不了 Release，就不知道清单数字是否属实。
         这时宁可只说「取不到」，也不把清单当权威 —— 否则 Release 缺失时页面会
         显示一个不存在的版本号和一条 404 链接，比不显示更糟。 */
      renderUnreachable('获取失败');
    });
  }

  function showFailure(selector, message) {
    document.querySelectorAll(selector).forEach(function (el) {
      el.innerHTML = '<p class="notice">' + escapeHtml(message) + '</p>';
    });
  }

  /* 单条更新日志的卡片。API 与 version.json 兜底共用同一套结构，
     免得两个数据源渲染出不一样的版式。 */
  function releaseCard(o) {
    return '' +
      '<article class="release">' +
        '<header>' +
          '<h3>' + escapeHtml(o.version) + '</h3>' +
          (o.date ? '<time>' + escapeHtml(o.date) + '</time>' : '') +
          (o.prerelease ? '<span class="tag-chip">预发布</span>' : '') +
          (o.static ? '<span class="tag-chip static-chip">本站清单</span>' : '') +
        '</header>' +
        '<div class="notes">' + notesHtml(o.notes) + '</div>' +
        (o.apkUrl
          ? '<p class="small"><a href="' + escapeHtml(o.apkUrl) + '">下载 ' + escapeHtml(o.apkName || 'APK') + '</a></p>'
          : '') +
      '</article>';
  }

  /*
   * 更新日志页的兜底：API 取不到时读同域的 version.json。
   *
   * 首页与下载页一直有这条退路，更新日志页原本没有 —— 同一个站点在限流时会
   * 自相矛盾：旁边两页照样显示版本号，更新日志页却只剩一句「暂时取不到」，
   * 看起来就像「没有发布过任何版本」。API 返回空列表时同样走这里：那多半是
   * Release 被删掉了。用「本站清单」标签说明数据来源，不冒充 Release 记录。
   *
   * 代价：version.json 只有一条记录，所以这里只能显示最新正式版，不是完整历史。
   */
  function loadChangelogFromStatic() {
    var host = document.querySelector('[data-changelog]');
    if (!host) return;

    loadManifest().then(function (v) {
      if (!v || !v.versionName) {
        showFailure('[data-changelog]', '暂时取不到更新日志，可以直接去 GitHub Releases 查看。');
        return;
      }
      var apkUrl = v.apkUrl || '';
      host.innerHTML =
        releaseCard({
          version: 'v' + cleanVersion(v.versionName),
          notes: v.changelog,
          apkUrl: apkUrl,
          apkName: apkUrl ? decodeURIComponent(apkUrl.split('/').pop().split('?')[0]) : '',
          static: true
        }) +
        '<p class="notice">这条记录来自本站的版本清单，只含最新正式版；' +
        '完整历史请到 <a href="' + RELEASES_PAGE + '">GitHub Releases</a> 查看。</p>';
    });
  }

  function loadChangelog() {
    var host = document.querySelector('[data-changelog]');
    if (!host) return;

    apiGet('/releases?per_page=20')
      .then(function (releases) {
        var published = (releases || []).filter(function (r) { return !r.draft; });
        if (!published.length) return loadChangelogFromStatic();

        host.innerHTML = published.map(function (release) {
          var apk = findApk(release);
          return releaseCard({
            version: 'v' + cleanVersion(release.tag_name),
            date: formatDate(release.published_at),
            prerelease: !!release.prerelease,
            notes: release.body,
            apkUrl: apk ? apk.browser_download_url : '',
            apkName: apk ? apk.name : ''
          });
        }).join('');
      })
      .catch(loadChangelogFromStatic);
  }

  document.addEventListener('DOMContentLoaded', function () {
    if (document.querySelector('[data-latest-version]') || document.querySelector('[data-download]')) {
      loadLatest();
    }
    loadChangelog();
  });
})();
