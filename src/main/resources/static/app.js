import {
    Virtualizer,
    measureElement,
    observeWindowOffset,
    observeWindowRect,
    windowScroll
} from "https://esm.sh/@tanstack/virtual-core@3.17.8?bundle";

/* global DOMPurify, marked */

const input = document.getElementById("search");
const packageFilter = document.getElementById("package-filter");
const sourceFilter = document.getElementById("source-filter");
const bundleVersionFilter = document.getElementById("bundle-version-filter");
const bundleTypeFilter = document.getElementById("bundle-type-filter");
const results = document.getElementById("results");
const status = document.getElementById("status");
const toggleAllChangelogs = document.getElementById("toggle-all-changelogs");
const viewToggle = document.getElementById("view-toggle");
const container = document.querySelector(".container");
const filterButtons = document.querySelectorAll(".filter-btn");
const adminAccess = document.getElementById("admin-access");
const disabledSourcesToggle = document.getElementById("disabled-sources-toggle");

// Keep the denser list layout as the mobile default; desktop starts in grid view.
if (matchMedia("(max-width: 700px)").matches) {
    results.classList.remove("grid-view");
    container.classList.remove("grid-view-enabled");
    viewToggle.setAttribute("aria-pressed", "false");
    viewToggle.textContent = "Grid view";
}

// Virtualized list: TanStack Virtual renders only the visible rows of cards. Row
// heights are measured from the DOM and a single spacer keeps the document scroll
// height correct, so card positions are stable and no scroll compensation is needed.
const ROW_GAP = 16;
const MIN_COLUMN_WIDTH = 380;

// Parsed markdown/patches reuse: rows are recreated as the viewport moves, so
// re-parsing the same bundle's changelog every time dominates re-render cost.
const renderCache = new Map();
const patchListCache = new WeakMap();
const cardViewStates = new Map();
const RENDER_CACHE_LIMIT = 500;
marked.setOptions({ breaks: true, gfm: true });

function cachedMarkdown(bundleId, key, markdown) {
    const cacheKey = `${bundleId}:${key}`;
    let html = renderCache.get(cacheKey);
    if (html === undefined) {
        html = renderMarkdown(markdown);
        if (renderCache.size >= RENDER_CACHE_LIMIT) renderCache.clear();
        renderCache.set(cacheKey, html);
    }
    return html;
}

function cachedPatchList(patches) {
    let html = patchListCache.get(patches);
    if (html === undefined) {
        html = patches.map(renderPatch).join('');
        patchListCache.set(patches, html);
    }
    return html;
}

function restoreScrollTop(element, scrollTop) {
    element.scrollTop = scrollTop;
    queueMicrotask(() => {
        if (element.isConnected && !element.hidden) element.scrollTop = scrollTop;
    });
}

const track = document.getElementById("v-track");
let filteredBundles = [];
let virtualizer = null;
let rowData = [];
let lastColumns = 1;
let lastVisibleFp = "";

function layoutColumns() {
    if (!results.classList.contains("grid-view")) return 1;
    const width = results.clientWidth;
    return Math.max(1, Math.floor((width + ROW_GAP) / (MIN_COLUMN_WIDTH + ROW_GAP)));
}

function buildRows() {
    const columns = layoutColumns();
    const rows = [];
    for (let i = 0; i < filteredBundles.length; i += columns) {
        rows.push(filteredBundles.slice(i, i + columns));
    }
    return rows;
}

function estimateRowHeight() {
    // Guess only: measured row heights replace this as rows render.
    return results.classList.contains("grid-view") ? 420 : 300;
}

function renderVisible() {
    if (!virtualizer || rowData.length === 0) return;

    const items = virtualizer.getVirtualItems();
    const fp = items.map(v => `${v.index}:${Math.round(v.start)}`).join(",");
    if (fp === lastVisibleFp) {
        track.style.height = `${virtualizer.getTotalSize()}px`;
        return;
    }
    lastVisibleFp = fp;

    track.style.height = `${virtualizer.getTotalSize()}px`;
    const fragment = document.createDocumentFragment();
    const columns = layoutColumns();
    for (const item of items) {
        const rowEl = document.createElement("div");
        rowEl.className = "virtual-row";
        rowEl.dataset.index = String(item.index);
        rowEl.style.transform = `translateY(${item.start - virtualizer.options.scrollMargin}px)`;
        rowEl.style.gridTemplateColumns = `repeat(${columns}, minmax(0, 1fr))`;
        for (const card of rowData[item.index]) {
            if (card.isDisabledSourceCard) renderDisabledSource(card, rowEl);
            else renderBundle(card, rowEl);
        }
        fragment.appendChild(rowEl);
    }
    track.replaceChildren(fragment);
    virtualizer.measureElement(null);
    for (const rowEl of track.children) virtualizer.measureElement(rowEl);
    updateAllChangelogsButton();
}

function clearVirtualizer() {
    if (virtualizer) virtualizer.cleanup();
    virtualizer = null;
    rowData = [];
    lastVisibleFp = "";
    track.replaceChildren();
    track.style.height = "0px";
}


