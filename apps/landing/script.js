'use strict';

const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
const compactLayout = window.matchMedia('(max-width:767px), (max-width:959px) and (max-height:560px) and (orientation:landscape)');
const clamp = function (value, min, max) { return Math.min(max, Math.max(min, value)); };
const controls = function (selector) { return Array.from(document.querySelectorAll(selector)); };
const enable = function (items) { items.forEach(function (item) { item.disabled = false; }); };
const softSwap = function (element) {
  if (reducedMotion.matches || !element.animate) return;
  element.getAnimations().forEach(function (animation) { animation.cancel(); });
  element.animate(
    [{ opacity: 0.5, transform: 'translateY(7px)' }, { opacity: 1, transform: 'translateY(0)' }],
    { duration: 350, easing: 'cubic-bezier(.22,1,.36,1)' }
  );
};

// The phone explains three stages. It is an illustration, not a remote app.
const heroVisual = document.querySelector('[data-phone-stage]');
const heroPhone = document.querySelector('.hero-phone');
const phoneDeck = document.querySelector('[data-phone-deck]');
const phoneControls = controls('[data-phone-control]');
const phoneNav = controls('.phone-nav > span');
const phoneTags = controls('.source-tag');
const phoneAnnotations = [
  [
    { label: 'Your starting point', title: 'The assignment brief', icon: '#i-doc' },
    { label: 'Keep it yours', title: 'A local project', icon: '#i-folder' }
  ],
  [
    { label: 'Your chosen material', title: 'Sources you can revisit', icon: '#i-doc' },
    { label: 'Follow the connection', title: 'Source → note → claim', icon: '#i-link' }
  ],
  [
    { label: 'A useful next step', title: 'Review and revise', icon: '#i-reset' },
    { label: 'Your academic judgment', title: 'Keep the limits visible', icon: '#i-check' }
  ]
];
let phoneIndex = -1;
function setPhone(index) {
  if (index === phoneIndex) return;
  const firstScreen = phoneIndex === -1;
  phoneIndex = index;
  heroVisual.dataset.screen = String(index);
  phoneTags.forEach(function (tag, tagIndex) {
    const annotation = phoneAnnotations[index][tagIndex];
    tag.querySelector('small').textContent = annotation.label;
    tag.querySelector('strong').textContent = annotation.title;
    tag.querySelector('use').setAttribute('href', annotation.icon);
    if (!firstScreen) softSwap(tag.querySelector('div'));
  });
  phoneDeck.style.setProperty('--phone-index', String(index));
  phoneControls.forEach(function (button, buttonIndex) {
    button.setAttribute('aria-pressed', String(buttonIndex === index));
    button.dataset.past = String(buttonIndex < index);
  });
  phoneNav.forEach(function (item, itemIndex) {
    item.classList.toggle('is-active', itemIndex === index);
  });
}
enable(phoneControls);
phoneControls.forEach(function (button) {
  button.addEventListener('click', function () { setPhone(Number(button.dataset.phoneControl)); });
});
setPhone(0);

// A sticky project preview follows the narrative on wide screens.
// Compact screens present the same stages as touch controls beside one caption.
const journeyStage = document.querySelector('.journey-stage');
const storySteps = controls('[data-story-step]');
const journeyScenes = controls('[data-journey-scene]');
const journeyControls = controls('[data-journey-control]');
const journeyLabel = document.querySelector('[data-journey-label]');
const journeyMarkers = controls('.journey-stage-footer i');
const journeyLabels = ['Create a local project', 'Connect the material behind a claim', 'Review and keep the next step visible'];
let journeyIndex = -1;
function setJourney(index, animate) {
  if (index === journeyIndex) return;
  journeyIndex = index;
  journeyStage.dataset.step = String(index);
  journeyScenes.forEach(function (scene, sceneIndex) { scene.hidden = sceneIndex !== index; });
  journeyControls.forEach(function (button, buttonIndex) {
    button.setAttribute('aria-pressed', String(buttonIndex === index));
  });
  storySteps.forEach(function (step, stepIndex) { step.classList.toggle('is-active', stepIndex === index); });
  journeyMarkers.forEach(function (marker, markerIndex) { marker.classList.toggle('is-active', markerIndex === index); });
  journeyLabel.textContent = journeyLabels[index];
  if (animate) softSwap(journeyScenes[index]);
}
enable(journeyControls);
journeyControls.forEach(function (button) {
  button.addEventListener('click', function () {
    setJourney(Number(button.dataset.journeyControl), true);
    queueResize();
  });
});
setJourney(0, false);

