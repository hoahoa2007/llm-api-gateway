<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import {
  Activity,
  AlertCircle,
  CheckCircle2,
  KeyRound,
  LayoutDashboard,
  LoaderCircle,
  Pencil,
  Play,
  Plus,
  Power,
  RadioTower,
  RefreshCw,
  ScrollText,
  Server,
  ShieldCheck,
  Trash2,
  X,
  Zap,
} from "lucide-vue-next";
import {
  askGateway,
  createChannel,
  createKey,
  deleteChannel,
  deleteKey,
  errorMessage,
  getLogSummary,
  listChannels,
  listKeys,
  listLogs,
  updateChannel,
  updateChannelStatus,
  updateKeyStatus,
} from "./api";

const activeView = ref("overview");
const loading = reactive({
  channels: false,
  keys: false,
  logs: false,
  summary: false,
});
const channels = ref([]);
const keys = ref([]);
const logs = ref([]);
const summary = ref({
  totalCalls: 0,
  successCalls: 0,
  failedCalls: 0,
  pendingCalls: 0,
  promptTokens: 0,
  completionTokens: 0,
  totalTokens: 0,
  totalCost: 0,
  averageLatencyMs: 0,
});
const notice = ref(null);
const channelModalOpen = ref(false);
const keyModalOpen = ref(false);
const saving = ref(false);
const plainKeyResult = ref(null);

const channelForm = reactive({
  id: null,
  name: "",
  baseUrl: "",
  apiKey: "",
  model: "",
  weight: 1,
});

const keyForm = reactive({
  name: "",
  quotaAmount: 100,
  expiresAt: "",
});

const playground = reactive({
  plainKey: "",
  model: "mock-model",
  prompt: "Explain what this gateway does in one sentence.",
  response: "",
  error: "",
  running: false,
});

const navItems = [
  { id: "overview", label: "总览", icon: LayoutDashboard },
  { id: "channels", label: "上游渠道", icon: RadioTower },
  { id: "keys", label: "下游 Key", icon: KeyRound },
  { id: "logs", label: "请求日志", icon: ScrollText },
  { id: "playground", label: "调用测试", icon: Zap },
];

const stats = computed(() => {
  const enabledChannels = channels.value.filter(
    (item) => item.status === "ENABLED",
  ).length;
  const activeKeys = keys.value.filter(
    (item) => item.status === "ACTIVE",
  ).length;
  const successRate = summary.value.totalCalls
    ? Math.round(
        (summary.value.successCalls / summary.value.totalCalls) * 100,
      )
    : 0;

  return {
    enabledChannels,
    activeKeys,
    requestCount: summary.value.totalCalls,
    successRate,
    totalTokens: summary.value.totalTokens,
    averageLatencyMs: summary.value.averageLatencyMs,
  };
});

const currentTitle = computed(
  () => navItems.find((item) => item.id === activeView.value)?.label,
);

onMounted(loadAll);

async function loadAll() {
  await Promise.all([
    loadChannels(),
    loadKeys(),
    loadLogs(),
    loadSummary(),
  ]);
}

async function loadChannels() {
  loading.channels = true;
  try {
    channels.value = await listChannels();
  } catch (error) {
    showNotice("error", errorMessage(error));
  } finally {
    loading.channels = false;
  }
}

async function loadKeys() {
  loading.keys = true;
  try {
    keys.value = await listKeys();
  } catch (error) {
    showNotice("error", errorMessage(error));
  } finally {
    loading.keys = false;
  }
}

async function loadLogs() {
  loading.logs = true;
  try {
    logs.value = await listLogs();
  } catch (error) {
    showNotice("error", errorMessage(error));
  } finally {
    loading.logs = false;
  }
}

async function loadSummary() {
  loading.summary = true;
  try {
    summary.value = await getLogSummary();
  } catch (error) {
    showNotice("error", errorMessage(error));
  } finally {
    loading.summary = false;
  }
}

