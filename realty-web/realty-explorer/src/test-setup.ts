import "@testing-library/jest-dom";

// jsdom implements neither of these, and Ant Design reads both: matchMedia from its
// responsive grid and colour-scheme detection, ResizeObserver from the overflow
// handling in Menu and Table. Answering "no match" and "never resized" is enough --
// layout is not what these tests assert.
if (typeof window !== "undefined") {
  if (!window.matchMedia) {
    window.matchMedia = (query: string): MediaQueryList => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
    });
  }
  // jsdom's getComputedStyle rejects a pseudo-element argument outright, and Ant
  // Design's click ripple asks for one. The element's own style is answer enough.
  const computedStyle = window.getComputedStyle.bind(window);
  window.getComputedStyle = (element: Element) => computedStyle(element);
  if (!window.ResizeObserver) {
    window.ResizeObserver = class {
      observe() {}
      unobserve() {}
      disconnect() {}
    };
  }
}
