const factInputs = Array.from(document.querySelectorAll('[data-fact]'));
const availability = document.getElementById('availability');
const boundedDescription = document.getElementById('bounded-description');
const scopeNote = document.getElementById('scope-note');
const resetFacts = document.getElementById('reset-facts');
const demoAnswer = document.querySelector('.demo-answer');
const demoOutput = document.querySelector('.demo-output');
const traceNodes = Array.from(document.querySelectorAll('[data-trace-fact]'));
const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
let feedbackAnimation;

const facts = {
  warm: { name: 'warm water', time: 32 },
  room: { name: 'room-temperature water', time: 58 },
  cold: { name: 'cold water', time: 92 },
};

function updateEvidenceExample(animateFeedback = false) {
  const selected = factInputs.filter((input) => input.checked).map((input) => facts[input.dataset.fact]);
  const selectedIds = new Set(factInputs.filter((input) => input.checked).map((input) => input.dataset.fact));
  availability.textContent = `${selected.length} of 3 available`;
  demoAnswer.dataset.availability = selected.length;
  resetFacts.disabled = selected.length === factInputs.length;
  traceNodes.forEach((node) => node.classList.toggle('is-missing', !selectedIds.has(node.dataset.traceFact)));

  if (selected.length === 3) {
    boundedDescription.textContent = 'In this supplied case, dissolution took 32 seconds in warm water, 58 seconds at room temperature, and 92 seconds in cold water.';
    scopeNote.textContent = 'All three supplied conditions are available for comparison. The description stays inside this case.';
  } else if (selected.length === 2) {
    boundedDescription.textContent = `The available observations show ${selected[0].time} seconds in ${selected[0].name} and ${selected[1].time} seconds in ${selected[1].name}.`;
    scopeNote.textContent = 'One observation is missing. A claim about all three temperatures would now go beyond the available facts.';
  } else if (selected.length === 1) {
    boundedDescription.textContent = `The only available observation is ${selected[0].time} seconds in ${selected[0].name}.`;
    scopeNote.textContent = 'A single observation cannot support a comparison across temperatures.';
  } else {
    boundedDescription.textContent = 'No observations are available to describe.';
    scopeNote.textContent = 'Review the supplied facts before writing a comparison.';
  }

  if (animateFeedback && !reducedMotion.matches && demoOutput.animate) {
    feedbackAnimation?.cancel();
    feedbackAnimation = demoOutput.animate(
      [{ opacity: 0.55, clipPath: 'inset(0 0 5% 0)' }, { opacity: 1, clipPath: 'inset(0 0 0 0)' }],
      { duration: 260, easing: 'cubic-bezier(0.16, 1, 0.3, 1)' }
    );
  }
}

for (const input of factInputs) {
  input.disabled = false;
  input.addEventListener('change', () => updateEvidenceExample(true));
}

resetFacts.addEventListener('click', () => {
  for (const input of factInputs) input.checked = true;
  updateEvidenceExample(true);
});

updateEvidenceExample();

const mobileNav = document.querySelector('.mobile-nav');
for (const link of mobileNav.querySelectorAll('a[href^="#"]')) {
  link.addEventListener('click', () => { mobileNav.open = false; });
}

const reasoningLine = document.querySelector('.reasoning-line');
const reasoningSteps = Array.from(reasoningLine.querySelectorAll('li'));
const storyStage = document.querySelector('[data-story-stage]');
const storySteps = Array.from(document.querySelectorAll('[data-story-step]'));
const storyShots = Array.from(document.querySelectorAll('[data-story-shot]'));
const pageProgress = document.querySelector('.page-progress');
const boundaryScene = document.querySelector('[data-boundary-scene]');
const revisionDesk = document.querySelector('[data-revision-desk]');
let activeStoryIndex = -1;
let scrollQueued = false;
let revealObserver;
let revisionObserver;

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value));
}

function setActiveStory(index) {
  if (index === activeStoryIndex) return;
  activeStoryIndex = index;
  storyStage.dataset.active = String(index);
  storyStage.setAttribute(
    'aria-label',
    `Case practice screen ${index + 1} of ${storyShots.length}: ${storyShots[index].alt}`
  );

  storySteps.forEach((step, stepIndex) => step.classList.toggle('is-current', stepIndex === index));
  storyShots.forEach((shot, shotIndex) => {
    shot.classList.toggle('is-current', shotIndex === index);
    shot.setAttribute('aria-hidden', String(shotIndex !== index));
  });
}

