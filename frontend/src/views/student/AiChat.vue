<template>

  <div class="flex flex-col h-screen max-w-2xl mx-auto p-4 bg-gray-50">
    <!-- 聊天记录区域 -->
    <div class="flex-1 overflow-y-auto mb-4 space-y-4">
      <div
          v-for="(msg, index) in chatList"
          :key="index"
          :class="['flex', msg.role === 'user' ? 'justify-end' : 'justify-start']"
      >
        <div
            :class="[
            'max-w-[80%] p-3 rounded-lg text-sm',
            msg.role === 'user' ? 'bg-blue-500 text-white' : 'bg-white border border-gray-200 text-gray-800'
          ]"
        >
          <!-- 聊天气泡文字 -->
          <div class="whitespace-pre-wrap">{{ msg.content }}</div>

          <!-- 🚨 如果 AI 的回复里提取到了 draftId，就渲染确认按钮 -->
          <button
              v-if="msg.draftId"
              @click="handleConfirm(msg.draftId)"
              class="mt-3 w-full bg-green-500 hover:bg-green-600 text-white font-bold py-2 px-4 rounded transition-colors"
          >
            ✅ 确认预约并扣减名额
          </button>
        </div>
      </div>
    </div>

    <!-- 输入框区域 -->
    <div class="flex gap-2">
      <input
          v-model="inputMessage"
          @keyup.enter="sendMessage"
          type="text"
          placeholder="例如：帮我约明天张教练上午的课"
          class="flex-1 border border-gray-300 rounded-lg px-4 py-2 focus:outline-none focus:border-blue-500"
      />
      <button
          @click="sendMessage"
          class="bg-blue-600 hover:bg-blue-700 text-white px-6 py-2 rounded-lg transition-colors"
      >
        发送
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import axios from 'axios' // 假设你项目里用了 axios，没有的话用 fetch 也行

// 聊天记录数组，包含角色、内容和可选的 draftId
const chatList = ref<Array<{ role: string, content: string, draftId?: string }>>([])
const inputMessage = ref('')

// 1. 发送消息给大模型
const sendMessage = async () => {
  if (!inputMessage.value.trim()) return

  // 添加用户消息到列表
  chatList.value.push({ role: 'user', content: inputMessage.value })
  const userMsg = inputMessage.value
  inputMessage.value = '' // 清空输入框

  try {
    // 🚨 注意：这里直接请求网关（8000），需要带上你的 Token
    const token = localStorage.getItem('token') // 假设你存在这里
    const res = await axios.get(`http://localhost:8000/api/reservations/ai/chat?message=${userMsg}`, {
      headers: { Authorization: `Bearer ${token}` }
    })

    // 后端如果直接返回字符串，用 res.data；如果包了一层 Result，用 res.data.data
    const aiReply = res.data

    // 🚨 核心：用正则提取 [DRAFT_xxx] 中的凭证号
    const match = aiReply.match(/\[(DRAFT_[a-zA-Z0-9]+)]/)
    const draftId = match ? match[1] : undefined

    // 添加 AI 回复，如果抓到了 draftId，就带上
    chatList.value.push({ role: 'assistant', content: aiReply, draftId })

  } catch (error) {
    chatList.value.push({ role: 'assistant', content: '网络异常，请稍后再试。' })
  }
}

// 2. 用户点击了确认按钮
const handleConfirm = async (draftId: string) => {
  try {
    const token = localStorage.getItem('token')

    // 🚨 发确认请求给后端，带上 draftId
    const res = await axios.post(`http://localhost:8000/api/reservations/ai/confirm?draftId=${draftId}`, {}, {
      headers: { Authorization: `Bearer ${token}` }
    })

    // 提示成功
    alert(res.data) // 或者用 Element Plus 的 ElMessage.success

    // 可选：成功后刷新页面或更新状态
  } catch (error: any) {
    alert(error.response?.data?.message || '预约失败，请稍后再试')
  }
}
</script>