// These controls explain the proposed workflow; they never call an AI service.
const aiControls = controls('[data-ai-control]');
const aiPanels = controls('[data-ai-panel]');
let aiIndex = 0;
enable(aiControls);
aiControls.forEach(function (button) {
  button.addEventListener('click', function () {
    const index = Number(button.dataset.aiControl);
    if (index === aiIndex) return;
    aiIndex = index;
    aiControls.forEach(function (control, controlIndex) {
      control.setAttribute('aria-pressed', String(controlIndex === index));
    });
    aiPanels.forEach(function (panel, panelIndex) { panel.hidden = panelIndex !== index; });
    softSwap(aiPanels[index]);
    queueResize();
  });
});

// The supplied practice data stay bounded to one fictional trial per condition.
const factInputs = controls('[data-fact]');
const resetFacts = document.getElementById('reset-facts');
const availability = document.getElementById('availability');
const boundedDescription = document.getElementById('bounded-description');
const scopeNote = document.getElementById('scope-note');
const labResult = document.querySelector('.lab-result');
const demoOutput = document.querySelector('.demo-output');
const factTrace = controls('[data-trace-fact]');
const suppliedFacts = [
  { id: 'warm', name: 'warm water', location: 'in warm water', time: 32 },
  { id: 'room', name: 'room temperature', location: 'at room temperature', time: 58 },
  { id: 'cold', name: 'cold water', location: 'in cold water', time: 92 }
];
function updateDemo(animate) {
  const active = suppliedFacts.filter(function (fact) {
    return factInputs.some(function (input) { return input.dataset.fact === fact.id && input.checked; });
  });
  const activeIds = new Set(active.map(function (fact) { return fact.id; }));
  const missing = suppliedFacts.filter(function (fact) { return !activeIds.has(fact.id); });
  availability.textContent = active.length + ' of 3 available';
  labResult.dataset.availability = String(active.length);
  resetFacts.disabled = active.length === suppliedFacts.length;
  factTrace.forEach(function (node) { node.classList.toggle('is-missing', !activeIds.has(node.dataset.traceFact)); });

  if (active.length === 3) {
    boundedDescription.textContent = 'In this supplied case, dissolution took 32 seconds in warm water, 58 seconds at room temperature, and 92 seconds in cold water.';
    scopeNote.textContent = 'All three supplied conditions are available for comparison. This description stays within one trial per condition.';
  } else if (active.length === 2) {
    boundedDescription.textContent = 'In this supplied case, dissolution took ' + active[0].time + ' seconds ' + active[0].location + ' and ' + active[1].time + ' seconds ' + active[1].location + '.';
    scopeNote.textContent = 'The ' + missing[0].name + ' observation is unavailable, so the three-condition comparison is incomplete. Each remaining condition has one trial.';
  } else if (active.length === 1) {
    boundedDescription.textContent = 'Only the ' + active[0].name + ' observation remains: ' + active[0].time + ' seconds in this supplied trial.';
    scopeNote.textContent = 'One observation cannot support a comparison across temperatures. Restore another supplied fact to compare conditions.';
  } else {
    boundedDescription.textContent = 'No supplied observations are available to describe.';
    scopeNote.textContent = 'Restore at least one observation to inspect this case. A comparison needs more than one condition.';
  }
  if (animate) softSwap(demoOutput);
}
enable(factInputs);
factInputs.forEach(function (input) { input.addEventListener('change', function () { updateDemo(true); }); });
resetFacts.addEventListener('click', function () {
  factInputs.forEach(function (input) { input.checked = true; });
  updateDemo(true);
});
updateDemo(false);

// Native horizontal scrolling remains available with touch and keyboard.
const gallery = document.querySelector('.screen-gallery');
const galleryButtons = controls('[data-gallery-direction]');
function updateGallery() {
  const remaining = Math.max(0, gallery.scrollWidth - gallery.clientWidth);
  galleryButtons.forEach(function (button) {
    button.disabled = Number(button.dataset.galleryDirection) < 0
      ? gallery.scrollLeft <= 2
      : gallery.scrollLeft >= remaining - 2;
  });
}
galleryButtons.forEach(function (button) {
  button.addEventListener('click', function () {
    const screen = gallery.querySelector('.screen-panel');
    const distance = screen.getBoundingClientRect().width + (parseFloat(window.getComputedStyle(gallery).columnGap) || 0);
    gallery.scrollBy({
      left: distance * Number(button.dataset.galleryDirection),
      behavior: reducedMotion.matches ? 'auto' : 'smooth'
    });
  });
});
gallery.addEventListener('scroll', updateGallery, { passive: true });