function updateScrollMotion() {
  const scrollableHeight = document.documentElement.scrollHeight - window.innerHeight;
  const pageFill = scrollableHeight > 0 ? clamp(window.scrollY / scrollableHeight, 0, 1) : 0;
  pageProgress.style.setProperty('--page-progress', pageFill.toFixed(4));

  const boundaryTop = boundaryScene.getBoundingClientRect().top;
  const boundaryProgress = clamp((window.innerHeight * 0.85 - boundaryTop) / (window.innerHeight * 0.7), 0, 1);
  const boundaryOffset = reducedMotion.matches ? 0 : 28 * (1 - boundaryProgress);
  boundaryScene.style.setProperty('--boundary-offset', `${boundaryOffset.toFixed(1)}px`);
  boundaryScene.style.setProperty('--boundary-fill', boundaryProgress.toFixed(3));

  const lineTop = reasoningLine.getBoundingClientRect().top;
  const progress = clamp((window.innerHeight * 0.85 - lineTop) / (window.innerHeight * 0.6), 0, 1);
  reasoningSteps.forEach((step, index) => {
    const fill = reducedMotion.matches ? 1 : clamp(progress * reasoningSteps.length - index, 0, 1);
    step.style.setProperty('--segment-fill', fill.toFixed(3));
    step.classList.toggle('is-passed', fill > 0.05);
  });

  const focusLine = window.innerHeight * 0.48;
  let visibleStep = 0;
  let nearestDistance = Number.POSITIVE_INFINITY;
  for (const [index, step] of storySteps.entries()) {
    const rect = step.getBoundingClientRect();
    const distance = focusLine < rect.top
      ? rect.top - focusLine
      : focusLine > rect.bottom
        ? focusLine - rect.bottom
        : 0;
    if (distance < nearestDistance) {
      nearestDistance = distance;
      visibleStep = index;
    }
  }
  setActiveStory(visibleStep);
}

function queueScrollMotion() {
  if (scrollQueued) return;
  scrollQueued = true;
  window.requestAnimationFrame(() => {
    scrollQueued = false;
    updateScrollMotion();
  });
}

function setupEntranceMotion() {
  if (reducedMotion.matches || !('IntersectionObserver' in window)) return;

  const groups = [
    { selector: '.assurance-grid p', motion: 'rise', stagger: 85 },
    { selector: '.thinking-grid > div:first-child', motion: 'from-left' },
    { selector: '.thinking-copy', motion: 'from-right' },
    { selector: '.demo-heading > *', motion: 'rise', stagger: 90 },
    { selector: '.demo-grid', motion: 'scale' },
    { selector: '.boundary-heading > *', motion: 'rise', stagger: 90 },
    { selector: '.boundary-endnote', motion: 'soft' },
    { selector: '.inside-heading > *', motion: 'rise', stagger: 90 },
    { selector: '.story-step h3, .story-step p', motion: 'rise', stagger: 55 },
    { selector: '.story-step-image', motion: 'uncover' },
    { selector: '.revision-intro > *', motion: 'rise', stagger: 90 },
    { selector: '.revision-sheet', motion: 'scale', stagger: 135 },
    { selector: '.revision-outro', motion: 'soft' },
    { selector: '.principles-lead', motion: 'from-left' },
    { selector: '.principles-list', motion: 'from-right' },
    { selector: '.faq-intro', motion: 'from-left' },
    { selector: '.faq-list', motion: 'soft' },
    { selector: '.final-inner', motion: 'rise' },
  ];

  revealObserver = new IntersectionObserver((entries, observer) => {
    for (const entry of entries) {
      if (!entry.isIntersecting) continue;
      entry.target.classList.add('is-revealed');
      observer.unobserve(entry.target);
    }
  }, { threshold: 0.12, rootMargin: '0px 0px -7% 0px' });

  for (const group of groups) {
    document.querySelectorAll(group.selector).forEach((element, index) => {
      if (!element.getClientRects().length) return;
      element.dataset.reveal = group.motion;
      element.style.setProperty('--reveal-delay', `${Math.min(index * (group.stagger || 0), 240)}ms`);
      const rect = element.getBoundingClientRect();
      if (rect.bottom <= 0 || rect.top < window.innerHeight * 0.88) {
        element.classList.add('is-revealed');
        return;
      }
      element.classList.add('reveal-prep');
      revealObserver.observe(element);
    });
  }

  document.documentElement.classList.add('motion-ready');
  revisionObserver = new IntersectionObserver((entries, observer) => {
    for (const entry of entries) {
      if (!entry.isIntersecting) continue;
      revisionDesk.classList.add('is-entered');
      observer.unobserve(entry.target);
    }
  }, { threshold: 0.16, rootMargin: '0px 0px -8% 0px' });
  if (revisionDesk.getBoundingClientRect().top < window.innerHeight * 0.88) {
    revisionDesk.classList.add('is-entered');
  } else {
    revisionObserver.observe(revisionDesk);
  }

  document.addEventListener('focusin', (event) => {
    const target = event.target.closest('.reveal-prep');
    if (!target) return;
    target.classList.add('is-revealed');
    revealObserver?.unobserve(target);
  });
}

setupEntranceMotion();

window.addEventListener('scroll', queueScrollMotion, { passive: true });
window.addEventListener('resize', queueScrollMotion);
window.addEventListener('load', queueScrollMotion);
reducedMotion.addEventListener('change', () => {
  if (reducedMotion.matches) {
    revealObserver?.disconnect();
    revisionObserver?.disconnect();
    document.querySelectorAll('.reveal-prep').forEach((element) => element.classList.add('is-revealed'));
    revisionDesk.classList.add('is-entered');
    document.documentElement.classList.remove('motion-ready');
  }
  queueScrollMotion();
});
queueScrollMotion();
document.documentElement.classList.add('story-scroll-ready');
