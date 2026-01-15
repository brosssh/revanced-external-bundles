const input = document.getElementById("search");
const packageFilter = document.getElementById("package-filter");
const results = document.getElementById("results");
const status = document.getElementById("status");
const filterButtons = document.querySelectorAll(".filter-btn");

let currentFilter = "release";
let currentSearchQuery = "";
let currentPackageFilter = "";
let allBundles = [];

// Load bundles on page load
loadBundles();

input.addEventListener("input", () => {
    currentSearchQuery = input.value.trim().toLowerCase();
    renderFilteredBundles();
});

packageFilter.addEventListener("input", () => {
    currentPackageFilter = packageFilter.value.trim().toLowerCase();
    renderFilteredBundles();
});

filterButtons.forEach(btn => {
    btn.addEventListener("click", () => {
        filterButtons.forEach(b => b.classList.remove("active"));
        btn.classList.add("active");
        currentFilter = btn.dataset.filter;
        renderFilteredBundles();
    });
});

async function loadBundles() {
    status.textContent = "Loading bundles...";
    status.classList.add("loading");

    try {
        const query = `
            query Snapshot {
                bundle(
                  where: { is_latest: { _eq: true } }
                  order_by: { source: { source_metadatum: { repo_stars: desc } } }
                ) {
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
                }
            }
        `;

        const res = await fetch('/hasura/v1/graphql', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            query,
          }),
        });

        if (!res.ok) {
            status.textContent = "Failed to load bundles";
            status.classList.remove("loading");
            return;
        }

        const { data, errors } = await res.json();

        if (errors) {
            console.error('GraphQL errors:', errors);
            status.textContent = "Failed to load bundles";
            status.classList.remove("loading");
            return;
        }

        const bundles = data?.bundle || [];

        if (!Array.isArray(bundles) || bundles.length === 0) {
            status.textContent = "No bundles available";
            status.classList.remove("loading");
            allBundles = [];
            return;
        }

        // Transform GraphQL data to match the old structure
        allBundles = bundles.map(transformBundle);
        status.classList.remove("loading");
        renderFilteredBundles();

    } catch (e) {
        console.error(e);
        status.textContent = "Network error";
        status.classList.remove("loading");
    }
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
    results.innerHTML = "";

    if (allBundles.length === 0) {
        status.textContent = "No bundles available";
        return;
    }

    const filteredBundles = allBundles
        .map(bundle => {
            if (currentFilter === "release" && bundle.isPrerelease) return null;
            if (currentFilter === "prerelease" && !bundle.isPrerelease) return null;

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
        const filterParts = [];
        if (currentSearchQuery) filterParts.push(`matching "${currentSearchQuery}"`);
        if (currentPackageFilter) filterParts.push(`for package "${currentPackageFilter}"`);
        if (currentFilter !== "all") filterParts.push(currentFilter);

        status.textContent = `No bundles found ${filterParts.join(' ')}`;
        return;
    }

    status.textContent = "";
    filteredBundles.forEach(renderBundle);
}

