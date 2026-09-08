<template>
  <main class="landing">
    <nav class="nav shell">
      <button class="brand" @click="scrollTop">
        <span class="brand-mark">F</span>
        <span>FitPlan</span>
      </button>
      <div class="nav-links">
        <a href="#features">核心能力</a>
        <a href="#workflow">生成流程</a>
        <button class="nav-cta" @click="startResearch">制定我的计划</button>
      </div>
    </nav>

    <section class="hero shell">
      <div class="hero-copy">
        <div class="eyebrow"><span></span> FITNESS RAG COACH</div>
        <h1>把身体目标，<br /><em>变成一份<br />能坚持的计划</em></h1>
        <p class="hero-desc">FitPlan 基于循证健身知识库，结合你的目标、时间、经验与器械条件，生成安全、可执行、可递进的训练方案。</p>
        <div class="hero-actions">
          <button class="primary" @click="startResearch">生成训练计划 <span>↗</span></button>
          <a class="secondary" href="#workflow">查看生成方式</a>
        </div>
        <div class="trust-row">
          <span>✓ 知识库增强</span><span>✓ 个性化约束</span><span>✓ 安全边界识别</span>
        </div>
      </div>

      <div class="agent-card">
        <div class="card-top">
          <div class="window-dots"><i></i><i></i><i></i></div>
          <span>PLAN BUILD / 04</span>
          <span class="live"><b></b> LIVE</span>
        </div>
        <div class="prompt-box">
          <small>用户目标</small>
          <p>健身新手，每周训练 3 天，使用健身房器械，目标是增肌并提高体能。</p>
        </div>
        <div class="steps">
          <div class="step done"><span>01</span><i>✓</i><div><b>建立用户画像</b><small>目标、经验、时间与器械条件</small></div></div>
          <div class="step done"><span>02</span><i>✓</i><div><b>检索健身知识库</b><small>匹配活动指南与训练原则</small></div></div>
          <div class="step active"><span>03</span><i></i><div><b>组装个性化计划</b><small>正在平衡训练量与恢复...</small></div></div>
          <div class="step"><span>04</span><i></i><div><b>生成递进规则</b><small>等待执行</small></div></div>
        </div>
        <div class="card-metrics"><span><b>5</b> 知识域</span><span><b>3</b> 训练日</span><span><b>4</b> 周周期</span></div>
      </div>
    </section>

    <section id="features" class="features shell">
      <div class="section-head"><span>CAPABILITIES</span><h2>不只是给动作，更给出可执行逻辑</h2></div>
      <div class="feature-grid">
        <article><span class="index">01</span><div class="icon">⌁</div><h3>用户画像</h3><p>综合目标、经验、时间、器械和身体限制，避免模板化计划。</p></article>
        <article><span class="index">02</span><div class="icon">⌕</div><h3>RAG 检索</h3><p>从本地健身知识库召回相关指南与训练原则，增强回答依据。</p></article>
        <article><span class="index">03</span><div class="icon">⚡</div><h3>渐进训练</h3><p>使用 RPE、RIR 与双重递进规则，让计划能够持续迭代。</p></article>
        <article><span class="index">04</span><div class="icon">▤</div><h3>安全边界</h3><p>识别异常症状与特殊人群，把医疗问题交给合格专业人士。</p></article>
      </div>
    </section>

    <section id="workflow" class="workflow">
      <div class="shell workflow-inner">
        <div class="section-head light"><span>HOW IT WORKS</span><h2>从身体目标到每周训练安排</h2></div>
        <div class="flow-row">
          <div><b>01</b><h3>描述你的情况</h3><p>提供目标、训练基础、时间和器械</p></div><i>→</i>
          <div><b>02</b><h3>知识检索与规划</h3><p>召回相关原则并匹配个人约束</p></div><i>→</i>
          <div><b>03</b><h3>获得训练方案</h3><p>得到周计划、强度、递进与复盘指标</p></div>
        </div>
        <button class="primary inverted" @click="startResearch">创建我的第一份计划 <span>↗</span></button>
      </div>
    </section>

    <AppFooter />
  </main>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useHead } from '@vueuse/head'
import AppFooter from '../components/AppFooter.vue'

useHead({ title: 'FitPlan｜AI 个性化健身计划' })
const router = useRouter()
const startResearch = () => router.push('/workspace')
const scrollTop = () => window.scrollTo({ top: 0, behavior: 'smooth' })
</script>

