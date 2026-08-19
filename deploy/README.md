# mini-mall-cloud 上线部署（单机 Docker Compose 全容器化）

这套文件把项目从「Windows 本机 java -jar 裸跑」变成「Linux 服务器全容器化」。
配套复习笔记见 `散笔记/部署上线/`（Linux基础 / Docker基础 / 本项目上线部署方案）。

## 目录内容

| 文件 | 作用 |
|---|---|
| `Dockerfile` | 通用镜像，10 个服务共用，靠 build 参数 `MODULE` 区分 |
| `docker-compose.prod.yml` | 全套编排：中间件 + 10 个 Java 服务 + Nginx |
| `nginx/nginx.conf` | 反向代理：前端静态 + /api 转发网关 + 支付回调 |
| `.env.example` | 环境变量样板，复制成 `.env` 填真实密钥 |
| `.gitignore` | 挡住 `.env`、证书、前端产物进 git |
| `build-all.sh` | 一键 mvn 打包全部 jar |

## 上线步骤

```bash
# 1. 服务器装好 Docker(见《本项目上线部署方案》3.1)，把整个项目传上去

# 2. 填密钥
cd deploy
cp .env.example .env
vim .env                       # 填 MySQL/支付宝/邮箱/DeepSeek 等真实值

# 3. 打包所有 jar
bash build-all.sh

# 4. 起全套(--build 会用各模块 jar 构建镜像)
docker compose -f docker-compose.prod.yml --env-file .env up -d --build

# 5. 看状态 / 日志
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs -f order

# 6. 前端：把 C 端 dist 拷到 nginx/html/，admin 后台同理(或另开 location)
```

## 设计要点（面试能讲）

- **一个 Dockerfile 打 10 个服务**：build args 传 `MODULE`，上下文设项目根拿 jar。
- **localhost 全换容器名**：不改任何 yml，靠 Spring 松绑定——环境变量（如
  `SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR=nacos:8848`）优先级高于 application.yml，
  容器名 `nacos/mysql/redis/rabbitmq/elasticsearch` 直接覆盖本机的 localhost。
- **启动顺序防自杀**：业务服务 `depends_on` 中间件 `condition: service_healthy`，
  等 Nacos 真正健康才启，避开「Nacos 没起 → 服务连不上重试后退出」的坑。
- **密钥零泄漏**：所有敏感值走 `.env`（不进 git），compose `--env-file` 注入，
  绝不写死进镜像或此仓库。延续项目「永不 git add .、提交前 grep key 自检」的约定。
- **对外只开 80/443**：网关 9080 和所有中间件端口都只在 `mmnet` 内网互通，
  唯一公网入口是 Nginx。

## ⚠️ 上线前要处理的差异（本机 vs 生产）

| 项 | 本机现状 | 生产要改 |
|---|---|---|
| MySQL / Redis(6379) | Windows 原生，没进容器 | 本 compose 已补成容器(mysql/redis)，或换云 RDS |
| Nacos 存储 | 单机 Derby | 生产建议 MySQL 存储 + 集群 |
| AI Embedding | 本机 Ollama(11434) 吃显存 | 云服务器多半没 GPU → 连宿主机 Ollama 或换云端向量 API |
| 支付回调 | natapp 内网穿透 | 换成正式域名 + HTTPS |
| SkyWalking | 可选接入 | 需要就把 sw-oap/sw-ui 加进 compose，服务加 -javaagent |

> 说明：这套编排是**可落地的脚手架**，不是已跑通的生产环境。各模块 yml 若有硬编码的
> 中间件地址（非 `${VAR:默认}` 形式），env 松绑定仍能覆盖标准 Spring 属性；但自定义前缀的
> 配置（如 `alipay.xxx`、`minio.xxx`）要确认 yml 用的是 `${环境变量:默认}` 占位，
> 我已按你项目已有的占位习惯注入（payment/file/mail 那几个）。真跑时按各服务日志逐个校准。