function currentAnchorBundleIndex() {
    if (!virtualizer) return null;
    const trackTop = track.getBoundingClientRect().top + window.scrollY;
    if (window.scrollY < trackTop) return null;
    const visibleRow = virtualizer.getVirtualItems()
        .find(item => item.end >= window.scrollY);
    return (visibleRow?.index ?? 0) * lastColumns;
}

function setupVirtualizer(anchorBundleIndex = null) {
    rowData = buildRows();
    lastColumns = layoutColumns();
    if (virtualizer) virtualizer.cleanup();
    virtualizer = new Virtualizer({
        count: rowData.length,
        getScrollElement: () => window,
        estimateSize: () => estimateRowHeight(),
        initialOffset: () => window.scrollY,
        scrollMargin: track.getBoundingClientRect().top + window.scrollY,
        overscan: 2,
        gap: ROW_GAP,
        observeElementRect: observeWindowRect,
        observeElementOffset: observeWindowOffset,
        scrollToFn: windowScroll,
        measureElement,
        onChange: () => renderVisible()
    });
    // The vanilla core does not attach its scroll/resize observers until
    // _willUpdate is invoked; without it no viewport rows ever render.
    virtualizer._willUpdate();
    lastVisibleFp = "";
    renderVisible();
    if (anchorBundleIndex !== null && rowData.length > 0) {
        virtualizer.scrollToIndex(
            Math.min(Math.floor(anchorBundleIndex / lastColumns), rowData.length - 1),
            { align: "start" }
        );
    }
}

const BUNDLE_FIELDS = `
    id
    bundle_type
    created_at
    description
    download_url
    signature_download_url
    is_prerelease
    version
    source {
         url
         enabled
        source_metadata: source_metadatum {
            owner_name
            owner_avatar_url
            repo_name
            repo_description
            repo_stars
            repo_pushed_at
            is_repo_archived
        }
    }
    patches {
        name
        description
        patch_packages {
            package {
                name
                version
            }
        }
    }
`;

let currentFilter = "release";
let currentSearchQuery = "";
let currentPackageFilter = "";
let currentSourceUrl = "";
let currentBundleVersion = "";
let currentBundleType = "";
let latestBundles = [];
let allBundles = [];
let sourceVersions = [];
let sourceRequestId = 0;
let bundleRequestId = 0;
let allChangelogsExpanded = true;
const ADMIN_SECRET_STORAGE_KEY = "bundle-search-admin-secret";

let adminSecret = null;
let adminEnabled = false;
let disabledSources = [];
let showingDisabledSources = false;


function storedAdminSecret() {
    try {
        return sessionStorage.getItem(ADMIN_SECRET_STORAGE_KEY);
    } catch {
        return null;
    }
}

function saveAdminSecret(secret) {
    try {
        sessionStorage.setItem(ADMIN_SECRET_STORAGE_KEY, secret);
    } catch {
        // Private browsing may disable session storage; admin access still works for this page.
    }
}

function clearAdminSecret() {
    try {
        sessionStorage.removeItem(ADMIN_SECRET_STORAGE_KEY);
    } catch {
        // Nothing to clear when session storage is unavailable.
    }
}

const renderAfterInput = debounce(() => {
    renderFilteredBundles();
}, 150);

async function restoreAdminAccess() {
    const secret = storedAdminSecret();
    if (!secret) return loadBundles();

    try {
        await requestGraphQL("query ValidateAdmin { source(limit: 1) { id } }", {}, secret);
        adminSecret = secret;
        adminEnabled = true;
        adminAccess.textContent = "Exit admin";
        await loadBundles(adminSecret);
    } catch {
        clearAdminSecret();
        await loadBundles();
    }
}

void restoreAdminAccess();

adminAccess.addEventListener("click", async () => {
    if (adminEnabled) {
        adminEnabled = false;
        adminSecret = null;
        showingDisabledSources = false;
        disabledSourcesToggle.hidden = true;
        disabledSourcesToggle.textContent = "Show disabled sources";
        clearAdminSecret();
        adminAccess.textContent = "Admin access";
        await loadBundles();
        return;
    }

    const secret = prompt("Hasura admin secret:");
    if (!secret) return;

    try {
        await requestGraphQL("query ValidateAdmin { source(limit: 1) { id } }", {}, secret);
        adminSecret = secret;
        adminEnabled = true;
        showingDisabledSources = false;
        saveAdminSecret(secret);
        adminAccess.textContent = "Exit admin";
        await loadBundles(adminSecret);
    } catch (error) {
        adminSecret = null;
        clearAdminSecret();
        alert("Invalid Hasura admin secret.");
    }
});

input.addEventListener("input", () => {
    currentSearchQuery = input.value.trim().toLowerCase();
    renderAfterInput();
});

packageFilter.addEventListener("input", () => {
    currentPackageFilter = packageFilter.value.trim().toLowerCase();
    renderAfterInput();
});

sourceFilter.addEventListener("change", () => selectSource(sourceFilter.value));

