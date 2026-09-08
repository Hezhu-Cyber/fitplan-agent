<template>
  <footer class="footer">
    <div class="inner">
      <div class="brand"><span>F</span><div><b>FitPlan</b><small>FITNESS RAG COACH</small></div></div>
      <p>让每一次训练都有依据、能执行、可复盘。</p>
      <small>© {{ currentYear }} FitPlan · Built with Spring AI & Vue</small>
    </div>
  </footer>
</template>
<script setup>
import { computed } from 'vue'
const currentYear = computed(() => new Date().getFullYear())
</script>
<style scoped>
.footer{background:#111411;color:#858a83;border-top:1px solid #2c302c;padding:34px 0}.inner{width:min(1180px,calc(100% - 40px));margin:auto;display:flex;align-items:center;justify-content:space-between;gap:24px;font-size:12px}.brand{display:flex;align-items:center;gap:10px}.brand>span{display:grid;place-items:center;width:30px;height:30px;border-radius:8px;background:#b6f83b;color:#121512;font:italic bold 18px Georgia}.brand b,.brand small{display:block}.brand b{color:#f3f4f1;font-size:14px}.brand small{font-size:9px;letter-spacing:1px;margin-top:2px}@media(max-width:700px){.inner{flex-direction:column;text-align:center}}
</style>