function showNotice(type, text) {
  notice.value = { type, text };
  window.setTimeout(() => {
    if (notice.value?.text === text) {
      notice.value = null;
    }
  }, 5000);
}

function openChannelModal(channel = null) {
  channelForm.id = channel?.id ?? null;
  channelForm.name = channel?.name ?? "";
  channelForm.baseUrl = channel?.baseUrl ?? "http://localhost:8080/mock/v1";
  channelForm.apiKey = "";
  channelForm.model = channel?.model ?? "mock-model";
  channelForm.weight = channel?.weight ?? 1;
  channelModalOpen.value = true;
}

async function saveChannel() {
  if (!channelForm.name.trim() || !channelForm.baseUrl.trim()) {
    showNotice("error", "渠道名称和上游地址不能为空");
    return;
  }

  saving.value = true;
  const payload = {
    name: channelForm.name.trim(),
    baseUrl: channelForm.baseUrl.trim(),
    apiKey: channelForm.apiKey.trim() || null,
    model: channelForm.model.trim(),
    weight: Number(channelForm.weight) || 1,
  };

  try {
    if (channelForm.id) {
      await updateChannel(channelForm.id, payload);
      showNotice("success", "渠道已更新");
    } else {
      await createChannel(payload);
      showNotice("success", "渠道已创建");
    }
    channelModalOpen.value = false;
    await loadChannels();
  } catch (error) {
    showNotice("error", errorMessage(error));
  } finally {
    saving.value = false;
  }
}

async function toggleChannel(channel) {
  const status = channel.status === "ENABLED" ? "DISABLED" : "ENABLED";
  try {
    await updateChannelStatus(channel.id, status);
    await loadChannels();
    showNotice("success", status === "ENABLED" ? "渠道已启用" : "渠道已停用");
  } catch (error) {
    showNotice("error", errorMessage(error));
  }
}

async function removeChannel(channel) {
  if (!window.confirm(`确认删除渠道“${channel.name}”？`)) {
    return;
  }
  try {
    await deleteChannel(channel.id);
    await loadChannels();
    showNotice("success", "渠道已删除");
  } catch (error) {
    showNotice("error", errorMessage(error));
  }
}

function openKeyModal() {
  keyForm.name = "";
  keyForm.quotaAmount = 100;
  keyForm.expiresAt = "";
  plainKeyResult.value = null;
  keyModalOpen.value = true;
}

async function saveKey() {
  if (!keyForm.name.trim()) {
    showNotice("error", "Key 名称不能为空");
    return;
  }

  saving.value = true;
  try {
    plainKeyResult.value = await createKey({
      name: keyForm.name.trim(),
      quotaAmount: Number(keyForm.quotaAmount) || 0,
      expiresAt: keyForm.expiresAt || null,
    });
    await loadKeys();
    showNotice("success", "Key 已创建");
  } catch (error) {
    showNotice("error", errorMessage(error));
  } finally {
    saving.value = false;
  }
}

async function toggleKey(apiKey) {
  const status = apiKey.status === "ACTIVE" ? "DISABLED" : "ACTIVE";
  try {
    await updateKeyStatus(apiKey.id, status);
    await loadKeys();
    showNotice("success", status === "ACTIVE" ? "Key 已启用" : "Key 已停用");
  } catch (error) {
    showNotice("error", errorMessage(error));
  }
}

async function removeKey(apiKey) {
  if (!window.confirm(`确认删除 Key“${apiKey.name}”？`)) {
    return;
  }
  try {
    await deleteKey(apiKey.id);
    await loadKeys();
    showNotice("success", "Key 已删除");
  } catch (error) {
    showNotice("error", errorMessage(error));
  }
}

async function runPlayground() {
  playground.response = "";
  playground.error = "";
  if (!playground.plainKey.trim()) {
    playground.error = "请输入下游 plainKey";
    return;
  }

  playground.running = true;
  try {
    const result = await askGateway(playground.plainKey.trim(), {
      model: playground.model.trim(),
      messages: [{ role: "user", content: playground.prompt.trim() }],
    });
    playground.response = JSON.stringify(result, null, 2);
    await Promise.all([loadLogs(), loadSummary()]);
  } catch (error) {
    playground.error = errorMessage(error);
  } finally {
    playground.running = false;
  }
}

