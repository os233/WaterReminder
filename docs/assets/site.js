/*
 * WaterReminder 官网脚本：只做一件事 —— 从 GitHub Releases API 取版本信息填进页面。
 * 无依赖、无构建。API 挂了就显示兜底文案，不影响页面其余部分。
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
        if (!res.ok) throw new Error('HTTP ' + res.status);
        return res.json();
      });
  }

  function findApk(release) {
    var assets = release.assets || [];
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

  function fillLatest(release) {
    var version = cleanVersion(release.tag_name);
    var apk = findApk(release);
    var date = formatDate(release.published_at);

    setText('[data-latest-version]', 'v' + version);
    setText('[data-latest-date]', date ? '发布于 ' + date : '');
    setText('[data-latest-title]', release.name || ('WaterReminder v' + version));

    document.querySelectorAll('[data-download]').forEach(function (el) {
      if (apk) {
        el.href = apk.browser_download_url;
        el.setAttribute('aria-disabled', 'false');
      } else {
        el.href = RELEASES_PAGE;
      }
    });

    document.querySelectorAll('[data-release-link]').forEach(function (el) {
      el.href = release.html_url || RELEASES_PAGE;
    });

    if (apk) {
      setText('[data-latest-size]', formatSize(apk.size) + (apk.download_count ? ' · 已下载 ' + apk.download_count + ' 次' : ''));
      var digest = apk.digest ? String(apk.digest).replace(/^sha256:/, '') : '';
      if (digest) setText('[data-latest-sha256]', 'SHA-256 ' + digest);
    }

    document.querySelectorAll('[data-latest-notes]').forEach(function (el) {
      var html = renderNotes(release.body);
      el.innerHTML = html || '<p class="muted">这个版本没有写更新说明。</p>';
    });
  }

  function showFailure(selector, message) {
    document.querySelectorAll(selector).forEach(function (el) {
      el.innerHTML = '<p class="notice">' + escapeHtml(message) + '</p>';
    });
  }

  function showLatestFailure() {
    setText('[data-latest-version]', '获取失败');
    document.querySelectorAll('[data-download]').forEach(function (el) {
      el.href = RELEASES_PAGE;
    });
    showFailure('[data-latest-notes]', '暂时取不到最新版本信息，可以直接去 GitHub Releases 查看。');
  }

  /*
   * API 失败时的兜底：读同域的 version.json。
   *
   * 未认证的 GitHub API 只有 60 次/小时，而且是按**出口 IP 共享**的 —— 共用网络
   * （公司、校园网、运营商 NAT）下很容易被别人用光，访客就会看到「获取失败」。
   * 同域文件没有配额限制、永远可达，内容也够用（版本号、下载链接、摘要、更新说明）。
   * 代价是缺 APK 大小与下载次数（version.json 里没有这两项）。
   */
  function loadLatestFromStatic() {
    if (!VERSION_JSON) return showLatestFailure();

    fetch(VERSION_JSON)
      .then(function (res) {
        if (!res.ok) throw new Error('HTTP ' + res.status);
        return res.json();
      })
      .then(function (v) {
        if (!v || !v.versionName) throw new Error('version.json 结构不符');
        setText('[data-latest-version]', 'v' + cleanVersion(v.versionName));
        document.querySelectorAll('[data-download]').forEach(function (el) {
          el.href = v.apkUrl || RELEASES_PAGE;
        });
        if (v.sha256) setText('[data-latest-sha256]', 'SHA-256 ' + v.sha256);
        document.querySelectorAll('[data-latest-notes]').forEach(function (el) {
          el.innerHTML = renderNotes(v.changelog) || '<p class="muted">这个版本没有写更新说明。</p>';
        });
      })
      .catch(showLatestFailure);
  }

  function loadLatest() {
    apiGet('/releases/latest')
      .then(fillLatest)
      .catch(loadLatestFromStatic);
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
        '<div class="notes">' + (renderNotes(o.notes) || '<p class="muted">这个版本没有写更新说明。</p>') + '</div>' +
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
   * Release 被删掉了，而版本清单里还有东西，比直接写「还没有发布过版本」诚实。
   *
   * 代价：version.json 只有一条记录，所以这里只能显示最新正式版，不是完整历史。
   */
  function loadChangelogFromStatic() {
    var host = document.querySelector('[data-changelog]');
    if (!host) return;

    if (!VERSION_JSON) {
      showFailure('[data-changelog]', '暂时取不到更新日志，可以直接去 GitHub Releases 查看。');
      return;
    }

    fetch(VERSION_JSON)
      .then(function (res) {
        if (!res.ok) throw new Error('HTTP ' + res.status);
        return res.json();
      })
      .then(function (v) {
        if (!v || !v.versionName) throw new Error('version.json 结构不符');
        var apkUrl = v.apkUrl || '';
        host.innerHTML =
          releaseCard({
            version: 'v' + cleanVersion(v.versionName),
            notes: v.changelog,
            apkUrl: apkUrl,
            apkName: apkUrl ? decodeURIComponent(apkUrl.split('/').pop().split('?')[0]) : '',
            static: true
          }) +
          '<p class="notice">GitHub 接口暂时不可用，上面这条来自本站的版本清单，只含最新正式版；' +
          '完整历史请到 <a href="' + RELEASES_PAGE + '">GitHub Releases</a> 查看。</p>';
      })
      .catch(function () {
        showFailure('[data-changelog]', '暂时取不到更新日志，可以直接去 GitHub Releases 查看。');
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
