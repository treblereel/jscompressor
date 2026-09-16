/*
 * Copyright © 2025 Treblereel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

document.addEventListener('alpine:init', () => {
  Alpine.data('mobileNavigation', () => ({
    open: false
  }));

  Alpine.data('compilerApp', () => ({
    leftWidth: 50,
    dragging: false,
    windowWidth: window.innerWidth,
    textareaContent: `function hello(name) {
    alert('Hello, ' + name);
}
hello('New user');`,
    optimization: 'Whitespace only',
    prettyPrint: false,
    printInputDelimiter: false,
    warningLevel: 'DEFAULT',
    outputFileName: 'default.js',
    languageIn: 'ECMASCRIPT_2021',
    languageOut: 'ECMASCRIPT_2021',
    selectedKeys: [],
    showFormatting: false,
    activeTab: 'compiledCode',
    loading: false,
    success: false,
    error: false,
    errorMessage: '',
    copyLabel: 'Copy code to the clipboard',
    resizeHandler: null,
    result: {
      originalSize: 0,
      compiledSize: 0,
      compressionRatio: null,
      downloadId: '',
      outputFileName: ''
    },

    get outputFileNameValid() {
      return /^[a-zA-Z0-9_-]+\.js$/.test(this.outputFileName);
    },

    init() {
      this.resizeHandler = () => {
        this.windowWidth = window.innerWidth;
      };
      window.addEventListener('resize', this.resizeHandler);

      Alpine.store('compiler', {
        compiledCode: '',
        warnings: [],
        errors: [],
        postData: {}
      });

      this.$nextTick(() => window.dispatchEvent(new Event('jscompressor:update-line-numbers')));
    },

    destroy() {
      window.removeEventListener('resize', this.resizeHandler);
    },

    async handleCompilerClick() {
      if (this.loading) {
        return;
      }

      this.loading = true;
      this.success = false;
      this.error = false;
      this.errorMessage = '';
      this.result.originalSize = 0;
      this.result.compiledSize = 0;
      this.result.compressionRatio = null;
      this.result.downloadId = '';
      this.result.outputFileName = '';
      this.activeTab = 'compiledCode';

      Alpine.store('compiler').compiledCode = '';
      Alpine.store('compiler').warnings = [];
      Alpine.store('compiler').errors = [];
      Alpine.store('compiler').postData = {};

      const postData = {
        payload: this.textareaContent,
        compilationLevel: this.optimization,
        warningLevel: this.warningLevel,
        outputFileName: this.outputFileName,
        language: {
          languageIn: this.languageIn,
          languageOut: this.languageOut
        },
        formatting: {
          prettyPrint: this.prettyPrint,
          printInputDelimiter: this.printInputDelimiter
        },
        externalScripts: { urls: this.selectedKeys }
      };
      Alpine.store('compiler').postData = postData;

      try {
        const response = await fetch('/compile', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json'
          },
          body: JSON.stringify(postData)
        });

        if (!response.ok) {
          let message = `Request failed with status ${response.status}`;
          try {
            const errorResponse = await response.json();
            if (errorResponse && typeof errorResponse.error === 'string') {
              message = errorResponse.error;
            }
          } catch (ignored) {
          }
          throw new Error(message);
        }

        const result = await response.json();
        this.onPostFinished(result, postData);
      } catch (error) {
        console.error('Error during compilation:', error);
        this.errorMessage = error instanceof Error ? error.message : 'Compilation request failed';
        this.error = true;
      } finally {
        this.loading = false;
      }
    },

    onPostFinished(result, postData) {
      if (result.errors && result.errors.length > 0) {
        this.error = true;
        this.errorMessage = 'Compilation completed with errors. See the Errors tab.';
        this.activeTab = 'errors';
      } else {
        this.success = true;
        this.activeTab = 'compiledCode';
      }

      this.result.originalSize = result.statistics.originalSize;
      this.result.compiledSize = result.statistics.compiledSize;
      this.result.compressionRatio = result.statistics.originalSize > 0
        ? ((result.statistics.compiledSize / result.statistics.originalSize) * 100).toFixed(2)
        : null;
      this.result.outputFileName = postData.outputFileName;
      this.result.downloadId = result.downloadId;

      Alpine.store('compiler').compiledCode = result.compiledCode || '';
      Alpine.store('compiler').warnings = result.warnings || [];
      Alpine.store('compiler').errors = result.errors || [];
      Alpine.store('compiler').postData = postData;
    },

    async copyCompiledCode() {
      try {
        await navigator.clipboard.writeText(Alpine.store('compiler').compiledCode);
        this.copyLabel = 'Copied';
      } catch (error) {
        this.copyLabel = 'Copy failed';
      }
      setTimeout(() => {
        this.copyLabel = 'Copy code to the clipboard';
      }, 1500);
    },

    startDragging() {
      if (this.windowWidth >= 768) {
        this.dragging = true;
      }
    },

    stopDragging() {
      this.dragging = false;
    },

    doDrag(event) {
      if (!this.dragging || this.windowWidth < 768) {
        return;
      }

      const containerWidth = this.$refs.container.offsetWidth;
      const offset = event.clientX - this.$refs.container.getBoundingClientRect().left;
      this.leftWidth = Math.min(70, Math.max(30, (offset / containerWidth) * 100));
    },

    adjustPanelWidth(change) {
      this.leftWidth = Math.min(70, Math.max(30, this.leftWidth + change));
    },

    selectAdjacentTab(change) {
      const tabs = ['compiledCode', 'warnings', 'errors', 'postData'];
      const currentIndex = tabs.indexOf(this.activeTab);
      const nextIndex = (currentIndex + change + tabs.length) % tabs.length;
      this.activeTab = tabs[nextIndex];
      this.$nextTick(() => document.getElementById(`tab-${this.activeTab}`).focus());
    },

    get leftPanelStyle() {
      return this.windowWidth < 768 ? 'width: 100%' : `width: ${this.leftWidth}%`;
    },

    get rightPanelStyle() {
      return this.windowWidth < 768 ? 'width: 100%' : `width: ${100 - this.leftWidth}%`;
    }
  }));

  Alpine.data('externalScriptsSelector', () => ({
    open: false,
    selectedKeys: [],
    options: [
      { key: 'closure_library_base', value: 'Closure Library' },
      { key: 'chrome_frame', value: 'Chrome frame' },
      { key: 'dojo', value: 'Dojo' },
      { key: 'ext_core', value: 'Ext core' },
      { key: 'jquery', value: 'JQuery' },
      { key: 'jquery_ui', value: 'JQuery UI' },
      { key: 'mootools', value: 'Mootools' },
      { key: 'prototype', value: 'Prototype' },
      { key: 'scriptaculous', value: 'Scriptaculous' },
      { key: 'swfobject', value: 'SWFObject' },
      { key: 'yui', value: 'Yahoo UI' },
      { key: 'fonts_loader', value: 'Fonts loader' }
    ],
    newOption: '',

    addCustomValue() {
      if (!this.newOption.trim()) {
        return;
      }

      const value = this.newOption.trim();
      const exists = this.options.some(option => option.key === value || option.value === value);
      if (!exists) {
        this.options.push({ key: value, value });
      }
      if (!this.selectedKeys.includes(value)) {
        this.selectedKeys.push(value);
      }
      this.updateSelectedKeys();
      this.newOption = '';
    },

    getSelectedValues() {
      return this.options
        .filter(option => this.selectedKeys.includes(option.key))
        .map(option => option.value);
    },

    updateSelectedKeys() {
      this.$dispatch('update-selected-keys', this.selectedKeys);
    }
  }));

  Alpine.data('termsDialog', () => ({
    isModalOpen: false,

    openModal() {
      this.isModalOpen = true;
      this.$nextTick(() => this.$refs.termsClose.focus());
    },

    closeModal() {
      this.isModalOpen = false;
      this.$nextTick(() => document.getElementById('terms-link').focus());
    },

    trapModalFocus(event) {
      const selector = `button, [href], [tabindex]:not([tabindex='-1'])`;
      const focusable = this.$refs.termsDialog.querySelectorAll(selector);
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    }
  }));
});

window.syncTextareaLineNumbersScroll = () => {
  const textarea = document.getElementById('customTextarea');
  const lineNumbers = document.getElementById('customTextareaLineNumbers');

  if (!textarea || !lineNumbers) {
    return;
  }

  lineNumbers.scrollTop = textarea.scrollTop;
};

window.updateTextareaLineNumbers = () => {
  const textarea = document.getElementById('customTextarea');
  const lineNumbers = document.getElementById('customTextareaLineNumbers');

  if (!textarea || !lineNumbers) {
    return;
  }

  const lineCount = Math.max(1, textarea.value.split('\n').length);
  lineNumbers.textContent = Array.from({ length: lineCount }, (_, index) => index + 1).join('\n');
  window.syncTextareaLineNumbersScroll();
};

document.addEventListener('DOMContentLoaded', () => {
  const textarea = document.getElementById('customTextarea');

  if (!textarea) {
    return;
  }

  textarea.addEventListener('input', window.updateTextareaLineNumbers);
  textarea.addEventListener('scroll', window.syncTextareaLineNumbersScroll);
  setTimeout(window.updateTextareaLineNumbers, 0);
  setTimeout(window.updateTextareaLineNumbers, 100);
});

window.addEventListener('jscompressor:update-line-numbers', () => {
  setTimeout(window.updateTextareaLineNumbers, 0);
});