function formatDate(value) {
  if (!value) {
    return "-";
  }
  return new Intl.DateTimeFormat("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}

function formatNumber(value) {
  return new Intl.NumberFormat("zh-CN", {
    maximumFractionDigits: 2,
  }).format(value ?? 0);
}

function statusClass(status) {
  return String(status || "").toLowerCase();
}
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-mark">
          <Server :size="21" aria-hidden="true" />
        </div>
        <div>
          <strong>LLM Gateway</strong>
          <span>管理控制台</span>
        </div>
      </div>

      <nav class="nav-list" aria-label="主导航">
        <button
          v-for="item in navItems"
          :key="item.id"
          class="nav-item"
          :class="{ active: activeView === item.id }"
          type="button"
          :title="item.label"
          :aria-label="item.label"
          @click="activeView = item.id"
        >
          <component :is="item.icon" :size="18" aria-hidden="true" />
          <span>{{ item.label }}</span>
        </button>
      </nav>

      <div class="sidebar-foot">
        <ShieldCheck :size="17" aria-hidden="true" />
        <span>Key 摘要存储</span>
      </div>
    </aside>

    <main class="main">
      <header class="topbar">
        <div>
          <p class="eyebrow">LLM API GATEWAY</p>
          <h1>{{ currentTitle }}</h1>
        </div>
        <button
          class="icon-button"
          type="button"
          title="刷新全部数据"
          aria-label="刷新全部数据"
          @click="loadAll"
        >
          <RefreshCw
            :size="18"
            :class="{
              spinning:
                loading.channels ||
                loading.keys ||
                loading.logs ||
                loading.summary,
            }"
          />
        </button>
      </header>

      <div
        v-if="notice"
        class="notice"
        :class="notice.type"
        role="status"
      >
        <CheckCircle2
          v-if="notice.type === 'success'"
          :size="18"
          aria-hidden="true"
        />
        <AlertCircle v-else :size="18" aria-hidden="true" />
        <span>{{ notice.text }}</span>
        <button
          class="notice-close"
          type="button"
          title="关闭提示"
          aria-label="关闭提示"
          @click="notice = null"
        >
          <X :size="16" />
        </button>
      </div>

      <section v-if="activeView === 'overview'" class="view">
        <div class="stat-grid">
          <article class="stat">
            <RadioTower :size="20" aria-hidden="true" />
            <div>
              <span>已启用渠道</span>
              <strong>{{ stats.enabledChannels }}</strong>
            </div>
          </article>
          <article class="stat">
            <KeyRound :size="20" aria-hidden="true" />
            <div>
              <span>可用 Key</span>
              <strong>{{ stats.activeKeys }}</strong>
            </div>
          </article>
          <article class="stat">
            <ScrollText :size="20" aria-hidden="true" />
            <div>
              <span>总调用次数</span>
              <strong>{{ stats.requestCount }}</strong>
            </div>
          </article>
          <article class="stat">
            <CheckCircle2 :size="20" aria-hidden="true" />
            <div>
              <span>成功率</span>
              <strong>{{ stats.successRate }}%</strong>
            </div>
          </article>
          <article class="stat">
            <Zap :size="20" aria-hidden="true" />
            <div>
              <span>Token 用量</span>
              <strong>{{ formatNumber(stats.totalTokens) }}</strong>
            </div>
          </article>
          <article class="stat">
            <Activity :size="20" aria-hidden="true" />
            <div>
              <span>平均延迟</span>
              <strong>{{ stats.averageLatencyMs }} ms</strong>
            </div>
          </article>
        </div>

        <div class="section-grid">
          <section class="panel">
            <div class="panel-head">
              <div>
                <h2>最近调用</h2>
                <p>按时间倒序展示网关收到的请求。</p>
              </div>
              <button
                class="text-button"
                type="button"
                @click="activeView = 'logs'"
              >
                查看全部
              </button>
            </div>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>状态</th>
                    <th>模型</th>
                    <th>渠道</th>
                    <th>延迟</th>
                    <th>时间</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="item in logs.slice(0, 6)" :key="item.id">
                    <td>
                      <span class="status" :class="statusClass(item.status)">
                        {{ item.status }}
                      </span>
                    </td>
                    <td>{{ item.model || "-" }}</td>
                    <td>#{{ item.channelId }}</td>
                    <td>{{ item.latencyMs ?? "-" }} ms</td>
                    <td>{{ formatDate(item.createdAt) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div v-if="!logs.length" class="empty-state">
              暂无调用记录
            </div>
          </section>

          <section class="panel action-panel">
            <div class="panel-head">
              <div>
                <h2>下一步操作</h2>
                <p>完成一条真实演示链路。</p>
              </div>
            </div>
            <button
              class="action-row"
              type="button"
              @click="openChannelModal()"
            >
              <RadioTower :size="18" />
              <span>
                <strong>配置上游渠道</strong>
                <small>地址、模型和权重</small>
              </span>
              <Plus :size="17" />
            </button>
            <button class="action-row" type="button" @click="openKeyModal">
              <KeyRound :size="18" />
              <span>
                <strong>创建下游 Key</strong>
                <small>生成一次性完整 Key</small>
              </span>
              <Plus :size="17" />
            </button>
            <button
              class="action-row"
              type="button"
              @click="activeView = 'playground'"
            >
              <Play :size="18" />
              <span>
                <strong>发起调用测试</strong>
                <small>验证转发、配额和日志</small>
              </span>
              <Zap :size="17" />
            </button>
          </section>
        </div>
      </section>

      <section v-else-if="activeView === 'channels'" class="view">
        <div class="toolbar">
          <div>
            <h2>上游渠道</h2>
            <p>启用的渠道会参与模型筛选和权重路由。</p>
          </div>
          <button
            class="primary-button"
            type="button"
            @click="openChannelModal()"
          >
            <Plus :size="17" />
            新建渠道
          </button>
        </div>

        <section class="panel">
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>名称</th>
                  <th>模型</th>
                  <th>权重</th>
                  <th>状态</th>
                  <th>上游地址</th>
                  <th class="actions-col">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in channels" :key="item.id">
                  <td>
                    <strong>{{ item.name }}</strong>
                    <small>#{{ item.id }}</small>
                  </td>
                  <td>{{ item.model }}</td>
                  <td>{{ item.weight }}</td>
                  <td>
                    <span class="status" :class="statusClass(item.status)">
                      {{ item.status }}
                    </span>
                  </td>
                  <td class="url-cell">{{ item.baseUrl }}</td>
                  <td>
                    <div class="row-actions">
                      <button
                        class="icon-button"
                        type="button"
                        title="编辑渠道"
                        aria-label="编辑渠道"
                        @click="openChannelModal(item)"
                      >
                        <Pencil :size="16" />
                      </button>
                      <button
                        class="icon-button"
                        type="button"
                        :title="
                          item.status === 'ENABLED' ? '停用渠道' : '启用渠道'
                        "
                        aria-label="切换渠道状态"
                        @click="toggleChannel(item)"
                      >
                        <Power :size="16" />
                      </button>
                      <button
                        class="icon-button danger"
                        type="button"
                        title="删除渠道"
                        aria-label="删除渠道"
                        @click="removeChannel(item)"
                      >
                        <Trash2 :size="16" />
                      </button>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <div v-if="!channels.length" class="empty-state">
            还没有上游渠道
          </div>
        </section>
      </section>

      <section v-else-if="activeView === 'keys'" class="view">
        <div class="toolbar">
          <div>
            <h2>下游 API Key</h2>
            <p>数据库只保存摘要，完整 Key 只在创建时返回一次。</p>
          </div>
          <button class="primary-button" type="button" @click="openKeyModal">
            <Plus :size="17" />
            创建 Key
          </button>
        </div>

        <section class="panel">
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>名称</th>
                  <th>前缀</th>
                  <th>状态</th>
                  <th>已用 / 配额</th>
                  <th>过期时间</th>
                  <th class="actions-col">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in keys" :key="item.id">
                  <td>
                    <strong>{{ item.name }}</strong>
                    <small>#{{ item.id }}</small>
                  </td>
                  <td class="mono">{{ item.keyPrefix }}</td>
                  <td>
                    <span class="status" :class="statusClass(item.status)">
                      {{ item.status }}
                    </span>
                  </td>
                  <td>
                    {{ formatNumber(item.usedAmount) }} /
                    {{ formatNumber(item.quotaAmount) }}
                  </td>
                  <td>{{ item.expiresAt ? formatDate(item.expiresAt) : "不过期" }}</td>
                  <td>
                    <div class="row-actions">
                      <button
                        class="icon-button"
                        type="button"
                        :title="item.status === 'ACTIVE' ? '停用 Key' : '启用 Key'"
                        aria-label="切换 Key 状态"
                        @click="toggleKey(item)"
                      >
                        <Power :size="16" />
                      </button>
                      <button
                        class="icon-button danger"
                        type="button"
                        title="删除 Key"
                        aria-label="删除 Key"
                        @click="removeKey(item)"
                      >
                        <Trash2 :size="16" />
                      </button>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <div v-if="!keys.length" class="empty-state">
            还没有下游 Key
          </div>
        </section>
      </section>

      <section v-else-if="activeView === 'logs'" class="view">
        <div class="toolbar">
          <div>
            <h2>请求日志</h2>
            <p>每个上游尝试都会留下状态、延迟和 token 用量。</p>
          </div>
          <button class="secondary-button" type="button" @click="loadLogs">
            <RefreshCw :size="17" />
            刷新
          </button>
        </div>

        <section class="panel">
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>状态</th>
                  <th>请求 ID</th>
                  <th>Key</th>
                  <th>渠道</th>
                  <th>用量</th>
                  <th>延迟</th>
                  <th>时间</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in logs" :key="item.id">
                  <td>
                    <span class="status" :class="statusClass(item.status)">
                      {{ item.status }}
                    </span>
                  </td>
                  <td class="mono short-id" :title="item.requestId">
                    {{ item.requestId?.slice(0, 8) }}
                  </td>
                  <td>#{{ item.apiKeyId }}</td>
                  <td>#{{ item.channelId }}</td>
                  <td>
                    {{ item.promptTokens ?? 0 }} +
                    {{ item.completionTokens ?? 0 }}
                  </td>
                  <td>{{ item.latencyMs ?? "-" }} ms</td>
                  <td>{{ formatDate(item.createdAt) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <div v-if="!logs.length" class="empty-state">
            暂无请求日志
          </div>
        </section>
      </section>

      <section v-else class="view">
        <div class="toolbar">
          <div>
            <h2>调用测试</h2>
            <p>使用下游 plainKey 穿过网关访问模拟上游。</p>
          </div>
        </div>

        <div class="playground-grid">
          <section class="panel form-panel">
            <label>
              <span>下游 plainKey</span>
              <input
                v-model="playground.plainKey"
                type="password"
                placeholder="sk-..."
                autocomplete="off"
              />
            </label>
            <label>
              <span>模型</span>
              <input v-model="playground.model" type="text" />
            </label>
            <label>
              <span>用户消息</span>
              <textarea v-model="playground.prompt" rows="7"></textarea>
            </label>
            <button
              class="primary-button full-width"
              type="button"
              :disabled="playground.running"
              @click="runPlayground"
            >
              <LoaderCircle
                v-if="playground.running"
                :size="17"
                class="spinning"
              />
              <Play v-else :size="17" />
              {{ playground.running ? "请求中" : "发送请求" }}
            </button>
          </section>

          <section class="panel response-panel">
            <div class="panel-head">
              <div>
                <h2>响应</h2>
                <p>调用结果会原样显示，便于排查。</p>
              </div>
            </div>
            <pre v-if="playground.response">{{ playground.response }}</pre>
            <div v-else-if="playground.error" class="response-error">
              <AlertCircle :size="18" />
              <span>{{ playground.error }}</span>
            </div>
            <div v-else class="empty-state">
              等待发送请求
            </div>
          </section>
        </div>
      </section>
    </main>

    <div v-if="channelModalOpen" class="modal-backdrop" @click.self="channelModalOpen = false">
      <section class="modal" role="dialog" aria-modal="true">
        <div class="modal-head">
          <div>
            <h2>{{ channelForm.id ? "编辑渠道" : "新建渠道" }}</h2>
            <p>模型名会参与请求路由筛选。</p>
          </div>
          <button
            class="icon-button"
            type="button"
            title="关闭"
            aria-label="关闭"
            @click="channelModalOpen = false"
          >
            <X :size="18" />
          </button>
        </div>
        <form class="form-grid" @submit.prevent="saveChannel">
          <label>
            <span>渠道名称</span>
            <input v-model="channelForm.name" type="text" required />
          </label>
          <label>
            <span>模型名称</span>
            <input v-model="channelForm.model" type="text" required />
          </label>
          <label class="span-2">
            <span>上游地址</span>
            <input v-model="channelForm.baseUrl" type="url" required />
          </label>
          <label>
            <span>上游 API Key</span>
            <input
              v-model="channelForm.apiKey"
              type="password"
              :placeholder="channelForm.id ? '留空表示不修改' : '可留空用于本地模拟'"
              autocomplete="off"
            />
          </label>
          <label>
            <span>权重</span>
            <input
              v-model.number="channelForm.weight"
              type="number"
              min="1"
              max="100"
            />
          </label>
          <div class="modal-actions span-2">
            <button
              class="secondary-button"
              type="button"
              @click="channelModalOpen = false"
            >
              取消
            </button>
            <button class="primary-button" type="submit" :disabled="saving">
              <LoaderCircle v-if="saving" :size="17" class="spinning" />
              <CheckCircle2 v-else :size="17" />
              保存
            </button>
          </div>
        </form>
      </section>
    </div>

    <div v-if="keyModalOpen" class="modal-backdrop" @click.self="keyModalOpen = false">
      <section class="modal" role="dialog" aria-modal="true">
        <div class="modal-head">
          <div>
            <h2>创建下游 Key</h2>
            <p>创建后只展示一次完整 Key。</p>
          </div>
          <button
            class="icon-button"
            type="button"
            title="关闭"
            aria-label="关闭"
            @click="keyModalOpen = false"
          >
            <X :size="18" />
          </button>
        </div>

        <form v-if="!plainKeyResult" class="form-grid" @submit.prevent="saveKey">
          <label class="span-2">
            <span>Key 名称</span>
            <input v-model="keyForm.name" type="text" required />
          </label>
          <label>
            <span>调用配额</span>
            <input
              v-model.number="keyForm.quotaAmount"
              type="number"
              min="0"
            />
          </label>
          <label>
            <span>过期时间</span>
            <input v-model="keyForm.expiresAt" type="datetime-local" />
          </label>
          <div class="modal-actions span-2">
            <button
              class="secondary-button"
              type="button"
              @click="keyModalOpen = false"
            >
              取消
            </button>
            <button class="primary-button" type="submit" :disabled="saving">
              <LoaderCircle v-if="saving" :size="17" class="spinning" />
              <KeyRound v-else :size="17" />
              创建
            </button>
          </div>
        </form>

        <div v-else class="key-result">
          <CheckCircle2 :size="26" />
          <h3>Key 创建成功</h3>
          <p>关闭后将无法再次查看完整 Key。</p>
          <code>{{ plainKeyResult.plainKey }}</code>
          <div class="modal-actions">
            <button
              class="primary-button"
              type="button"
              @click="keyModalOpen = false"
            >
              我已保存
            </button>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>
