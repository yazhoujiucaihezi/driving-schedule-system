<template>
  <div>
    <!-- 聊天小窗口 -->
    <div
        v-if="isOpen"
        class="fixed bottom-24 right-8 w-96 h-[550px] bg-white rounded-xl shadow-2xl flex flex-col z-[9998] border border-gray-200 overflow-hidden"
    >
      <!-- 小窗口头部 -->
      <div class="bg-green-500 text-white p-4 flex justify-between items-center">
        <span class="font-bold flex items-center gap-2">
          <span class="text-xl">🤖</span> 智能练车助手
        </span>
        <button @click="isOpen = false" class="hover:bg-green-600 rounded p-1 px-2 transition-colors">
          ✖
        </button>
      </div>

      <!-- 聊天记录区 -->
      <div class="flex-1 p-4 overflow-y-auto space-y-4 bg-gray-50">
        <div
            v-for="(msg, index) in chatList"
            :key="index"
            :class="['flex', msg.role === 'user' ? 'justify-end' : 'justify-start']"
        >
          <div
              :class="[
              'max-w-[85%] p-3 rounded-lg text-sm',
              msg.role === 'user' ? 'bg-blue-500 text-white rounded-tr-none' : 'bg-white border border-gray-200 text-gray-800 rounded-tl-none shadow-sm'
            ]"
          >
            <div class="whitespace-pre-wrap">{{ msg.content }}</div>

            <!-- 确认按钮 -->
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

      <!-- 底部输入框 -->
      <div class="p-3 bg-white border-t border-gray-200 flex gap-2">
        <input
            v-model="inputMessage"
            @keyup.enter="sendMessage"
            type="text"
            placeholder="例如：帮我约明天张教练上午的课"
            class="flex-1 border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-blue-500"
        />
        <button
            @click="sendMessage"
            class="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded-lg text-sm transition-colors"
        >
          发送
        </button>
      </div>
    </div>

    <!-- 悬浮球 -->
    <div
        @click="isOpen = !isOpen"
        class="fixed bottom-8 right-8 w-14 h-14 bg-green-500 hover:bg-green-600 text-white rounded-full shadow-2xl flex items-center justify-center cursor-pointer transition-all z-[9999] hover:scale-110"
        title="AI 智能助手"
    >
      <span class="text-2xl">🤖</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import axios from 'axios'

// 控制窗口开关
const isOpen = ref(false)
// 聊天记录
const chatList = ref<Array<{ role: string, content: string, draftId?: string }>>([])
const inputMessage = ref('')

// 发送消息
const sendMessage = async () => {
  if (!inputMessage.value.trim()) return

  chatList.value.push({ role: 'user', content: inputMessage.value })
  const userMsg = inputMessage.value
  inputMessage.value = ''

  try {
    const token = localStorage.getItem('token')

    // 🚨 改成了相对路径，走 Vite 代理
    const res = await axios.get(`/api/reservations/ai/chat?message=${userMsg}`, {
      headers: { Authorization: `Bearer ${token}` }
    })

    const aiReply = typeof res.data === 'string' ? res.data : JSON.stringify(res.data)

    // 匹配草稿
    const match = aiReply.match(/\[(DRAFT_[a-zA-Z0-9]+)]/)
    const draftId = match ? match[1] : undefined

    chatList.value.push({ role: 'assistant', content: aiReply, draftId })

  } catch (error: any) {
    const errorMsg = error.response?.data?.message || error.message || '网络异常'
    chatList.value.push({ role: 'assistant', content: `出错了: ${errorMsg}` })
  }
}

// 确认下单
const handleConfirm = async (draftId: string) => {
  try {
    const token = localStorage.getItem('token')

    // 🚨 这里修好了！必须是 POST 请求，并且走相对路径调 /confirm 接口
    const res = await axios.post(`/api/reservations/ai/confirm?draftId=${draftId}`, {}, {
      headers: { Authorization: `Bearer ${token}` }
    })

    alert(typeof res.data === 'string' ? res.data : '操作成功')

  } catch (error: any) {
    alert(error.response?.data?.message || '预约失败，请稍后再试')
  }
}
</script>