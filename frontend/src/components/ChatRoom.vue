<template>
  <section class="chat">
    <div ref="messagesContainer" class="messages">
      <div v-for="(msg,index) in messages" :key="index" class="row" :class="msg.isUser ? 'user' : 'agent'">
        <div v-if="!msg.isUser" class="avatar">F</div>
        <div class="bubble" :class="msg.type">
          <div v-if="!msg.isUser" class="label">FITPLAN COACH <span v-if="msg.type==='agent-step'">· RAG</span></div>
          <div class="content">{{ msg.content }}</div>
          <time>{{ formatTime(msg.time) }}</time>
        </div>
      </div>
      <div v-if="connectionStatus==='connecting'" class="thinking"><span></span><span></span><span></span> 正在检索知识库并生成建议</div>
    </div>
    <div class="composer">
      <textarea v-model="inputMessage" @keydown.enter.exact.prevent="sendMessage" maxlength="2000" placeholder="描述你的目标、经验、时间、器械和身体情况..." :disabled="connectionStatus==='connecting'"></textarea>
      <div class="composer-bottom"><span>Enter 发送 · Shift + Enter 换行</span><button @click="sendMessage" :disabled="connectionStatus==='connecting'||!inputMessage.trim()">生成计划 <b>↗</b></button></div>
    </div>
  </section>
</template>
<script setup>
import { ref,nextTick,watch } from 'vue'
const props=defineProps({messages:{type:Array,default:()=>[]},connectionStatus:{type:String,default:'disconnected'},aiType:{type:String,default:'default'}})
const emit=defineEmits(['send-message'])
const inputMessage=ref('');const messagesContainer=ref(null)
const sendMessage=()=>{if(!inputMessage.value.trim())return;emit('send-message',inputMessage.value.trim());inputMessage.value=''}
const formatTime=t=>new Date(t).toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit'})
const scroll=async()=>{await nextTick();if(messagesContainer.value)messagesContainer.value.scrollTop=messagesContainer.value.scrollHeight}
watch(()=>[props.messages.length,props.messages.map(m=>m.content).join('')],scroll)
</script>
<style scoped>
.chat{height:calc(100vh - 160px);min-height:600px;border:1px solid #d3d2cb;border-radius:14px;background:#f8f7f3;display:flex;flex-direction:column;overflow:hidden;box-shadow:0 12px 35px #25281f0a}.messages{flex:1;overflow-y:auto;padding:30px}.row{display:flex;gap:11px;margin-bottom:22px;align-items:flex-start}.row.user{justify-content:flex-end}.avatar{flex:0 0 31px;height:31px;display:grid;place-items:center;border-radius:8px;background:#1a1d1a;color:#b6f83b;font:italic bold 16px Georgia}.bubble{max-width:min(76%,760px);border:1px solid #deddd6;border-radius:4px 12px 12px 12px;background:#fff;padding:14px 16px;box-shadow:0 4px 14px #20231b08}.user .bubble{background:#1a1d1a;color:#f5f6f2;border-color:#1a1d1a;border-radius:12px 4px 12px 12px}.bubble.agent-step{border-left:3px solid #a7e32d;background:#fbfcf8}.label{font-size:8px;letter-spacing:1.3px;color:#7f847c;font-weight:800;margin-bottom:8px}.label span{color:#75a80e}.content{white-space:pre-wrap;word-break:break-word;font-size:13px;line-height:1.75}.bubble time{display:block;margin-top:8px;font-size:9px;color:#9a9d96;text-align:right}.thinking{display:flex;align-items:center;gap:5px;margin-left:43px;color:#8a8e87;font-size:10px}.thinking span{width:5px;height:5px;border-radius:50%;background:#8ac716;animation:blink 1.2s infinite}.thinking span:nth-child(2){animation-delay:.2s}.thinking span:nth-child(3){animation-delay:.4s;margin-right:5px}.composer{margin:18px;border:1px solid #cccbc4;border-radius:11px;background:#fff;padding:13px;box-shadow:0 8px 20px #1f231a0b}.composer textarea{display:block;width:100%;height:58px;resize:none;border:0;outline:0;background:transparent;color:#20231f;font:13px/1.6 inherit}.composer textarea::placeholder{color:#a3a59f}.composer-bottom{display:flex;align-items:center;justify-content:space-between;border-top:1px solid #eeede8;padding-top:10px}.composer-bottom>span{font-size:9px;color:#a1a39e}.composer button{border:0;border-radius:7px;background:#1a1d1a;color:#fff;padding:9px 13px;font-size:11px;font-weight:700}.composer button b{color:#b6f83b;margin-left:7px}.composer button:disabled{opacity:.4;cursor:not-allowed}@keyframes blink{50%{opacity:.2}}@media(max-width:700px){.chat{height:calc(100vh - 135px);min-height:520px}.messages{padding:18px}.bubble{max-width:87%}.composer{margin:10px}.composer-bottom>span{display:none}}
</style>
