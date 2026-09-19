# LLM API Gateway

这是一个统一调用和管理多个 LLM API 的网关项目。

## 项目目标

让调用方使用一个统一的 API Key，通过统一接口访问多个上游 LLM。

## 主要功能

- 管理上游渠道
- 管理下游 API Key
- 统一转发 LLM 请求
- 自动选择上游渠道
- 失败时切换渠道
- 限制调用次数和配额
- 记录调用日志和用量

## 技术栈

- Java
- Spring Boot
- MySQL
- MyBatis-Plus
- Vue 3

## 当前进度

- [x] 创建 GitHub 仓库
- [x] 编写 README
- [ ] 创建 Spring Boot 后端
- [ ] 完成渠道管理
- [ ] 完成 API Key 管理
- [ ] 完成统一转发接口
- [ ] 完成管理后台

## 项目状态

开发中。
