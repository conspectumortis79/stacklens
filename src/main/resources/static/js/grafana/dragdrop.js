export const DragdropMixin = {


    _initDragDrop(el, panelIdx) {
      const DRAG_THRESHOLD = 6;

      el.addEventListener('mousedown', (e) => {
        if (e.button !== 0) return;
        if (e.target.closest('.gb-panel-edit-btn, .gb-panel-delete-btn')) return;

        let dragging  = false;
        let targetIdx = -1;
        let targetEl  = null;
        const startX  = e.clientX;
        const startY  = e.clientY;
        let siblings  = [];

        const onSiblingEnter = function () {
          if (targetEl) targetEl.classList.remove('gb-drag-over');
          const idx = parseInt(this.getAttribute('data-panel-idx'), 10);
          if (isNaN(idx)) return;
          targetIdx = idx;
          targetEl  = this;
          this.classList.add('gb-drag-over');
        };
        const onSiblingLeave = function (ev) {
          if (this.contains(ev.relatedTarget)) return;
          this.classList.remove('gb-drag-over');
          if (targetEl === this) { targetEl = null; targetIdx = -1; }
        };

        const startDrag = () => {
          dragging = true;
          el.classList.add('gb-dragging');
          document.body.style.userSelect = 'none';
          if (this._container) {
            this._container.querySelectorAll('[data-panel-idx]').forEach(p => {
              if (p === el) return;
              p.addEventListener('mouseenter', onSiblingEnter);
              p.addEventListener('mouseleave', onSiblingLeave);
              siblings.push(p);
            });
          }
        };

        const cleanup = () => {
          el.classList.remove('gb-dragging');
          document.body.style.userSelect = '';
          siblings.forEach(p => {
            p.removeEventListener('mouseenter', onSiblingEnter);
            p.removeEventListener('mouseleave', onSiblingLeave);
            p.classList.remove('gb-drag-over');
          });
          siblings = [];
        };

        const onMove = (e2) => {
          if (!dragging) {
            if (Math.hypot(e2.clientX - startX, e2.clientY - startY) < DRAG_THRESHOLD) return;
            startDrag();
          }
        };

        const onUp = () => {
          document.removeEventListener('mousemove', onMove);
          document.removeEventListener('mouseup',   onUp);
          cleanup();

          if (dragging) {
            const suppress = (e3) => {
              e3.stopPropagation();
              el.removeEventListener('click', suppress, true);
            };
            el.addEventListener('click', suppress, true);
            if (targetIdx >= 0) this._swapPanels(panelIdx, targetIdx);
          }
        };

        document.addEventListener('mousemove', onMove);
        document.addEventListener('mouseup',   onUp);
      });
    },

    _swapPanels(srcIdx, dstIdx) {
      if (!this._dashObj) return;
      const panels = this._dashObj.panels || [];
      const src = panels[srcIdx];
      const dst = panels[dstIdx];
      if (!src || !dst) return;

      const srcGp = Object.assign({}, src.gridPos || { x: 0, y: srcIdx * 8, w: 24, h: 8 });
      const dstGp = Object.assign({}, dst.gridPos || { x: 0, y: dstIdx * 8, w: 24, h: 8 });
      src.gridPos = dstGp;
      dst.gridPos = srcGp;

      this._syncJsonFromDash();

      const cached = this._panelApiData.length ? this._panelApiData.slice() : null;
      this.renderFromTextarea(cached);
    },

};
