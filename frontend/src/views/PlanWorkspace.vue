<template>
  <div class="workspace">
    <header class="topbar">
      <button class="brand" @click="goBack"><span>F</span><b>FitPlan</b></button>
      <div class="run-state"><i :class="connectionStatus"></i>{{ statusText }}</div>
      <button class="home-link" @click="goBack">返回首页</button>
    </header>

    <div class="layout">
      <aside class="sidebar">
        <div class="side-title"><small>QUICK START</small><h2>选择一个目标开始</h2></div>
        <button v-for="item in examples" :key="item.title" class="example" @click="submitExample(item.prompt)">
          <span>{{ item.icon }}</span><div><b>{{ item.title }}</b><small>{{ item.desc }}</small></div><i>↗</i>
        </button>
        <div class="capability">
          <small>RAG CAPABILITIES</small>
          <div><span>⌕</span> 循证知识检索</div><div><span>⌁</span> 个性化训练计划</div>
          <div><span>▤</span> 渐进负荷设计</div><div><span>⚡</span> 风险边界识别</div>
        </div>
      </aside>

      <main class="main-panel">
        <div class="panel-head">
          <div><small>FITNESS WORKSPACE</small><h1>个人训练计划工作台</h1></div>
          <div class="model-chip"><i></i> Fitness RAG</div>
        </div>
        <ChatRoom :messages="messages" :connection-status="connectionStatus" ai-type="super" @send-message="sendMessage" />
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useHead } from '@vueuse/head'
import ChatRoom from '../components/ChatRoom.vue'
import { streamFitnessPlan } from '../api'

useHead({ title: '个人训练计划｜FitPlan' })
const router = useRouter()
const messages = ref([])
const connectionStatus = ref('disconnected')
let eventSource = null
let receivedData = false
const chatId = `fitness_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`

const examples = [
  { icon: '◆', title: '健身房增肌', desc: '每周三练的全身进阶方案', prompt: '我 25 岁，健身新手，目标增肌，每周能去健身房 3 次，每次 60 分钟，没有已知伤病。请帮我制定 4 周计划。' },
  { icon: '⌁', title: '减脂塑形', desc: '力量与有氧组合安排', prompt: '我想减脂并提高体能，每周能训练 4 天，每次 45 分钟。请先告诉我还需要提供哪些信息。' },
  { icon: '◉', title: '居家训练', desc: '少器械也能稳定执行', prompt: '我只有瑜伽垫和一对哑铃，每周能练 3 天，希望改善体能和体态。请为我定制计划。' }
]
const statusText = computed(() => ({ connecting: '正在检索并生成计划', error: '连接异常', disconnected: 'FitPlan 就绪' }[connectionStatus.value]))
const addMessage = (content, isUser, type = '') => messages.value.push({ content, isUser, type, time: Date.now() })
const submitExample = prompt => { if (connectionStatus.value !== 'connecting') sendMessage(prompt) }

const sendMessage = message => {
  addMessage(message, true, 'user-question')
  eventSource?.close()
  connectionStatus.value = 'connecting'
  receivedData = false
  const reply = { content: '', isUser: false, type: 'ai-answer', time: Date.now() }
  messages.value.push(reply)
  eventSource = streamFitnessPlan(message, chatId)
  eventSource.onmessage = event => {
    if (!event.data || event.data === '[DONE]') return
    receivedData = true
    reply.content += event.data
  }
  eventSource.onerror = () => {
    connectionStatus.value = receivedData ? 'disconnected' : 'error'
    if (!receivedData) reply.content = '暂时无法连接健身计划服务，请确认后端已经重启并正常运行。'
    eventSource?.close()
  }
}

const goBack = () => router.push('/')
onMounted(() => addMessage('你好，我是 FitPlan。告诉我你的年龄、目标、训练经验、每周可训练时间、器械条件和伤病情况，我会结合健身知识库为你制定可执行的计划。', false, 'welcome'))
onBeforeUnmount(() => eventSource?.close())
</script>

<style scoped>
.workspace{min-height:100vh;background:#eeede8;color:#191b18}.topbar{height:68px;background:#151815;color:#f7f7f3;display:flex;align-items:center;justify-content:space-between;padding:0 28px;border-bottom:1px solid #30342f}.brand{display:flex;align-items:center;gap:10px;background:none;border:0;color:inherit;font-size:17px}.brand span{display:grid;place-items:center;width:32px;height:32px;border-radius:8px;background:#b6f83b;color:#121512;font:italic bold 18px Georgia}.run-state{position:absolute;left:50%;transform:translateX(-50%);font-size:11px;letter-spacing:.5px;color:#a3a79f}.run-state i{display:inline-block;width:7px;height:7px;border-radius:50%;background:#b6f83b;margin-right:8px;box-shadow:0 0 9px #b6f83b}.run-state i.connecting{animation:pulse 1s infinite}.run-state i.error{background:#ff765e}.home-link{border:1px solid #3c413b;border-radius:7px;background:transparent;color:#c8ccc5;padding:9px 13px;font-size:12px}.layout{width:min(1420px,100%);margin:auto;display:grid;grid-template-columns:300px 1fr;min-height:calc(100vh - 68px)}.sidebar{padding:34px 24px;border-right:1px solid #d4d3cc}.side-title small,.panel-head small,.capability>small{font-size:9px;letter-spacing:1.7px;color:#80837e;font-weight:800}.side-title h2{font-size:19px;margin:9px 0 22px}.example{width:100%;display:grid;grid-template-columns:34px 1fr auto;gap:10px;align-items:center;text-align:left;padding:14px 12px;margin-bottom:9px;border:1px solid #d5d4cd;border-radius:10px;background:#f7f6f2;color:#20221f;transition:.2s}.example:hover{border-color:#9bd51e;transform:translateY(-1px);box-shadow:0 8px 22px #2830140d}.example>span{display:grid;place-items:center;width:32px;height:32px;border-radius:8px;background:#e8e7e1}.example b,.example small{display:block}.example b{font-size:13px}.example small{font-size:10px;color:#858781;margin-top:4px}.example i{font-style:normal;color:#8a8d87}.capability{margin-top:34px;padding-top:23px;border-top:1px solid #d4d3cc}.capability>div{font-size:12px;margin-top:16px;color:#666963}.capability span{display:inline-block;width:25px;color:#679600}.main-panel{padding:34px;min-width:0}.panel-head{display:flex;justify-content:space-between;align-items:center;margin-bottom:22px}.panel-head h1{font-size:25px;margin-top:6px}.model-chip{padding:8px 12px;border:1px solid #d0cfc8;border-radius:20px;font:11px monospace;color:#636660;background:#f5f4ef}.model-chip i{display:inline-block;width:6px;height:6px;background:#8ccb12;border-radius:50%;margin-right:6px}@keyframes pulse{50%{opacity:.3}}@media(max-width:820px){.layout{grid-template-columns:1fr}.sidebar{display:none}.main-panel{padding:20px}.run-state{display:none}}@media(max-width:480px){.topbar{padding:0 15px}.home-link{font-size:0}.home-link:after{content:'首页';font-size:12px}.panel-head h1{font-size:21px}.model-chip{display:none}}
</style>
