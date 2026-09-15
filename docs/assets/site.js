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

  /* Release Notes 是 Markdown，这里只处理 `## 标题` 与 `- 条目` 两种写法 */
  function renderNotes(markdown) {
    var lines = String(markdown || '').split('\n');
    var html = '';
    var inList = false;

    function closeList() {
      if (inList) { html += '</ul>'; inList = false; }
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
        if (!inList) { html += '<ul>'; inList = true; }
        html += '<li>' + escapeHtml(bullet[1].replace(/\*\*/g, '')) + '</li>';
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

  function loadChangelog() {
    var host = document.querySelector('[data-changelog]');
    if (!host) return;

    apiGet('/releases?per_page=20')
      .then(function (releases) {
        var published = (releases || []).filter(function (r) { return !r.draft; });
        if (!published.length) {
          host.innerHTML = '<p class="notice">还没有发布过版本。</p>';
          return;
        }
        host.innerHTML = published.map(function (release) {
          var version = cleanVersion(release.tag_name);
          var apk = findApk(release);
          return '' +
            '<article class="release">' +
              '<header>' +
                '<h3>' + escapeHtml('v' + version) + '</h3>' +
                '<time>' + escapeHtml(formatDate(release.published_at)) + '</time>' +
                (release.prerelease ? '<span class="tag-chip">预发布</span>' : '') +
              '</header>' +
              '<div class="notes">' + (renderNotes(release.body) || '<p class="muted">这个版本没有写更新说明。</p>') + '</div>' +
              (apk
                ? '<p class="small"><a href="' + escapeHtml(apk.browser_download_url) + '">下载 ' + escapeHtml(apk.name) + '</a></p>'
                : '') +
            '</article>';
        }).join('');
      })
      .catch(function () {
        showFailure('[data-changelog]', '暂时取不到更新日志，可以直接去 GitHub Releases 查看。');
      });
  }

  document.addEventListener('DOMContentLoaded', function () {
    if (document.querySelector('[data-latest-version]') || document.querySelector('[data-download]')) {
      loadLatest();
    }
    loadChangelog();
  });
})();
