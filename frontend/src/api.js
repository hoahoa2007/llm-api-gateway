import axios from "axios";

const api = axios.create({
  baseURL: "/api",
  timeout: 12000,
});

export async function listChannels() {
  return (await api.get("/channels")).data;
}

export async function createChannel(payload) {
  return (await api.post("/channels", payload)).data;
}

export async function updateChannel(id, payload) {
  return (await api.put(`/channels/${id}`, payload)).data;
}

export async function updateChannelStatus(id, status) {
  await api.patch(`/channels/${id}/status`, null, {
    params: { status },
  });
}

export async function deleteChannel(id) {
  await api.delete(`/channels/${id}`);
}

export async function listKeys() {
  return (await api.get("/keys")).data;
}

export async function createKey(payload) {
  return (await api.post("/keys", payload)).data;
}

export async function updateKeyStatus(id, status) {
  await api.patch(`/keys/${id}/status`, null, {
    params: { status },
  });
}

export async function deleteKey(id) {
  await api.delete(`/keys/${id}`);
}

export async function listLogs() {
  return (await api.get("/logs")).data;
}

export async function askGateway(plainKey, payload) {
  const response = await axios.post("/v1/chat/completions", payload, {
    timeout: 40000,
    headers: {
      Authorization: `Bearer ${plainKey}`,
    },
  });
  return response.data;
}

export function errorMessage(error) {
  const data = error?.response?.data;
  return (
    data?.message ||
    data?.error ||
    error?.message ||
    "请求失败，请检查后端服务"
  );
}