<style scoped>
.landing{min-height:100vh;background:#f5f4ef;color:#171916}.shell{width:min(1180px,calc(100% - 40px));margin:0 auto}.nav{height:78px;display:flex;align-items:center;justify-content:space-between;border-bottom:1px solid #d9d8d1}.brand{display:flex;align-items:center;gap:11px;border:0;background:none;color:#171916;font-size:20px;font-weight:800;letter-spacing:-.5px}.brand-mark{display:grid;place-items:center;width:34px;height:34px;border-radius:9px;background:#bbff32;color:#111;font-family:Georgia,serif;font-style:italic}.nav-links{display:flex;align-items:center;gap:30px;font-size:14px}.nav-links a{color:#60625e}.nav-cta{padding:11px 18px;border:1px solid #20221f;border-radius:8px;background:transparent;color:#171916;font-weight:650}.hero{display:grid;grid-template-columns:1.04fr .96fr;gap:72px;align-items:center;padding-top:88px;padding-bottom:105px}.eyebrow{display:flex;align-items:center;gap:10px;font-size:12px;font-weight:800;letter-spacing:2px;color:#60625e}.eyebrow span{width:28px;height:2px;background:#90d800}.hero h1{margin:24px 0 24px;font-size:clamp(46px,5.2vw,76px);line-height:1.06;letter-spacing:-4px;font-weight:750}.hero h1 em{font-family:Georgia,'Songti SC',serif;font-weight:400}.hero-desc{max-width:590px;font-size:18px;line-height:1.8;color:#676964}.hero-actions{display:flex;align-items:center;gap:22px;margin-top:34px}.primary{border:0;border-radius:9px;padding:16px 22px;background:#171916;color:#fff;font-weight:700;font-size:15px;box-shadow:0 8px 25px #17191624}.primary span{margin-left:18px;color:#bbff32}.secondary{font-size:14px;border-bottom:1px solid #8a8c86;padding-bottom:4px}.trust-row{display:flex;gap:22px;margin-top:32px;font-size:12px;color:#767873}.agent-card{background:#181b19;border-radius:18px;color:#f7f7f4;padding:19px;box-shadow:0 30px 70px #22261b2b;transform:rotate(1deg)}.card-top{display:grid;grid-template-columns:1fr auto 1fr;align-items:center;padding:2px 3px 15px;color:#838782;font-size:10px;letter-spacing:1.2px}.window-dots{display:flex;gap:5px}.window-dots i{width:7px;height:7px;border-radius:50%;background:#444843}.live{justify-self:end;color:#b6f83b}.live b{display:inline-block;width:6px;height:6px;border-radius:50%;background:#b6f83b;box-shadow:0 0 10px #b6f83b}.prompt-box{background:#232724;border:1px solid #303531;border-radius:11px;padding:17px 18px}.prompt-box small{font-size:10px;letter-spacing:1.5px;color:#9da19a}.prompt-box p{margin-top:8px;font-size:14px;line-height:1.65}.steps{padding:16px 7px 4px}.step{display:grid;grid-template-columns:28px 14px 1fr;gap:11px;min-height:66px;position:relative;color:#7e837d}.step:after{content:'';position:absolute;left:35px;top:24px;height:34px;width:1px;background:#343834}.step:last-child:after{display:none}.step>span{font:10px monospace;margin-top:5px}.step>i{width:14px;height:14px;border:1px solid #50554f;border-radius:50%;margin-top:2px;z-index:1;background:#181b19;font-size:9px;text-align:center;font-style:normal}.step b{display:block;font-size:13px;color:#aeb2ac}.step small{display:block;margin-top:5px;font-size:11px}.step.done>i{background:#b6f83b;border-color:#b6f83b;color:#151715}.step.done b,.step.active b{color:#fff}.step.active>i{border-color:#b6f83b;box-shadow:0 0 0 4px #b6f83b16}.step.active>i:after{content:'';display:block;width:4px;height:4px;border-radius:50%;background:#b6f83b;margin:4px}.card-metrics{display:flex;justify-content:space-around;border-top:1px solid #303430;padding-top:16px;color:#777c76;font-size:10px}.card-metrics b{color:#fff;font-size:14px;margin-right:3px}.features{padding:92px 0 110px}.section-head span{font-size:11px;letter-spacing:2px;color:#699e00;font-weight:800}.section-head h2{margin-top:12px;font-size:36px;letter-spacing:-1.4px}.feature-grid{display:grid;grid-template-columns:repeat(4,1fr);margin-top:48px;border-top:1px solid #cdccc5;border-bottom:1px solid #cdccc5}.feature-grid article{padding:27px 24px 34px;border-right:1px solid #cdccc5;position:relative}.feature-grid article:first-child{padding-left:0}.feature-grid article:last-child{border:0}.index{position:absolute;right:18px;top:18px;font:10px monospace;color:#989a95}.icon{font-size:28px;margin:18px 0 38px}.feature-grid h3{font-size:17px;margin-bottom:11px}.feature-grid p{font-size:13px;line-height:1.75;color:#6f716c}.workflow{background:#171a18;color:#f5f4ef;padding:90px 0}.section-head.light span{color:#b6f83b}.flow-row{display:grid;grid-template-columns:1fr auto 1fr auto 1fr;gap:28px;align-items:center;margin:55px 0 48px}.flow-row>div{border-top:1px solid #3a3e3a;padding-top:22px}.flow-row b{font:12px monospace;color:#b6f83b}.flow-row h3{margin:28px 0 9px;font-size:21px}.flow-row p{font-size:13px;color:#92978f}.flow-row i{color:#636860;font-style:normal}.primary.inverted{background:#b6f83b;color:#161916;box-shadow:none}.primary.inverted span{color:#161916}@media(max-width:850px){.nav-links a{display:none}.hero{grid-template-columns:1fr;padding-top:60px;gap:55px}.hero h1{letter-spacing:-2px}.agent-card{transform:none}.feature-grid{grid-template-columns:1fr 1fr}.feature-grid article:nth-child(2){border-right:0}.feature-grid article:first-child{padding-left:24px}.flow-row{grid-template-columns:1fr}.flow-row>i{display:none}}@media(max-width:560px){.shell{width:min(100% - 28px,1180px)}.nav{height:68px}.nav-links{gap:0}.hero{padding-bottom:65px}.hero h1{font-size:43px}.hero-desc{font-size:16px}.hero-actions{align-items:flex-start;flex-direction:column}.trust-row{flex-direction:column;gap:8px}.feature-grid{grid-template-columns:1fr}.feature-grid article{border-right:0;border-bottom:1px solid #cdccc5}.section-head h2{font-size:29px}.flow-row{margin-top:38px}}
.hero h1{font-size:clamp(46px,4.7vw,68px);line-height:1.05;letter-spacing:-3px}
</style>