bundleVersionFilter.addEventListener("change", () => selectBundleVersion(bundleVersionFilter.value));

bundleTypeFilter.addEventListener("change", () => {
    currentBundleType = bundleTypeFilter.value;
    resetBundleSelection();
    populateVersionOptions();
    renderFilteredBundles();
});

filterButtons.forEach(btn => {
    btn.addEventListener("click", () => {
        filterButtons.forEach(button => button.classList.remove("active"));
        btn.classList.add("active");
        currentFilter = btn.dataset.filter;
        resetBundleSelection();
        populateVersionOptions();
        renderFilteredBundles();
    });
});

const toggleAllChangelogsAction = () => {
    const cards = [...results.querySelectorAll(".bundle-item")];
    allChangelogsExpanded = !allChangelogsExpanded;
    for (const state of cardViewStates.values()) state.section = undefined;
    cards.forEach(card => card.setActiveSection?.(
        allChangelogsExpanded ? "changelog" : null,
        false,
        false
    ));
    updateAllChangelogsButton();
};

toggleAllChangelogs.addEventListener("click", toggleAllChangelogsAction);

disabledSourcesToggle.addEventListener("click", () => {
    showingDisabledSources = !showingDisabledSources;
    disabledSourcesToggle.textContent = showingDisabledSources ? "Show bundles" : "Show disabled sources";
    renderFilteredBundles();
});

viewToggle.addEventListener("click", () => {
    const anchorBundleIndex = currentAnchorBundleIndex();
    const gridEnabled = results.classList.toggle("grid-view");
    container.classList.toggle("grid-view-enabled", gridEnabled);
    viewToggle.setAttribute("aria-pressed", String(gridEnabled));
    viewToggle.textContent = gridEnabled ? "List view" : "Grid view";
    // Rebuild the row grouping while keeping the first visible bundle anchored.
    setupVirtualizer(anchorBundleIndex);
    updateStatus();
});

addEventListener("resize", () => {
    const columns = layoutColumns();
    if (columns !== lastColumns) setupVirtualizer(currentAnchorBundleIndex());
});

async function loadBundles(secret = null) {
    status.textContent = "Loading bundles...";
    status.classList.add("loading");

    const query = `
        query Snapshot {
            bundle(
              where: { is_latest: { _eq: true } }
              order_by: { source: { source_metadatum: { repo_stars: desc } } }
            ) {
                ${BUNDLE_FIELDS}
            }
            ${secret ? `source {
                    url
                    enabled
                    source_metadata: source_metadatum {
                        owner_avatar_url
                        repo_description
                        repo_stars
                        repo_pushed_at
                    }
                }` : ""}
        }
    `;

    try {
        const data = await requestGraphQL(query, {}, secret);
        const bundles = data?.bundle || [];
        disabledSources = secret
            ? (data?.source || []).filter(source => !source.enabled).map(transformDisabledSource)
            : [];
        disabledSourcesToggle.hidden = !adminEnabled;

        if (bundles.length === 0 && disabledSources.length === 0) {
            latestBundles = [];
            allBundles = [];
            filteredBundles = [];
            clearVirtualizer();
            status.textContent = "No bundles available";
            return;
        }

        latestBundles = bundles.map(transformBundle);
        allBundles = latestBundles;
        populateSourceOptions();
        reconcileSourceSelection();
        populateBundleTypeOptions();
        renderFilteredBundles();
    } catch (error) {
        console.error(error);
        status.textContent = "Failed to load bundles";
    } finally {
        status.classList.remove("loading");
    }
}

async function requestGraphQL(query, variables = {}, secret = null) {
    const headers = { 'Content-Type': 'application/json' };
    if (secret) headers['X-Hasura-Admin-Secret'] = secret;
    const response = await fetch('/hasura/v1/graphql', {
        method: 'POST',
        headers,
        body: JSON.stringify({ query, variables })
    });

    if (!response.ok) {
        throw new Error(`GraphQL request failed with status ${response.status}`);
    }

    const payload = await response.json();
    if (payload.errors) {
        throw new Error(payload.errors.map(error => error.message).join('; '));
    }

    return payload.data;
}

async function selectSource(sourceUrl) {
    const requestId = ++sourceRequestId;
    bundleRequestId++;
    currentSourceUrl = sourceUrl;
    sourceVersions = [];
    resetBundleSelection();
    setVersionControlsEnabled(false);
    populateVersionOptions();
    renderFilteredBundles();

    if (!sourceUrl) return;

    status.textContent = "Loading bundle versions...";
    status.classList.add("loading");

    const query = `
        query SourceVersions($sourceUrl: String!) {
            bundle(
              where: { source: { url: { _eq: $sourceUrl } } }
              order_by: { created_at: desc }
            ) {
                version
                bundle_type
                is_prerelease
            }
        }
    `;

    try {
        const data = await requestGraphQL(query, { sourceUrl }, adminSecret);
        if (requestId !== sourceRequestId) return;

        sourceVersions = data?.bundle || [];
        setVersionControlsEnabled(true);
        populateVersionOptions();
        populateBundleTypeOptions();
        renderFilteredBundles();
    } catch (error) {
        if (requestId !== sourceRequestId) return;
        console.error(error);
        status.textContent = "Failed to load bundle versions";
    } finally {
        if (requestId === sourceRequestId) status.classList.remove("loading");
    }
}

