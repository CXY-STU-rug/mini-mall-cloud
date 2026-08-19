# ============================================================
# 通用 Dockerfile —— 10 个 Java 服务共用这一个
# 用 build 参数 MODULE 指定具体模块，不用写 10 份
# 构建上下文(context)是项目根目录，才拿得到各模块的 target/*.jar
# ============================================================

# 基础镜像：Temurin 提供的 JDK21 运行时(jre 比完整 jdk 小，够跑 Spring Boot)
FROM eclipse-temurin:21-jre

# 容器内工作目录
WORKDIR /app

# 声明构建参数：要打包哪个模块(如 mini-mall-order)
# 注意 ARG 在 FROM 之后要重新声明一次才能在下面用
ARG MODULE

# 把对应模块打好的 jar 拷进镜像，统一改名 app.jar
# 前提：先在项目根跑过 mvn package，各模块 target 下有 jar
COPY ${MODULE}/target/*.jar app.jar

# 时区设成东八区，否则容器内是 UTC，日志/下单时间差 8 小时
ENV TZ=Asia/Shanghai

# JVM 参数留一个口子，compose 里可覆盖(如限制堆内存 -Xmx512m)
ENV JAVA_OPTS=""

# 容器启动就跑这条；用 sh -c 是为了让 ${JAVA_OPTS} 变量能展开
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]