// No animation loop runs while idle. Geometry is cached on resize, not read
// repeatedly during scroll. One frame coalesces each burst of scroll events.
const progressBar = document.querySelector('.page-progress');
let geometry;
let frame = 0;
let resizePending = false;
let motionObserver;
function measure() {
  const position = window.scrollY;
  const phoneTop = heroVisual.getBoundingClientRect().top + position;
  const start = Math.max(0, phoneTop - window.innerHeight * 0.6);
  geometry = {
    total: Math.max(1, document.documentElement.scrollHeight - window.innerHeight),
    heroEnd: phoneTop + heroVisual.offsetHeight,
    phoneStart: start,
    phoneRange: Math.max(360, phoneTop + heroPhone.offsetHeight - window.innerHeight * 0.25 - start),
    steps: storySteps.map(function (step) { return step.querySelector('h3').getBoundingClientRect().top + position; }),
    journeyEnd: document.querySelector('.journey-layout').getBoundingClientRect().bottom + position
  };
}
function renderScroll(fromScroll) {
  if (!geometry) measure();
  const position = window.scrollY;
  progressBar.style.setProperty('--page-progress', clamp(position / geometry.total, 0, 1).toFixed(4));

  if (position < geometry.heroEnd && !reducedMotion.matches) {
    const progress = clamp((position - geometry.phoneStart) / geometry.phoneRange, 0, 1);
    heroVisual.style.setProperty('--hero-travel', progress.toFixed(3));
    heroVisual.style.setProperty('--folio-shift', (progress * -14).toFixed(1) + 'px');
    const wide = !compactLayout.matches;
    heroPhone.style.setProperty('--phone-y', wide ? (-8 + progress * 9).toFixed(2) + 'deg' : '0deg');
    heroPhone.style.setProperty('--phone-z', wide ? (2 - progress * 3).toFixed(2) + 'deg' : '0deg');
    if (fromScroll) setPhone(Math.min(2, Math.floor(progress * 3)));
  }

  if (fromScroll && !compactLayout.matches && position < geometry.journeyEnd) {
    const readingLine = position + window.innerHeight * 0.5;
    let index = 0;
    geometry.steps.forEach(function (top, stepIndex) { if (top <= readingLine) index = stepIndex; });
    setJourney(index, true);
  }
}
function queueScroll() {
  if (frame) return;
  frame = window.requestAnimationFrame(function () {
    frame = 0;
    renderScroll(true);
  });
}
function queueResize() {
  if (resizePending) return;
  resizePending = true;
  window.requestAnimationFrame(function () {
    resizePending = false;
    measure();
    renderScroll(false);
    updateGallery();
  });
}
window.addEventListener('scroll', queueScroll, { passive: true });
window.addEventListener('resize', queueResize, { passive: true });
document.addEventListener('visibilitychange', function () {
  if (document.hidden && frame) {
    window.cancelAnimationFrame(frame);
    frame = 0;
  }
});
function observeArtwork() {
  const artwork = controls('[data-in-view]');
  if (motionObserver) motionObserver.disconnect();
  if (reducedMotion.matches || !('IntersectionObserver' in window)) {
    artwork.forEach(function (item) { item.classList.add('is-in-view'); });
    return;
  }
  motionObserver = new IntersectionObserver(function (entries, observer) {
    entries.forEach(function (entry) {
      if (!entry.isIntersecting) return;
      entry.target.classList.add('is-in-view');
      observer.unobserve(entry.target);
    });
  }, { threshold: 0.25 });
  artwork.forEach(function (item) {
    if (!item.classList.contains('is-in-view')) motionObserver.observe(item);
  });
}
reducedMotion.addEventListener('change', function () {
  if (reducedMotion.matches) {
    document.getAnimations().forEach(function (animation) { animation.cancel(); });
  }
  observeArtwork();
  queueResize();
});
document.querySelectorAll('.faq-list details').forEach(function (item) {
  item.addEventListener('toggle', queueResize);
});
document.documentElement.classList.add('has-interactions');
measure();
renderScroll(false);
updateGallery();
observeArtwork();
if (document.fonts && document.fonts.ready) document.fonts.ready.then(queueResize);
window.addEventListener('load', queueResize, { once: true });