async function selectBundleVersion(version) {
    const requestId = ++bundleRequestId;
    currentBundleVersion = version;

    if (!version) {
        allBundles = latestBundles;
        renderFilteredBundles();
        return;
    }

    status.textContent = "Loading bundle...";
    status.classList.add("loading");

    const query = `
        query BundleVersion($sourceUrl: String!, $version: String!) {
            bundle(
              where: {
                source: { url: { _eq: $sourceUrl } }
                version: { _eq: $version }
              }
              order_by: { created_at: desc }
            ) {
                ${BUNDLE_FIELDS}
            }
        }
    `;

    try {
        const data = await requestGraphQL(query, {
            sourceUrl: currentSourceUrl,
            version
        }, adminSecret);
        if (requestId !== bundleRequestId) return;

        allBundles = (data?.bundle || []).map(transformBundle);
        renderFilteredBundles();
    } catch (error) {
        if (requestId !== bundleRequestId) return;
        console.error(error);
        status.textContent = "Failed to load bundle";
    } finally {
        if (requestId === bundleRequestId) status.classList.remove("loading");
    }
}

function sourceOptionLabel(bundle) {
    const parts = sourceName(bundle.sourceUrl, bundle.ownerName, bundle.repoName).split('/');
    if (parts.length >= 2) {
        return `${parts.slice(0, -1).join('/')} / ${parts[parts.length - 1]}`;
    }
    return parts[0];
}

function populateSourceOptions() {
    const sources = new Map();
    for (const bundle of latestBundles) {
        if (!sources.has(bundle.sourceUrl)) {
            let host = '';
            try { host = new URL(bundle.sourceUrl).hostname; } catch { /* keep empty */ }
            sources.set(bundle.sourceUrl, { base: sourceOptionLabel(bundle), host });
        }
    }

    // A namespace/repo shared across hosts is ambiguous, so tag every option with that
    // base with its host.
    const baseHosts = new Map();
    for (const { base, host } of sources.values()) {
        const hosts = baseHosts.get(base) || new Set();
        if (host) hosts.add(host);
        baseHosts.set(base, hosts);
    }

    const options = [new Option("All sources", "")];
    [...sources.entries()]
        .sort((left, right) => left[1].base.localeCompare(right[1].base))
        .forEach(([url, { base, host }]) => {
            const label = baseHosts.get(base).size > 1 ? `${base} (${host})` : base;
            options.push(new Option(label, url));
        });
    sourceFilter.replaceChildren(...options);
}

function reconcileSourceSelection() {
    if (currentSourceUrl && !latestBundles.some(bundle => bundle.sourceUrl === currentSourceUrl)) {
        sourceRequestId++;
        currentSourceUrl = "";
        sourceVersions = [];
        resetBundleSelection();
        setVersionControlsEnabled(false);
        populateVersionOptions();
    }
    sourceFilter.value = currentSourceUrl;
}

function populateBundleTypeOptions() {
    const selectedType = currentBundleType;
    const types = new Set(latestBundles.map(bundle => bundle.bundleType).filter(Boolean));
    sourceVersions.forEach(bundle => {
        if (bundle.bundle_type) types.add(bundle.bundle_type);
    });

    const options = [new Option("All types", "")];
    [...types].sort().forEach(type => options.push(new Option(type, type)));
    bundleTypeFilter.replaceChildren(...options);
    bundleTypeFilter.value = selectedType;
}

function populateVersionOptions() {
    const options = [new Option("All versions", "")];
    const versions = new Map();
    sourceVersions
        .filter(bundle => currentFilter === "all" || bundle.is_prerelease === (currentFilter === "prerelease"))
        .filter(bundle => !currentBundleType || bundle.bundle_type === currentBundleType)
        .forEach(bundle => {
            if (!versions.has(bundle.version)) versions.set(bundle.version, bundle.is_prerelease);
        });
    versions.forEach((isPrerelease, version) => {
        options.push(new Option(`${version} · ${isPrerelease ? "Prerelease" : "Release"}`, version));
    });

    bundleVersionFilter.replaceChildren(...options);
    bundleVersionFilter.value = currentBundleVersion;
}

function setVersionControlsEnabled(enabled) {
    bundleVersionFilter.disabled = !enabled;
}

function resetBundleSelection() {
    bundleRequestId++;
    currentBundleVersion = "";
    bundleVersionFilter.value = "";
    allBundles = latestBundles;
}

