const input = document.getElementById("search");
const results = document.getElementById("results");
const status = document.getElementById("status");

let timeout = null;

input.addEventListener("input", () => {
    clearTimeout(timeout);
    timeout = setTimeout(search, 400);
});

async function search() {
    const query = input.value.trim();

    results.innerHTML = "";

    if (!query) {
        status.textContent = "Type a source URL to search";
        return;
    }

    status.textContent = "Searching";
    status.classList.add("loading");

    try {
        const res = await fetch(`/bundles/search?q=${encodeURIComponent(query)}`);

        if (res.status === 404) {
            status.textContent = "No bundles found";
            status.classList.remove("loading");
            return;
        }

        if (!res.ok) {
            status.textContent = "Server error";
            status.classList.remove("loading");
            return;
        }

        const bundles = await res.json();
        status.textContent = "";
        status.classList.remove("loading");

        if (!Array.isArray(bundles) || bundles.length === 0) {
            status.textContent = "No bundles found";
            return;
        }

        bundles.forEach(renderBundle);

    } catch (e) {
        console.error(e);
        status.textContent = "Network error";
        status.classList.remove("loading");
    }
}

function renderBundle(bundle) {
    const li = document.createElement("li");
    li.className = "bundle-item";

    const patchesPreview = bundle.patches.slice(0, 5);
    const hasMore = bundle.patches.length > 5;

    li.innerHTML = `
        <div class="bundle-header">
            <div class="bundle-title">${escapeHtml(bundle.version)}</div>
            <span class="bundle-badge ${bundle.isPrerelease ? 'badge-prerelease' : 'badge-release'}">
                ${bundle.isPrerelease ? 'Prerelease' : 'Release'}
            </span>
        </div>

        ${bundle.description ? `<div class="bundle-description">${escapeHtml(bundle.description)}</div>` : ''}

        <div class="bundle-meta">
            <a href="${escapeHtml(bundle.sourceUrl)}" target="_blank" rel="noopener">
                ${escapeHtml(bundle.sourceUrl)}
            </a>
            <span>•</span>
            <a href="${escapeHtml(bundle.downloadUrl)}" target="_blank" rel="noopener">
                Download .RVP
            </a>
            ${bundle.signatureDownloadUrl ? `
                <span>•</span>
                <a href="${escapeHtml(bundle.signatureDownloadUrl)}" target="_blank" rel="noopener">
                    Download signature
                </a>
            ` : ''}
            <span>•</span>
            <button class="copy-btn" data-url="https://revanced-external-bundles.onrender.com/bundles/id?id=${bundle.bundleId}">
                Copy URL
            </button>
        </div>

        ${bundle.patches.length > 0 ? `
            <div class="patches-section">
                <div class="patches-header">
                    <span class="patches-title">Patches</span>
                    <span class="patches-count">${bundle.patches.length} total</span>
                </div>
                <div class="patches-list ${hasMore ? 'collapsed' : ''}" data-patches-container>
                    ${patchesPreview.map(patch => `
                        <div class="patch-item">
                            <div class="patch-name">${escapeHtml(patch.name || 'Unnamed patch')}</div>
                            ${patch.description ? `<div class="patch-description">${escapeHtml(patch.description)}</div>` : ''}
                        </div>
                    `).join('')}
                </div>
                ${hasMore ? `
                    <button class="toggle-patches" data-toggle-patches>
                        Show all ${bundle.patches.length} patches
                    </button>
                ` : ''}
            </div>
        ` : ''}
    `;

    results.appendChild(li);

    // Setup copy button
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

    // Setup toggle patches button
    const toggleBtn = li.querySelector("[data-toggle-patches]");
    const patchesContainer = li.querySelector("[data-patches-container]");

    if (toggleBtn && patchesContainer) {
        let expanded = false;

        toggleBtn.addEventListener("click", () => {
            if (!expanded) {
                // Expand: show all patches
                patchesContainer.innerHTML = bundle.patches.map(patch => `
                    <div class="patch-item">
                        <div class="patch-name">${escapeHtml(patch.name || 'Unnamed patch')}</div>
                        ${patch.description ? `<div class="patch-description">${escapeHtml(patch.description)}</div>` : ''}
                    </div>
                `).join('');
                patchesContainer.classList.remove("collapsed");
                toggleBtn.textContent = "Show less";
                expanded = true;
            } else {
                // Collapse: show only first 5
                patchesContainer.innerHTML = patchesPreview.map(patch => `
                    <div class="patch-item">
                        <div class="patch-name">${escapeHtml(patch.name || 'Unnamed patch')}</div>
                        ${patch.description ? `<div class="patch-description">${escapeHtml(patch.description)}</div>` : ''}
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