function renderBundle(bundle) {
    const li = document.createElement("li");
    li.className = "bundle-item";

    const patchesPreview = bundle.patches.slice(0, 5);
    const hasMore = bundle.patches.length > 5;
    const v3WarningHtml = bundle.bundleType == "ReVanced:V3"
        ? `<div class="v3-warning">
                ⚠️ This bundle is V3. It will not be usable in URV and the patches list will always be empty.
           </div>`
        : '';

    li.innerHTML = `
        ${v3WarningHtml}

        <div class="bundle-header">
            <img src="${escapeHtml(bundle.ownerAvatarUrl)}"
                 alt="${escapeHtml(bundle.ownerName)}"
                 class="owner-avatar"
                 onerror="this.src='data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 100 100%22%3E%3Crect fill=%22%23eee%22 width=%22100%22 height=%22100%22/%3E%3Ctext x=%2250%22 y=%2250%22 font-size=%2240%22 text-anchor=%22middle%22 dy=%22.3em%22 fill=%22%23999%22%3E${escapeHtml(bundle.ownerName[0] || '?').toUpperCase()}%3C/text%3E%3C/svg%3E'">
            <div class="bundle-header-content">
                <div class="repo-info">
                    <a href="${escapeHtml(bundle.sourceUrl)}" target="_blank" rel="noopener" class="repo-name">
                        ${escapeHtml(bundle.ownerName)}/${escapeHtml(bundle.repoName)}
                    </a>
                    <span>•</span>
                    <span class="stars">${bundle.repoStars.toLocaleString()}</span>
                </div>
                ${bundle.repoDescription ? `<div class="bundle-description">${renderMarkdown(bundle.repoDescription)}</div>` : ''}
                <div class="bundle-version">
                    <span class="version-text">${escapeHtml(bundle.version)}</span>
                    <span class="bundle-badge ${bundle.isPrerelease ? 'badge-prerelease' : 'badge-release'}">
                        ${bundle.isPrerelease ? 'Prerelease' : 'Release'}
                    </span>
                    <span class="created-date">${formatDate(bundle.createdAt)}</span>
                </div>
            </div>
        </div>

        ${bundle.description ? `<div class="changelog-content">${renderMarkdown(bundle.description)}</div>` : ''}

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
            <button class="copy-btn" ${bundle.bundleType == "REVANCED_V3" ? 'disabled' : ''} data-url="https://revanced-external-bundles.brosssh.com/api/v1/bundle/${bundle.ownerName}/${bundle.repoName}/latest?prerelease=${bundle.isPrerelease}">
                Copy remote bundle URL
            </button>
        </div>

        ${bundle.patches.length > 0 ? `
                    <div class="patches-section">
                        <div class="patches-header">
                            <span class="patches-title">Patches</span>
                            <span class="patches-count">${bundle.patches.length} total</span>
                        </div>
                        <div class="patches-list" data-patches-container>
                            ${bundle.patches.map(patch => `
                                <div class="patch-item">
                                    <div class="patch-name">${escapeHtml(patch.name || 'Unnamed patch')}</div>
                                    ${patch.description ? `<div class="patch-description">${escapeHtml(patch.description)}</div>` : ''}
                                    ${patch.compatiblePackages && patch.compatiblePackages.length > 0 ? `
                                        <div class="patch-packages">
                                            ${patch.compatiblePackages.map(pkg => {
                                                const versionsText = pkg.versions && pkg.versions.length > 0
                                                    ? pkg.versions.filter(v => v).join(', ') || 'all versions'
                                                    : 'all versions';
                                                return `<span class="package-tag">
                                                    <span class="package-name">${escapeHtml(pkg.name)}</span>
                                                    <span class="package-versions">${escapeHtml(versionsText)}</span>
                                                </span>`;
                                            }).join('')}
                                        </div>
                                    ` : ''}
                                </div>
                            `).join('')}
                </div>
            </div>
        ` : ''}
    `;

    results.appendChild(li);

    const copyBtn = li.querySelector(".copy-btn");
    copyBtn.addEventListener("click", async () => {
        const url = copyBtn.dataset.url;
        try {
            await navigator.clipboard.writeText(url);
            const originalText = copyBtn.textContent;
            copyBtn.textContent = "Copied!";
            copyBtn.classList.add("copied");
            setTimeout(() => {
                copyBtn.textContent = originalText;
                copyBtn.classList.remove("copied");
            }, 1500);
        } catch (err) {
            console.error("Failed to copy:", err);
            copyBtn.textContent = "Failed!";
        }
    });

    const toggleBtn = li.querySelector("[data-toggle-patches]");
    const patchesContainer = li.querySelector("[data-patches-container]");

    if (toggleBtn && patchesContainer) {
        let expanded = false;

        toggleBtn.addEventListener("click", () => {
            if (!expanded) {
                patchesContainer.innerHTML = bundle.patches.map(patch => `
                    <div class="patch-item">
                        <div class="patch-name">${escapeHtml(patch.name || 'Unnamed patch')}</div>
                        ${patch.description ? `<div class="patch-description">${escapeHtml(patch.description)}</div>` : ''}
                        ${patch.compatiblePackages && patch.compatiblePackages.length > 0 ? `
                            <div class="patch-packages">
                                ${patch.compatiblePackages.map(pkg => {
                                    const versionsText = pkg.versions && pkg.versions.length > 0
                                        ? pkg.versions.filter(v => v).join(', ') || 'all versions'
                                        : 'all versions';
                                    return `<span class="package-tag">
                                        <span class="package-name">${escapeHtml(pkg.name)}</span>
                                        <span class="package-versions">${escapeHtml(versionsText)}</span>
                                    </span>`;
                                }).join('')}
                            </div>
                        ` : ''}
                    </div>
                `).join('');
                patchesContainer.classList.remove("collapsed");
                toggleBtn.textContent = "Show less";
                expanded = true;
            } else {
                patchesContainer.innerHTML = patchesPreview.map(patch => `
                    <div class="patch-item">
                        <div class="patch-name">${escapeHtml(patch.name || 'Unnamed patch')}</div>
                        ${patch.description ? `<div class="patch-description">${escapeHtml(patch.description)}</div>` : ''}
                        ${patch.compatiblePackages && patch.compatiblePackages.length > 0 ? `
                            <div class="patch-packages">
                                ${patch.compatiblePackages.map(pkg => {
                                    const versionsText = pkg.versions && pkg.versions.length > 0
                                        ? pkg.versions.filter(v => v).join(', ') || 'all versions'
                                        : 'all versions';
                                    return `<span class="package-tag">
                                        <span class="package-name">${escapeHtml(pkg.name)}</span>
                                        <span class="package-versions">${escapeHtml(versionsText)}</span>
                                    </span>`;
                                }).join('')}
                            </div>
                        ` : ''}
                    </div>
                `).join('');
                patchesContainer.classList.add("collapsed");
                toggleBtn.textContent = `Show all ${bundle.patches.length} patches`;
                expanded = false;
            }
        });
    }
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function renderMarkdown(text) {
    if (!text) return '';
    marked.setOptions({
        breaks: true,
        gfm: true
    });
    return marked.parse(text);
}

function formatDate(dateString) {
    try {
        const date = new Date(dateString);
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