function installAvatarFallback(card, label) {
    const image = card.querySelector(".owner-avatar");
    if (!image) return;
    const initial = escapeHtml(label.trim()[0] || "?").toUpperCase();
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><rect fill="#eee" width="100" height="100"/><text x="50" y="50" font-size="40" text-anchor="middle" dy=".3em" fill="#999">${initial}</text></svg>`;
    image.addEventListener("error", () => {
        image.src = `data:image/svg+xml,${encodeURIComponent(svg)}`;
    }, { once: true });
}

function transformDisabledSource(source) {
    const metadata = source.source_metadata || {};
    return {
        isDisabledSourceCard: true,
        sourceUrl: source.url,
        sourceName: sourceName(source.url, "", ""),
        sourceEnabled: false,
        ownerAvatarUrl: metadata.owner_avatar_url || "",
        description: metadata.repo_description,
        stars: metadata.repo_stars,
        pushedAt: metadata.repo_pushed_at
    };
}

function transformBundle(bundle) {
    const metadata = bundle.source?.source_metadata || {};

    // Transform patches to include compatiblePackages
    const patches = (bundle.patches || []).map(patch => ({
        name: patch.name,
        description: patch.description,
        compatiblePackages: (patch.patch_packages || []).map(pp => ({
            name: pp.package.name,
            versions: pp.package.version ? [pp.package.version] : []
        }))
    }));

    return {
        id: bundle.id,
        bundleType: bundle.bundle_type,
        createdAt: bundle.created_at,
        description: bundle.description,
        downloadUrl: bundle.download_url,
        signatureDownloadUrl: bundle.signature_download_url,
        isPrerelease: bundle.is_prerelease,
        version: bundle.version,
         sourceUrl: bundle.source?.url || '',
         sourceEnabled: bundle.source?.enabled ?? true,
        sourceName: sourceName(bundle.source?.url, metadata.owner_name, metadata.repo_name),
        ownerName: metadata.owner_name || '',
        ownerAvatarUrl: metadata.owner_avatar_url || '',
        repoName: metadata.repo_name || '',
        repoDescription: metadata.repo_description || '',
        repoStars: metadata.repo_stars || 0,
        repoPushedAt: metadata.repo_pushed_at,
        isRepoArchived: metadata.is_repo_archived,
        patches: patches
    };
}

function renderFilteredBundles() {
    if (showingDisabledSources) {
        filteredBundles = disabledSources;
        clearVirtualizer();
        if (filteredBundles.length === 0) {
            updateAllChangelogsButton();
            status.textContent = "No disabled sources";
            return;
        }
        document.scrollingElement.scrollTop = 0;
        setupVirtualizer();
        updateStatus();
        return;
    }

    if (allBundles.length === 0) {
        filteredBundles = [];
        clearVirtualizer();
        updateAllChangelogsButton();
        status.textContent = "No bundles available";
        return;
    }

    filteredBundles = allBundles
        .map(bundle => {
            if (currentFilter === "release" && bundle.isPrerelease) return null;
            if (currentFilter === "prerelease" && !bundle.isPrerelease) return null;
            if (currentSourceUrl && bundle.sourceUrl !== currentSourceUrl) return null;
            if (currentBundleVersion && bundle.version !== currentBundleVersion) return null;
            if (currentBundleType && bundle.bundleType !== currentBundleType) return null;

            if (currentSearchQuery) {
                const searchableText = [
                    bundle.sourceUrl,
                    bundle.ownerName,
                    bundle.repoName,
                    bundle.repoDescription,
                    bundle.version
                ].filter(Boolean).join(' ').toLowerCase();

                if (!searchableText.includes(currentSearchQuery)) {
                    return null;
                }
            }

            if (currentPackageFilter) {
                const matchingPatches = bundle.patches.filter(patch => {
                    if (!patch.compatiblePackages || patch.compatiblePackages.length === 0) {
                        return false;
                    }

                    return patch.compatiblePackages.some(pkg => {
                        const nameMatches = pkg.name.toLowerCase().includes(currentPackageFilter);
                        const versionMatches = pkg.versions?.some(v =>
                            v && v.toLowerCase().includes(currentPackageFilter)
                        );
                        return nameMatches || versionMatches;
                    });
                });

                if (matchingPatches.length === 0) return null;
                return { ...bundle, patches: matchingPatches };
            }

            return bundle;
        })
        .filter(bundle => bundle !== null);

    if (filteredBundles.length === 0) {
        clearVirtualizer();
        updateAllChangelogsButton();
        const filterParts = [];
        if (currentSearchQuery) filterParts.push(`matching "${currentSearchQuery}"`);
        if (currentPackageFilter) filterParts.push(`for package "${currentPackageFilter}"`);
        if (currentSourceUrl) filterParts.push(`from ${sourceFilter.selectedOptions[0]?.textContent}`);
        if (currentBundleVersion) filterParts.push(`at ${currentBundleVersion}`);
        if (currentBundleType) filterParts.push(`of type ${currentBundleType}`);
        if (currentFilter !== "all") filterParts.push(currentFilter);

        status.textContent = `No bundles found ${filterParts.join(' ')}`;
        return;
    }

    document.scrollingElement.scrollTop = 0;
    setupVirtualizer();
    updateStatus();
}


function updateStatus() {
    const count = filteredBundles.length;
    if (count > 0) {
        const noun = showingDisabledSources ? "source" : "bundle";
        status.textContent = `Showing ${count} ${noun}${count === 1 ? "" : "s"}`;
    }
}

function renderDisabledSource(source, target) {
    const card = document.createElement("article");
    card.className = "bundle-item disabled-source-card";
    card.innerHTML = `
        <div class="bundle-header">
            <a href="${escapeHtml(source.sourceUrl)}" target="_blank" rel="noopener" class="owner-avatar-link" aria-label="Open ${escapeHtml(source.sourceName)} repository">
                <img src="${escapeHtml(source.ownerAvatarUrl)}" alt="${escapeHtml(source.sourceName)}" class="owner-avatar" loading="lazy" decoding="async">
            </a>
            <div class="bundle-header-content">
                <div class="repo-info">
                    <a href="${escapeHtml(source.sourceUrl)}" target="_blank" rel="noopener" class="repo-name">
                        ${escapeHtml(source.sourceName)}
                    </a>
                    <span>•</span>
                    <span class="stars">${(source.stars || 0).toLocaleString()}</span>
                </div>
                ${source.description ? `<p class="repo-description">${escapeHtml(source.description)}</p>` : ''}
                ${(() => { const d = formatDate(source.pushedAt); return d ? `<span class="created-date">Last updated ${d}</span>` : ''; })()}
            </div>
        </div>
        <div class="admin-controls">
            <button type="button" class="source-delete" data-source-delete
                    aria-label="Hard-delete source" title="Hard-delete source">Hard-Delete</button>
        </div>
    `;
    installAvatarFallback(card, source.sourceName);
    target.appendChild(card);
    card.querySelector("[data-source-delete]").addEventListener("click", () => deleteSource(source));
}

function renderBundle(bundle, target) {
    const card = document.createElement("article");
    card.className = "bundle-item";

    const v3WarningHtml = bundle.bundleType === "ReVanced:V3"
        ? `<div class="v3-warning">
                ⚠️ It will not be usable in URV and the patches list can be empty.
           </div>`
        : '';
    const tagUrl = releaseTagUrl(bundle);
    const versionHtml = tagUrl
        ? `<a class="version-text" href="${escapeHtml(tagUrl)}" target="_blank" rel="noopener">${escapeHtml(bundle.version)}</a>`
        : `<span class="version-text">${escapeHtml(bundle.version)}</span>`;

    // Show the tracked namespace/repo from the source URL; after an upstream rename the
    // metadata names diverge from what is actually tracked, so both are shown.
    const trackedParts = sourceName(bundle.sourceUrl, bundle.ownerName, bundle.repoName).split('/');
    const trackedNamespace = trackedParts.length >= 2 ? trackedParts.slice(0, -1).join('/') : bundle.ownerName;
    const trackedRepo = trackedParts.length >= 2 ? trackedParts[trackedParts.length - 1] : bundle.repoName;
    const trackedName = `${trackedNamespace}/${trackedRepo}`;
    const currentName = bundle.ownerName && bundle.repoName ? `${bundle.ownerName}/${bundle.repoName}` : '';
    const repoLinkText = currentName && currentName !== trackedName
        ? `${escapeHtml(trackedName)} <span class="renamed-name">(${escapeHtml(currentName)})</span>`
        : escapeHtml(trackedName);

    card.innerHTML = `
        ${v3WarningHtml}

        <div class="bundle-header">
            <a href="${escapeHtml(bundle.sourceUrl)}" target="_blank" rel="noopener" class="owner-avatar-link" aria-label="Open ${escapeHtml(bundle.ownerName || 'source')} repository">
                <img src="${escapeHtml(bundle.ownerAvatarUrl)}"
                     alt="${escapeHtml(bundle.ownerName)}"
                     class="owner-avatar"
                     loading="lazy"
                     decoding="async">
            </a>
            <div class="bundle-header-content">
                <div class="repo-info">
                    <a href="${escapeHtml(bundle.sourceUrl)}" target="_blank" rel="noopener" class="repo-name">
                        ${repoLinkText}
                    </a>
                    <span>•</span>
                    <span class="stars">${bundle.repoStars.toLocaleString()}</span>
                </div>
                ${bundle.repoDescription ? `<div class="bundle-description">${cachedMarkdown(bundle.id, 'desc', bundle.repoDescription)}</div>` : ''}
                <div class="bundle-version">
                    ${versionHtml}
                    <span class="bundle-badge ${bundle.isPrerelease ? 'badge-prerelease' : 'badge-release'}">
                        ${bundle.isPrerelease ? 'Prerelease' : 'Release'}
                    </span>
                    <span class="bundle-badge badge-type">${escapeHtml(bundle.bundleType)}</span>
                    ${bundle.isRepoArchived ? `<span class="bundle-badge badge-archived">Archived</span>` : ''}
                    <span class="created-date">${formatDate(bundle.createdAt)}</span>
                </div>
            </div>
        </div>

        <div class="bundle-meta">
            <a href="${escapeHtml(bundle.downloadUrl)}" target="_blank" rel="noopener">
                Download bundle
            </a>
            ${bundle.signatureDownloadUrl ? `
                <span>•</span>
                <a href="${escapeHtml(bundle.signatureDownloadUrl)}" target="_blank" rel="noopener">
                    Download signature
                </a>
            ` : ''}
            <span>•</span>
            <button class="copy-btn" ${bundle.bundleType === "ReVanced:V3" ? 'disabled' : ''} data-url="/api/v3/bundle?source_url=${encodeURIComponent(bundle.sourceUrl)}&version=latest&channel=${bundle.isPrerelease ? 'prerelease' : 'stable'}">
                Copy remote bundle URL
            </button>
        </div>

        ${(bundle.description || bundle.patches.length > 0) ? `
            <div class="card-toggles">
                ${bundle.description ? `
                    <button class="card-toggle" type="button" data-toggle-changelog data-expanded="false">Show changelog</button>
                ` : ''}
                ${bundle.patches.length > 0 ? `
                    <button class="card-toggle" type="button" data-toggle-patches data-expanded="false">Show patches (${bundle.patches.length})</button>
                ` : ''}
            </div>
            <div class="card-content">
                ${bundle.description ? `<div class="changelog-content" data-changelog hidden></div>` : ''}
                ${bundle.patches.length > 0 ? `<div class="patches-content" data-patches-content hidden></div>` : ''}
            </div>
        ` : ''}
    `;

    installAvatarFallback(card, bundle.ownerName || trackedName);
    target.appendChild(card);

    const copyBtn = card.querySelector(".copy-btn");
    const copyButtonLabel = copyBtn.textContent.trim();
    let copyFeedbackTimeout;
    const showCopyFeedback = (text, copied = false) => {
        clearTimeout(copyFeedbackTimeout);
        copyBtn.textContent = text;
        copyBtn.classList.toggle("copied", copied);
        copyFeedbackTimeout = setTimeout(() => {
            copyBtn.textContent = copyButtonLabel;
            copyBtn.classList.remove("copied");
        }, 1500);
    };
    copyBtn.addEventListener("click", async () => {
        // The copied value must be absolute: it is pasted into the patched-app
        // manager, which resolves it from the device, not from this page.
        const url = new URL(copyBtn.dataset.url, location.origin).href;
        try {
            await navigator.clipboard.writeText(url);
            showCopyFeedback("Copied!", true);
        } catch (err) {
            console.error("Failed to copy:", err);
            showCopyFeedback("Failed!");
        }
    });

    const cardContent = card.querySelector(".card-content");
    const changelogBtn = card.querySelector("[data-toggle-changelog]");
    const changelogContainer = card.querySelector("[data-changelog]");
    const patchesBtn = card.querySelector("[data-toggle-patches]");
    const patchesContainer = card.querySelector("[data-patches-content]");
    const viewState = cardViewStates.get(bundle.id) || {
        section: undefined,
        changelogScrollTop: 0,
        patchesScrollTop: 0
    };

    const setActiveSection = (section, refreshGlobal = true, persist = true) => {
        const showChangelog = section === "changelog";
        const showPatches = section === "patches";

        if (changelogBtn && changelogContainer) {
            changelogContainer.hidden = !showChangelog;
            if (showChangelog && !changelogContainer.hasChildNodes()) {
                changelogContainer.innerHTML = cachedMarkdown(bundle.id, 'changelog', bundle.description);
            }
            if (showChangelog) restoreScrollTop(changelogContainer, viewState.changelogScrollTop);
            changelogBtn.dataset.expanded = String(showChangelog);
            changelogBtn.textContent = showChangelog ? "Hide changelog" : "Show changelog";
        }

        if (patchesBtn && patchesContainer) {
            patchesContainer.hidden = !showPatches;
            if (showPatches && !patchesContainer.hasChildNodes()) {
                patchesContainer.innerHTML = cachedPatchList(bundle.patches);
            }
            if (showPatches) restoreScrollTop(patchesContainer, viewState.patchesScrollTop);
            patchesBtn.dataset.expanded = String(showPatches);
            patchesBtn.textContent = showPatches ? "Hide patches" : `Show patches (${bundle.patches.length})`;
        }

        if (cardContent) cardContent.classList.toggle("active", !!section);
        if (persist) {
            viewState.section = section;
            cardViewStates.set(bundle.id, viewState);
        }
        // Row heights are re-measured automatically: virtualizer.measureElement
        // registers each row with a ResizeObserver, so expanding a changelog or
        // patch list corrects the virtual layout without a manual pass.
        if (refreshGlobal) updateAllChangelogsButton();
    };

    card.setActiveSection = setActiveSection;
    let initialSection = viewState.section;
    if (initialSection === undefined) {
        initialSection = allChangelogsExpanded && changelogBtn ? "changelog" : null;
    }
    if (initialSection === "changelog" && !changelogBtn) initialSection = null;
    if (initialSection === "patches" && !patchesBtn) initialSection = null;
    setActiveSection(initialSection, false, false);

    changelogContainer?.addEventListener("scroll", () => {
        viewState.changelogScrollTop = changelogContainer.scrollTop;
        cardViewStates.set(bundle.id, viewState);
    }, { passive: true });
    patchesContainer?.addEventListener("scroll", () => {
        viewState.patchesScrollTop = patchesContainer.scrollTop;
        cardViewStates.set(bundle.id, viewState);
    }, { passive: true });

    changelogBtn?.addEventListener("click", () => {
        setActiveSection(changelogBtn.dataset.expanded === "true" ? null : "changelog");
    });

    patchesBtn?.addEventListener("click", () => {
        setActiveSection(patchesBtn.dataset.expanded === "true" ? null : "patches");
    });
}

async function deleteSource(bundle) {
    if (!confirm(`Hard-delete ${bundle.sourceUrl} and all cached data? This cannot be undone.`)) return;
    try {
        const response = await fetch(`/api/v3/source?source_url=${encodeURIComponent(bundle.sourceUrl)}`, {
            method: "DELETE",
            headers: { "X-Hasura-Admin-Secret": adminSecret }
        });
        if (!response.ok) throw new Error(`Delete failed with status ${response.status}`);
        await loadBundles(adminSecret);
    } catch (error) {
        console.error(error);
        alert("Could not delete source.");
    }
}

function updateAllChangelogsButton() {
    toggleAllChangelogs.hidden = showingDisabledSources;
    const hasChangelogs = !showingDisabledSources && filteredBundles.some(bundle => bundle.description);
    toggleAllChangelogs.disabled = !hasChangelogs;
    toggleAllChangelogs.textContent = allChangelogsExpanded ? "Hide all changelogs" : "Show all changelogs";
}

function renderPatch(patch) {
    const packages = (patch.compatiblePackages || []).map(pkg => {
        const versionsText = pkg.versions?.filter(Boolean).join(', ') || 'all versions';
        return `<span class="package-tag">
            <span class="package-name">${escapeHtml(pkg.name)}</span>
            <span class="package-versions">${escapeHtml(versionsText)}</span>
        </span>`;
    }).join('');

    return `<div class="patch-item">
        <div class="patch-name">${escapeHtml(patch.name || 'Unnamed patch')}</div>
        ${patch.description ? `<div class="patch-description">${escapeHtml(patch.description)}</div>` : ''}
        ${packages ? `<div class="patch-packages">${packages}</div>` : ''}
    </div>`;
}

function sourceName(sourceUrl, ownerName, repoName) {
    try {
        const path = new URL(sourceUrl).pathname.replace(/^\/+|\/+$/g, '');
        return path || [ownerName, repoName].filter(Boolean).join('/');
    } catch {
        return [ownerName, repoName].filter(Boolean).join('/') || sourceUrl;
    }
}

function releaseTagUrl(bundle) {
    try {
        const source = new URL(bundle.sourceUrl);
        const repositoryUrl = `${source.origin}${source.pathname.replace(/\/+$/, '')}`;
        const tag = encodeURIComponent(bundle.version);

        if (source.hostname === 'gitlab.com') {
            return `${repositoryUrl}/-/releases/${tag}`;
        }
        if (['github.com', 'codeberg.org', 'gitea.com'].includes(source.hostname)) {
            return `${repositoryUrl}/releases/tag/${tag}`;
        }

        const download = new URL(bundle.downloadUrl);
        const downloadPrefix = `${source.pathname.replace(/\/+$/, '')}/releases/download/`;
        if (download.origin === source.origin && download.pathname.startsWith(downloadPrefix)) {
            const downloadedTag = download.pathname.slice(downloadPrefix.length).split('/')[0];
            if (downloadedTag) return `${repositoryUrl}/releases/tag/${downloadedTag}`;
        }
    } catch {
        // Keep the version as plain text when the API URLs cannot identify a release page.
    }

    return null;
}

function debounce(callback, delay) {
    let timeout;
    return () => {
        clearTimeout(timeout);
        timeout = setTimeout(callback, delay);
    };
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function renderMarkdown(text) {
    if (!text) return '';
    return DOMPurify.sanitize(marked.parse(text));
}

function formatDate(dateString) {
    try {
        if (!dateString) return "";
        const date = new Date(dateString);
        if (Number.isNaN(date.getTime())) return "";
        const now = new Date();
        const diffMs = now - date;
        const diffMins = Math.floor(diffMs / 60000);
        const diffHours = Math.floor(diffMs / 3600000);
        const diffDays = Math.floor(diffMs / 86400000);

        if (diffMins < 60) {
            return `${diffMins} minute${diffMins !== 1 ? 's' : ''} ago`;
        } else if (diffHours < 24) {
            return `${diffHours} hour${diffHours !== 1 ? 's' : ''} ago`;
        } else if (diffDays < 7) {
            return `${diffDays} day${diffDays !== 1 ? 's' : ''} ago`;
        } else {
            return date.toLocaleDateString('en-US', {
                year: 'numeric',
                month: 'short',
                day: 'numeric'
            });
        }
    } catch (e) {
        return dateString;
    }
}
