(() => {
  document.documentElement.classList.remove("no-js");
  const REPO = "unicxrn/XerahS-Android";
  const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  // Reveal sections as they scroll in. Siblings get a small stagger.
  const revealables = document.querySelectorAll("[data-reveal]");
  if ("IntersectionObserver" in window && !reduceMotion) {
    const io = new IntersectionObserver((entries) => {
      for (const entry of entries) {
        if (!entry.isIntersecting) continue;
        entry.target.classList.add("is-in");
        io.unobserve(entry.target);
      }
    }, { rootMargin: "0px 0px -10% 0px", threshold: 0.12 });

    revealables.forEach((el) => {
      const siblings = [...el.parentElement.children].filter((c) => c.hasAttribute("data-reveal"));
      el.style.setProperty("--d", `${Math.min(siblings.indexOf(el), 5) * 80}ms`);
      io.observe(el);
    });
  } else {
    revealables.forEach((el) => el.classList.add("is-in"));
  }

  // Nav background once the page moves.
  const nav = document.querySelector("[data-nav]");
  const onScroll = () => nav.classList.toggle("is-scrolled", window.scrollY > 24);
  onScroll();
  window.addEventListener("scroll", onScroll, { passive: true });

  // Mobile menu.
  const toggle = document.querySelector("[data-menu-toggle]");
  const menu = document.querySelector("[data-menu]");
  const setMenu = (open) => {
    toggle.setAttribute("aria-expanded", String(open));
    toggle.setAttribute("aria-label", open ? "Close menu" : "Open menu");
    menu.hidden = !open;
    document.body.style.overflow = open ? "hidden" : "";
  };
  toggle.addEventListener("click", () => setMenu(toggle.getAttribute("aria-expanded") !== "true"));
  menu.addEventListener("click", (e) => { if (e.target.closest("a")) setMenu(false); });
  document.addEventListener("keydown", (e) => { if (e.key === "Escape" && !menu.hidden) setMenu(false); });

  // The marquee scrolls by half its width, so it needs a second copy of the list.
  const track = document.querySelector(".marquee__track");
  if (track) {
    [...track.children].forEach((item) => {
      const copy = item.cloneNode(true);
      copy.setAttribute("aria-hidden", "true");
      track.appendChild(copy);
    });
  }

  // Tilt the hero phone toward the pointer.
  const stage = document.querySelector("[data-tilt]");
  const heroPhone = stage && stage.querySelector(".phone--hero");
  if (stage && heroPhone && !reduceMotion && window.matchMedia("(pointer: fine)").matches) {
    heroPhone.addEventListener("animationend", () => { heroPhone.style.animation = "none"; }, { once: true });
    const hero = document.querySelector(".hero");
    hero.addEventListener("pointermove", (e) => {
      const r = stage.getBoundingClientRect();
      const x = (e.clientX - (r.left + r.width / 2)) / r.width;
      const y = (e.clientY - (r.top + r.height / 2)) / r.height;
      heroPhone.style.setProperty("--ry", `${(-8 + x * 14).toFixed(2)}deg`);
      heroPhone.style.setProperty("--rx", `${(-y * 10).toFixed(2)}deg`);
    });
    hero.addEventListener("pointerleave", () => {
      heroPhone.style.setProperty("--ry", "-8deg");
      heroPhone.style.setProperty("--rx", "0deg");
    });
  }

  // How it works: the phone follows whichever step is in the middle of the screen.
  const steps = [...document.querySelectorAll("[data-step]")];
  const shots = [...document.querySelectorAll("[data-step-shot]")];
  const rail = document.querySelector("[data-rail]");
  const setStep = (i) => {
    steps.forEach((s, n) => s.classList.toggle("is-active", n === i));
    shots.forEach((s, n) => s.classList.toggle("is-active", n === i));
    if (rail) rail.style.transform = `translateY(${i * 100}%)`;
  };
  if (steps.length && "IntersectionObserver" in window) {
    const stepIO = new IntersectionObserver((entries) => {
      for (const entry of entries) {
        if (entry.isIntersecting) setStep(Number(entry.target.dataset.step));
      }
    }, { rootMargin: "-45% 0px -45% 0px" });
    steps.forEach((s) => stepIO.observe(s));
  }

  // Only play the recordings while they're on screen.
  const videos = document.querySelectorAll("video");
  if ("IntersectionObserver" in window) {
    const videoIO = new IntersectionObserver((entries) => {
      for (const entry of entries) {
        const v = entry.target;
        if (entry.isIntersecting && !reduceMotion) {
          v.play().catch(() => {});
        } else {
          v.pause();
        }
      }
    }, { threshold: 0.2 });
    videos.forEach((v) => {
      if (reduceMotion) v.removeAttribute("autoplay");
      videoIO.observe(v);
    });
  }

  // Gallery: buttons and drag to scroll.
  const gallery = document.querySelector("[data-gallery]");
  if (gallery) {
    const step = () => Math.max(260, gallery.clientWidth * 0.6);
    document.querySelector("[data-gallery-prev]").addEventListener("click", () => gallery.scrollBy({ left: -step(), behavior: "smooth" }));
    document.querySelector("[data-gallery-next]").addEventListener("click", () => gallery.scrollBy({ left: step(), behavior: "smooth" }));

    let startX = 0;
    let startScroll = 0;
    let dragging = false;
    gallery.addEventListener("pointerdown", (e) => {
      if (e.pointerType !== "mouse") return;
      dragging = true;
      startX = e.clientX;
      startScroll = gallery.scrollLeft;
      gallery.classList.add("is-dragging");
      gallery.setPointerCapture(e.pointerId);
    });
    gallery.addEventListener("pointermove", (e) => {
      if (dragging) gallery.scrollLeft = startScroll - (e.clientX - startX);
    });
    const stop = () => { dragging = false; gallery.classList.remove("is-dragging"); };
    gallery.addEventListener("pointerup", stop);
    gallery.addEventListener("pointercancel", stop);
  }

  // Releases: fill in the download links and the changelog from GitHub.
  const releasesBox = document.querySelector("[data-releases]");

  const escapeHtml = (s) => s.replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

  const inline = (s) => escapeHtml(s)
    .replace(/`([^`]+)`/g, "<code>$1</code>")
    .replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>")
    .replace(/\[([^\]]+)\]\((https?:\/\/[^)\s]+)\)/g, '<a href="$2" rel="noopener">$1</a>');

  // Release notes are short markdown: headings, bullet lists and paragraphs.
  const renderNotes = (md) => {
    const out = [];
    let list = false;
    const closeList = () => { if (list) { out.push("</ul>"); list = false; } };
    for (const raw of md.replace(/\r/g, "").split("\n")) {
      const line = raw.trim();
      if (!line) { closeList(); continue; }
      const heading = line.match(/^#{1,6}\s+(.*)$/);
      const bullet = line.match(/^[-*]\s+(.*)$/);
      if (heading) {
        closeList();
        out.push(`<h4>${inline(heading[1])}</h4>`);
      } else if (bullet) {
        if (!list) { out.push("<ul>"); list = true; }
        out.push(`<li>${inline(bullet[1])}</li>`);
      } else {
        closeList();
        out.push(`<p>${inline(line)}</p>`);
      }
    }
    closeList();
    return out.join("");
  };

  const formatDate = (iso) => new Date(iso).toLocaleDateString("en", { year: "numeric", month: "short", day: "numeric" });
  const formatSize = (bytes) => `${(bytes / 1048576).toFixed(0)} MB`;

  const showReleases = (releases) => {
    const published = releases.filter((r) => !r.draft);
    if (!published.length) throw new Error("no releases");

    const latest = published[0];
    document.querySelectorAll("[data-latest-version]").forEach((el) => { el.textContent = latest.tag_name; });
    const apk = (latest.assets || []).find((a) => a.name.toLowerCase().endsWith(".apk"));
    if (apk) {
      document.querySelectorAll("[data-apk-link]").forEach((a) => { a.href = apk.browser_download_url; });
      const size = document.querySelector("[data-apk-size]");
      if (size) size.textContent = `${formatSize(apk.size)} download`;
    }

    releasesBox.innerHTML = "";
    published.slice(0, 6).forEach((r, i) => {
      const card = document.createElement("div");
      card.className = "release";
      card.innerHTML = `
        <details class="release__inner"${i === 0 ? " open" : ""}>
          <summary>
            <span class="release__tag">${escapeHtml(r.tag_name)}</span>
            ${i === 0 ? '<span class="release__latest">Latest</span>' : ""}
            <span class="release__date">${formatDate(r.published_at || r.created_at)}</span>
            <svg class="release__chev" viewBox="0 0 24 24" aria-hidden="true"><path d="m6 9 6 6 6-6"/></svg>
          </summary>
          <div class="release__body">${r.body ? renderNotes(r.body) : "<p>No notes for this release.</p>"}</div>
        </details>`;
      releasesBox.appendChild(card);
    });
  };

  const showFallback = () => {
    releasesBox.innerHTML = `<p class="releases__empty">Couldn't load the changelog right now. It's always on <a href="https://github.com/${REPO}/releases">GitHub Releases</a>.</p>`;
  };

  const cacheKey = "xerahs-releases";
  let cached = null;
  try { cached = JSON.parse(sessionStorage.getItem(cacheKey) || "null"); } catch (e) { cached = null; }

  if (cached) {
    try { showReleases(cached); } catch (e) { showFallback(); }
  } else {
    fetch(`https://api.github.com/repos/${REPO}/releases?per_page=6`, { headers: { Accept: "application/vnd.github+json" } })
      .then((res) => { if (!res.ok) throw new Error(res.status); return res.json(); })
      .then((data) => {
        showReleases(data);
        try { sessionStorage.setItem(cacheKey, JSON.stringify(data)); } catch (e) { /* private mode */ }
      })
      .catch(showFallback);
  }
})();
