智维Wiki 企业级智能安全知识库系统 完整设计方案
一、项目概述
1.1 项目定位与背景
“智维Wiki”是一款面向中大型企业、金融机构、政府单位打造的安全可控的企业级智能知识库平台。针对传统Wiki存在的知识孤岛、检索低效、权限管控粗放、AI能力与数据安全脱节等核心痛点，深度融合大语言模型(LLM)、检索增强生成(RAG)、细粒度权限管控、安全沙箱等技术，实现从文档创建、解析、向量化存储、智能检索到安全交互的全流程闭环，同时支持多租户隔离，为企业提供“智能赋能+安全合规”双保障的知识管理解决方案。
1.2 核心设计目标
1. 安全优先：将权限管控深度融入系统全链路，从接入层、检索层到生成层实现全流程权限校验，杜绝越权数据访问，满足等保2.0、GDPR等合规要求。
2. 智能高效：基于RAG+Agent技术实现精准可追溯的智能问答、深度研究、内容创作，解决传统知识库“找得到、看得懂、用得上”的核心需求。
3. 企业级可用：提供高可用、可扩展、易运维的微服务架构，支持大规模企业级部署，适配复杂的组织架构与权限体系。
4. 可落地演进：采用分阶段实施策略，从MVP到企业级能力渐进式落地，同时预留足够的扩展能力，适配未来AI技术与业务需求的迭代。
1.3 系统核心亮点
● 深度权限融合：独创“接入层认证+检索层前置过滤+返回层二次校验”三层纵深防御架构，实现向量数据的行级权限管控，从根本上杜绝越权访问。
● 增强型RAG引擎：集成多路召回、重排序、查询理解、智能记忆管理等能力，大幅提升问答准确率与上下文连贯性，降低幻觉发生率。
● 企业级文档处理：支持复杂PDF、扫描件、多格式文档的智能解析，提供版面恢复、表格提取、跨页拼接、OCR增强等能力，保障非结构化文档的解析质量。
● 安全代码沙箱：基于Docker实现隔离的代码解释器环境，支持用户通过自然语言完成数据分析、图表生成等操作，同时严格保障服务器与数据安全。
● 全链路可观测：提供完整的操作审计、AI调用日志、异常检测、Badcase闭环优化体系，实现系统全流程可追溯、可优化。
二、系统整体架构设计
2.1 分层架构总览
系统采用前后端分离+微服务架构，整体分为5层，同时拆分Java业务主服务与Python AI专项服务，兼顾业务稳定性与AI能力的灵活性，整体架构如下：
┌─────────────────────────────────────────────────────────────────────┐
│                        前端接入层 (Vue3 SPA)                         │
│  门户界面、管理后台、编辑器、AI交互界面、SSE流式输出、WebSocket通信  │
└───────────────────────────────┬─────────────────────────────────────┘
                                │
┌───────────────────────────────▼─────────────────────────────────────┐
│                        API网关层 (Spring Cloud Gateway)               │
│       路由转发、全局权限拦截、身份认证、流控、熔断、API文档管理      │
└───────────┬─────────────────────────────────────┬───────────────────┘
            │                                     │
┌───────────▼───────────────────┐     ┌─────────▼─────────────────────┐
│      Java业务服务集群         │     │      Python AI服务集群         │
│  核心职责：业务逻辑编排、      │     │  核心职责：AI能力专项处理、    │
│  用户权限管理、文档元数据管理、│     │  文档解析、向量化、RAG检索、  │
│  审批流程、审计日志、运营管理  │     │  LLM调用、Agent编排、沙箱执行  │
└───────────┬───────────────────┘     └─────────┬─────────────────────┘
            │                                     │
┌───────────▼─────────────────────────────────────▼───────────────────┐
│                        共享数据存储层                                  │
│  关系型数据库(MySQL)、向量数据库(Milvus/Qdrant)、对象存储(MinIO)、  │
│  缓存(Redis)、搜索引擎(Elasticsearch)、消息队列(RabbitMQ)            │
└─────────────────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────────────┐
│                        基础设施层                                      │
│  Docker/K8s容器编排、Prometheus+Grafana监控、ELK日志体系、SkyWalking │
│  链路追踪、Vault密钥管理、CI/CD持续集成                              │
└─────────────────────────────────────────────────────────────────────┘
2.2 微服务拆分与职责
2.2.1 Java业务微服务集群
服务名称	核心职责
用户认证与权限服务	用户/部门/角色管理、RBAC权限体系、JWT签发与校验、权限缓存、动态权限变更处理
文档管理服务	文档/知识库空间管理、目录树维护、文档版本管理、收藏/点赞/评论、文档元数据管理
审批流程服务	文档入库权限审批、高密级文档二次审批、审批流程编排、审批通知
审计与运营服务	全链路操作日志、AI调用审计、异常检测、安全告警、Badcase收集分析、数据统计可视化
网关服务	路由转发、全局拦截、流控熔断、黑白名单、协议转换
任务调度服务	基于XXL-JOB的分布式任务调度，负责记忆过期清理、文档状态同步、统计报表生成等
2.2.2 Python AI微服务集群
服务名称	核心职责
文档解析服务	多格式文档解析、版面分析、表格提取、OCR识别、后处理与质量评估
向量与检索服务	文本分块、Embedding向量化、向量库管理、多路召回、重排序、检索调优
RAG与对话服务	智能问答、Prompt工程、上下文构建、答案校验与引用标注、SSE流式输出
记忆管理服务	对话记忆提取、冲突检测、记忆存储与检索、生命周期管理、合规清理
Agent编排服务	多步骤复杂任务处理、工具调用、多Agent协作、工作流编排
代码沙箱服务	代码静态安全检测、Docker隔离容器管理、代码执行、结果捕获与返回
2.3 跨服务交互设计
系统采用同步HTTP+异步消息双模式实现跨服务通信，兼顾实时性与可靠性：
1. 同步HTTP调用：Java主服务 → Python AI服务，用于实时性要求高的场景，如智能问答、检索测试、在线文档预览解析，通过Feign/HTTP客户端实现，携带统一的权限Token与请求TraceID。
2. 异步消息队列：基于RabbitMQ实现，用于耗时任务的异步处理，如批量文档解析、向量化入库、记忆提取、Badcase离线分析，实现服务解耦与削峰填谷。
3. 共享存储交互：通过MinIO实现文档源文件的共享，通过Redis实现权限信息、会话状态、热点数据的共享，通过MySQL实现任务状态、元数据的持久化共享。
2.4 多租户架构设计
系统支持三种粒度的多租户隔离方案，可根据企业安全需求灵活选型：
隔离粒度	实现方式	适用场景	安全等级
元数据过滤隔离	所有租户共享向量Collection/MySQL表，通过tenant_id元数据字段实现过滤	中小企业、集团内非敏感业务部门	低
分区隔离	同一Collection/表下，为每个租户创建独立的Partition/表分区，实现物理隔离	中大型企业、多分支机构	中
集合/库隔离	为每个租户创建独立的向量Collection/MySQL数据库，完全物理隔离	金融、政府等强监管行业、SaaS平台	高
三、核心功能模块详细设计
3.1 前台用户门户模块
面向普通用户、知识创作者，提供完整的知识消费与贡献能力，界面采用左侧导航+右侧内容的经典企业级布局，核心功能如下：
1. 用户中心
  ○ 登录/注册：支持账号密码、企业微信/钉钉/OAuth2.0单点登录，双因素认证。
  ○ 个人中心：个人信息管理、密码修改、我的文档/收藏/浏览历史、权限查看、个人API密钥管理。
  ○ 仪表盘：个性化首页，展示常用知识库、最近浏览文档、待办审批、AI问答历史、贡献统计。
2. 知识浏览与检索
  ○ 目录导航：树状目录结构，支持文件夹/文档层级管理、标签分类筛选、未发布/未学习状态标记。
  ○ 全局智能检索：支持关键词检索与自然语言问答，检索结果按权限匹配度排序，支持按文档类型、部门、时间范围筛选。
  ○ 文档详情页：支持富文本/Markdown渲染、版本历史查看与回滚、评论互动、收藏点赞、权限查看、文档导出。
3. AI交互中心
  ○ 智能问答（RAG）：核心功能，支持多轮对话、流式输出、答案来源引用标注、相关文档推荐、答案点赞/点踩反馈。
  ○ 深度研究（Agent）：支持多步骤复杂任务处理，如跨文档对比分析、行业报告撰写、数据汇总研究，支持断点续传与中间结果查看。
  ○ 安全代码解释器：支持CSV/Excel/PDF等数据文件上传，通过自然语言描述分析需求，AI自动生成代码并在隔离沙箱中执行，返回数据分析结果、可视化图表。
  ○ 内容创作辅助：基于知识库内容，辅助用户完成文档撰写、大纲生成、内容润色、格式优化。
4. 内容贡献模块
  ○ 文档创建编辑：集成专业富文本/Markdown编辑器，支持图片/表格/代码块/附件插入、版本保存、预览发布。
  ○ 文档上传解析：支持PDF/Word/Excel/PPT/TXT等多格式文件批量上传，后端自动解析、分块、向量化入库，支持解析进度查看。
  ○ 批量操作：支持文档批量移动、归档、权限设置、发布/下架。
3.2 后台管理运营模块
面向系统管理员、知识运营人员、安全审计人员，提供全维度的系统管理与运营能力：
1. 系统管理中心
  ○ 菜单与路由管理：动态配置前端菜单、路由、按钮权限，支持界面可视化配置。
  ○ 角色与权限管理：基于RBAC模型，自定义角色（管理员、编辑、只读用户、访客等），分配菜单权限、操作权限、数据权限、安全等级权限。
  ○ 用户与部门管理：用户全生命周期管理、部门层级架构维护、用户-部门-角色关联配置，支持批量导入与同步。
  ○ 系统配置：基础参数配置、模型参数配置、存储配置、安全策略配置、通知模板配置。
2. 知识库管理中心
  ○ 知识库/空间管理：创建独立/共享知识库，设置空间级访问权限、成员管理、安全等级、存储配额。
  ○ 文档全生命周期管理：全平台文档查看、批量操作、归档/恢复、版本管理、违规内容处理。
  ○ 向量化任务管理：文档解析、分块、向量化任务的状态监控、失败重试、任务日志查看。
3. AI与向量库管理
  ○ 模型管理：Embedding模型、LLM模型的配置管理、API密钥管理、参数调优、用量统计。
  ○ 向量库管理：向量集合/分区管理、数据统计、索引优化、生命周期管理、多租户数据隔离配置。
  ○ 检索调优：检索策略测试、混合检索权重配置、重排序模型管理、Badcase检索效果复现与优化。
4. 运营与审计中心
  ○ 全链路审计日志：用户操作日志、AI问答全链路日志、权限校验日志、文档访问日志、系统变更日志，支持多维度筛选与导出。
  ○ Badcase闭环管理：集成用户反馈、客服工单、自动质量检测三路渠道，按检索失败、幻觉生成、路由错误、知识缺失四类自动归类，支持任务分配、优化跟踪、效果验证。
  ○ 数据统计可视化：系统使用情况、文档增长趋势、AI调用统计、用户活跃数据、热门文档、问题解决率等多维度报表，支持自定义看板。
  ○ 安全告警中心：异常行为检测告警、越权访问告警、高频访问告警、系统故障告警，支持多渠道通知与告警策略配置。
3.3 AI智能能力中心
系统核心能力模块，为前台与后台提供完整的AI能力支撑：
1. 增强型文档解析引擎
  ○ 多格式兼容：原生PDF、扫描PDF、Word、Excel、PPT、HTML、TXT等全格式支持，自动识别文档类型并路由到对应解析器。
  ○ 智能版面分析：多栏布局恢复、页眉页脚自动过滤、目录页识别、跨页内容自动拼接、图文混排内容还原。
  ○ 表格专项处理：合并单元格/斜线表头识别、表格结构还原、表格转Markdown、大表格智能分块存储。
  ○ OCR增强：集成PaddleOCR，支持中文/仿宋体/竖排文字识别，OCR后处理去噪、断行修复、字符纠错，支持多模态LLM对图片内容进行语义描述。
  ○ 质量分层保障：基础解析→增强解析→人工复核三级处理机制，低质量文档自动触发人工复核，保障解析准确率。
2. 智能检索与RAG引擎
  ○ 多路并行召回：向量语义召回、BM25关键词召回、混合召回，并行执行结果融合，兼顾语义匹配与关键词精准度。
  ○ 查询理解优化：查询扩展、同义词替换、意图识别、问题分类，提升复杂问题的检索准确率。
  ○ 分块策略优化：支持语义分块、重叠分块、动态分块大小、父文档检索、上下文增强，解决上下文碎片化问题。
  ○ 重排序机制：基于Cross-Encoder模型（BGE-Reranker）对召回结果进行二次精排，动态阈值过滤低相关性内容，提升上下文质量。
  ○ 答案校验：支持答案忠实度、相关性自动检测，来源引用自动标注，降低幻觉风险。
3. 智能记忆管理系统
  ○ 记忆分类管理：语义记忆（通用共享知识）、情节记忆（用户专属对话历史）、程序性记忆（操作流程固化），分类存储与管理。
  ○ 记忆生命周期管理：对话结束后自动提取关键信息，支持ADD/UPDATE/DELETE/NOOP四种操作，自动语义去重、TTL过期管理、矛盾冲突检测，支持软删除与合规清理，满足GDPR被遗忘权。
  ○ 双层记忆架构：短期记忆（对话上下文）+长期记忆（向量库存储），每次对话自动检索相关记忆，格式化后注入Prompt，实现个性化、连贯的对话体验。
4. Agent与工作流编排
  ○ 多Agent协作：支持规划、检索、编码、审计等多角色Agent协同工作，处理复杂的跨文档、多步骤任务。
  ○ 自定义工具调用：支持知识库检索、代码执行、API调用、文件处理等工具，可扩展自定义工具。
  ○ 工作流可视化：支持可视化编排AI工作流，适配企业标准化的知识处理、内容审核、报告生成等场景。
5. 安全代码解释器
  ○ 静态安全检测：基于AST语法树分析，禁止危险模块、系统调用、网络访问等高危代码模式。
  ○ 隔离沙箱执行：基于Docker创建临时隔离容器，禁用网络、只读根目录、CPU/内存资源严格限制，执行完成后立即销毁容器。
  ○ 多场景支持：支持数据分析、图表生成、文件格式转换、数学计算、代码调试等场景，支持Python语言与常用数据科学库。
3.4 安全管控与审计模块
系统的安全核心模块，实现全链路的权限管控与合规审计：
1. 权限管控体系
  ○ 四维权限矩阵：部门、角色、安全等级、用户白名单，实现从菜单、按钮到数据行级的全链路权限控制。
  ○ 四层安全等级：公开→内部→敏感→机密，分级管控，不同等级对应不同的审批流程与访问限制。
  ○ 动态权限变更：权限变更实时生效，自动失效缓存与Token，支持用户离职、角色变更、部门调整的全流程自动化处理。
  ○ 行级安全RLS：基于PostgreSQL/MySQL实现关系型数据的行级安全隔离，确保用户只能访问有权限的数据。
2. 向量数据库权限融合
  ○ 前置过滤策略：检索前先基于用户权限构建过滤条件，仅召回用户有权访问的知识片段，从根本上杜绝越权数据访问。
  ○ 混合隔离方案：支持分区物理隔离+元数据逻辑过滤的双重隔离策略，兼顾性能与安全性。
  ○ 二次校验机制：检索结果返回前，进行二次权限校验，过滤无权限内容，实现双重保险。
3. 全链路审计与异常检测
  ○ 全链路日志审计：记录从用户登录、检索、AI调用、文档访问到系统变更的全流程日志，不可篡改，支持合规审计与问题追溯。
  ○ 实时异常检测：高频查询检测、高密级文档批量访问检测、非工作时间异常访问检测、跨部门越权访问检测，发现异常实时告警。
  ○ 安全事件响应：支持异常事件的分级处置、溯源分析、封禁处理，形成完整的安全事件闭环。
4. 合规性设计
  ○ 数据加密：传输加密（HTTPS）、存储加密、敏感数据脱敏，保障数据全生命周期安全。
  ○ 密钥管理：基于Vault实现API密钥、数据库密码的统一管理与轮换，杜绝硬编码风险。
  ○ 合规清理：支持用户数据的批量导出、软删除、永久销毁，满足GDPR、个人信息保护法等合规要求。
  ○ 等保适配：满足网络安全等级保护2.0三级要求，提供完整的安全防护、审计、备份恢复能力。
四、核心业务流程与实现
4.1 带权限管控的增强RAG问答全流程
这是系统的核心流程，实现权限管控与RAG能力的深度融合，完整步骤如下：
public AnswerResponse enhancedSecureRAG(String question, String sessionId, HttpServletRequest request) {
    // 1. 接入层：身份认证与权限解析
    UserPermission user = permissionService.resolveUserPermission(request);
    sessionService.validateSession(sessionId, user.getUserId());
    if (!user.isActive()) {
        throw new UserInactiveException("用户账户已停用");
    }

    // 2. 检索层：权限前置过滤的多路召回
    // 2.1 构建向量检索权限过滤条件
    VectorSearchFilter filter = permissionService.buildVectorFilter(user);
    // 2.2 查询理解与扩展
    QueryUnderstandingResult understoodQuery = queryUnderstandingService.understand(question);
    // 2.3 多路并行召回（仅在授权范围内）
    List<Chunk> recalledChunks = enhancedRetriever.multiRecall(understoodQuery.getExpandedQuery(), filter);
    // 2.4 Cross-Encoder重排序
    List<Chunk> rerankedChunks = enhancedRetriever.rerank(recalledChunks, question);
    if (rerankedChunks.isEmpty()) {
        auditService.logNoResults(question, user);
        return AnswerResponse.noAuthorizedResults();
    }

    // 3. 记忆层：个性化记忆注入
    List<Memory> memories = memoryService.retrieveMemories(question, user.getUserId(), 5);
    String memoryContext = formatMemories(memories);
    String shortTermMemory = getSessionShortTermMemory(sessionId);

    // 4. 生成层：安全约束下的LLM生成
    // 4.1 构建带权限约束的安全Prompt
    String systemPrompt = buildSecurePrompt(user, rerankedChunks, memoryContext, shortTermMemory);
    // 4.2 调用LLM生成答案（带流式输出）
    LLMResponse llmResponse = llmService.generateWithConstraints(question, systemPrompt, buildSecurityRules(user));
    // 4.3 答案忠实度校验与来源引用标注
    Answer answer = securityValidator.validateAndAddCitations(llmResponse, rerankedChunks, user);

    // 5. 返回层：二次权限校验与结果过滤
    List<Chunk> finalFilteredChunks = permissionService.postFilterResults(answer.getSourceChunks(), user);
    answer.filterUnauthorizedContent(finalFilteredChunks);

    // 6. 审计层：全链路日志记录与异常检测
    RagAuditLog auditLog = buildAuditLog(question, answer, user, recalledChunks);
    auditService.logRagQuery(auditLog);
    anomalyDetector.detectInRealTime(auditLog);

    // 7. 收尾：更新对话记忆
    updateSessionShortTermMemory(sessionId, question, answer.getContent());
    asyncExtractAndStoreLongTermMemory(sessionId, user.getUserId());

    return AnswerResponse.success(answer);
}
4.2 企业级文档解析与向量化入库流程
实现复杂文档的高质量解析与权限绑定的向量化存储，完整流程如下：
def document_processing_pipeline(file, space_id, permission, submitter_id):
    # 1. 前置校验：提交者权限校验
    submitter_permission = permission_service.get_user_permissions(submitter_id)
    if not submitter_permission.can_upload_document(permission.security_level):
        raise PermissionDeniedException("无权限上传该安全等级的文档")

    # 2. 文件类型检测与解析器路由
    file_type = detect_file_type(file)
    parsed_doc = None
    match file_type.category:
        case "NATIVE_PDF":
            parsed_doc = minerU_parser.parse(file)
        case "SCANNED_PDF":
            parsed_doc = ocr_enhanced_parser.parse(file)
        case "WORD":
            parsed_doc = word_parser.parse(file)
        case "EXCEL":
            parsed_doc = excel_parser.parse(file)
        case _:
            parsed_doc = general_tika_parser.parse(file)

    # 3. 专项内容处理
    # 3.1 表格专项提取与处理
    tables = table_extractor.extract_tables(parsed_doc)
    for table in tables:
        process_table_and_store(table, space_id, permission)
    # 3.2 图片OCR与语义描述
    images = image_extractor.extract_images(parsed_doc)
    for image in images:
        process_image_and_add_description(image, parsed_doc)

    # 4. 后处理：版面优化、跨页拼接、噪声过滤
    parsed_doc = post_processor.process(parsed_doc)

    # 5. 质量评估与分层处理
    quality_score = quality_evaluator.evaluate(parsed_doc)
    if quality_score < LOW_QUALITY_THRESHOLD:
        # 低质量文档进入人工复核队列
        add_to_review_queue(parsed_doc, file.name, submitter_id)
        return ProcessingResult.need_review(quality_score)
    elif quality_score < MEDIUM_QUALITY_THRESHOLD:
        # 中等质量文档触发增强解析
        parsed_doc = enhanced_parser.reparse(file)
        quality_score = quality_evaluator.evaluate(parsed_doc)

    # 6. 语义分块与权限绑定
    chunks = semantic_chunking_strategy.chunk(parsed_doc)
    for chunk in chunks:
        chunk.permission = permission
        chunk.space_id = space_id
        chunk.document_id = parsed_doc.document_id

    # 7. 向量化与入库
    embedding_service.embed_and_store_chunks(chunks, isolation_strategy=get_isolation_strategy(permission))

    # 8. 审计与结果返回
    audit_service.log_document_indexed(parsed_doc, submitter_id, chunks, quality_score)
    return ProcessingResult.success(parsed_doc, quality_score, len(chunks))
4.3 安全代码沙箱执行流程
实现用户自然语言数据分析需求的安全执行，完整步骤如下：
def secure_code_executor(user_request, data_files, user_id):
    # 1. 需求理解与代码生成
    code_generation_prompt = build_code_prompt(user_request, data_files)
    generated_code = llm_service.generate_code(code_generation_prompt)

    # 2. 静态安全检测
    is_safe, risk_info = code_security_checker.ast_analyze(generated_code)
    if not is_safe:
        raise SecurityException(f"代码存在安全风险：{risk_info}")

    # 3. 准备隔离执行环境
    # 3.1 从预热池获取空闲容器，避免冷启动延迟
    container = sandbox_pool.get_idle_container()
    # 3.2 挂载用户数据文件与生成的代码
    sandbox_mount(container, data_files, generated_code)

    # 4. 隔离环境中执行代码
    try:
        exec_result = container.exec_run(
            cmd=f"python /sandbox/user_code.py",
            timeout=30,
            mem_limit="512m",
            cpu_period=100000,
            cpu_quota=50000
        )
    except TimeoutException:
        container.kill()
        raise TimeoutException("代码执行超时")
    finally:
        # 5. 执行完成后销毁容器，释放资源
        sandbox_pool.recycle_and_destroy_container(container)

    # 6. 执行结果处理与安全校验
    result = process_exec_result(exec_result)
    result_safe, filtered_result = result_security_checker.check(result)
    if not result_safe:
        raise SecurityException("执行结果包含敏感信息，已拦截")

    # 7. 审计日志记录
    audit_service.log_code_execution(user_id, user_request, generated_code, exec_result)

    return filtered_result
4.4 文档权限审批与动态变更流程
实现高密级文档的入库审批与权限动态变更，完整流程如下：
1. 文档提交审批：用户上传文档，设置文档安全等级、访问权限，提交入库申请。
2. 分级审批路由：
  ○ 公开文档：系统自动审批通过，直接进入解析入库流程。
  ○ 内部文档：路由至提交者所属部门负责人审批。
  ○ 敏感文档：部门负责人+合规部门双人审批。
  ○ 机密文档：多级管理层审批，需二次身份校验。
3. 审批通过处理：审批通过后，自动触发文档解析与向量化入库流程，为每个Chunk绑定审批通过的权限元数据。
4. 权限变更事件监听：当文档权限、用户角色/部门发生变更时，自动触发：
  ○ 实时失效相关用户权限缓存与JWT Token。
  ○ 批量更新向量库中对应文档的权限元数据。
  ○ 记录完整的权限变更审计日志，包含变更前后内容、操作人、变更原因。
  ○ 触发相关用户的权限变更通知。
5. 用户离职特殊处理：用户离职时，立即失效所有Token与权限缓存，软删除用户个人记忆，回收文档权限，记录完整的离职审计日志。
五、全栈技术栈选型
5.1 前端技术栈
技术组件	选型与版本	选型说明
核心框架	Vue 3.4+ + TypeScript 5.0+	组合式API，强类型支持，提升开发效率与代码健壮性
构建工具	Vite 5.0+	极速热更新，构建性能优异，适配现代前端工程化
UI组件库	Naive UI / Element Plus	企业级UI组件库，主题可定制，适配中后台系统场景
状态管理	Pinia 2.1+	Vue3官方推荐状态管理，轻量化、类型友好、支持组合式API
路由管理	Vue Router 4.2+	支持动态路由、权限路由控制，适配后台菜单动态配置
HTTP客户端	Axios 1.6+	支持请求/响应拦截、统一错误处理、Token自动携带
富文本编辑器	Tiptap / Vditor	支持Markdown、富文本双模式，适配知识库文档编辑场景
流式通信	SSE / Socket.io	支持AI问答流式输出、实时通知、消息推送
图表可视化	ECharts 5.4+ / AntV	适配统计报表、数据可视化场景，性能优异、可定制性强
5.2 Java后端业务技术栈
技术组件	选型与版本	选型说明
核心框架	Spring Boot 3.2+ + JDK 17+	企业级Java开发标准，生态完善、稳定性高
微服务体系	Spring Cloud Alibaba	适配国内企业环境，Nacos服务注册发现、Sentinel流控熔断、OpenFeign服务调用
API网关	Spring Cloud Gateway 4.1+	响应式网关，性能优异，支持全局拦截、路由转发、流控熔断
ORM框架	MyBatis-Plus 3.5+	增强型MyBatis，简化CRUD开发，支持分页、多租户、逻辑删除
权限认证	Sa-Token 1.37+	轻量级权限认证框架，支持JWT、多端登录、权限校验、会话管理
消息队列	RabbitMQ 3.12+	成熟可靠的消息队列，支持异步任务、解耦、削峰填谷，适配文档解析等耗时场景
任务调度	XXL-JOB 2.4+	分布式任务调度平台，支持分片任务、失败重试、日志监控，适配定时任务场景
数据库连接池	Druid 1.2+	阿里开源数据库连接池，支持监控、防SQL注入，稳定性高
接口文档	SpringDoc + OpenAPI 3.0	适配Spring Boot 3，自动生成API接口文档，支持在线调试
5.3 Python AI服务技术栈
技术组件	选型与版本	选型说明
Web框架	FastAPI 0.104+	高性能异步Python Web框架，自动生成OpenAPI文档，适配AI服务场景
LLM应用框架	LangChain 0.1+	成熟的LLM应用开发框架，适配RAG、Agent、工具调用等场景
文档解析	MinerU、Apache Tika、PyMuPDF	适配复杂PDF解析、全格式文档通用解析，保障解析准确率
OCR识别	PaddleOCR 2.7+	开源中文OCR引擎，识别准确率高，适配扫描件、图片内容识别
表格提取	Tabula、Camelot	专业PDF表格提取工具，适配复杂表格结构还原
向量数据库SDK	PyMilvus / Qdrant Client	适配向量数据库的操作，支持权限过滤、批量操作
代码沙箱	Docker SDK for Python 6.1+	实现Docker容器的创建、管理、销毁，支撑安全代码执行
异步任务	Celery 5.3+	分布式异步任务框架，适配批量文档解析、向量化等耗时任务
数据科学	Pandas、NumPy、Matplotlib	适配数据分析、图表生成场景，支撑代码解释器能力
5.4 数据存储层选型
存储组件	选型与版本	选型说明
关系型数据库	MySQL 8.0+ / PostgreSQL 14+	存储业务数据、用户权限、元数据、审计日志等结构化数据，PostgreSQL支持更完善的RLS行级安全
向量数据库	Milvus 2.4+ / Qdrant 1.7+	企业级向量数据库，支持高性能向量检索、丰富的元数据过滤、多租户隔离，Milvus适合大规模部署，Qdrant轻量易部署
搜索引擎	Elasticsearch 8.x	实现关键词BM25检索，与向量检索实现混合召回，同时支撑日志存储与分析
对象存储	MinIO	开源对象存储，兼容S3协议，存储文档源文件、图片、附件等非结构化数据
缓存数据库	Redis 7.0+	存储权限缓存、会话状态、热点数据、限流配置，支持集群部署，性能优异
5.5 基础设施与运维组件
组件类型	选型与版本	选型说明
容器化	Docker 24.0+	实现服务的容器化打包、一致化部署
容器编排	Kubernetes 1.28+ / Docker Compose	生产环境使用K8s实现集群部署、弹性伸缩、高可用；开发/测试环境使用Docker Compose快速部署
监控告警	Prometheus + Grafana	实现系统指标、业务指标、AI调用指标的全维度监控与可视化告警
日志体系	ELK Stack (Elasticsearch+Logstash+Kibana)	实现全服务日志的收集、存储、检索、分析
链路追踪	SkyWalking 9.0+	实现分布式服务的全链路追踪、性能分析、故障定位
密钥管理	HashiCorp Vault	实现API密钥、数据库密码、证书的统一管理、加密存储、自动轮换
CI/CD	GitLab CI / GitHub Actions + ArgoCD	实现代码的持续集成、自动构建、持续部署
服务网格	Istio 1.20+（可选）	实现服务间的流量管理、安全通信、可观测性，适配大规模微服务集群
六、数据模型设计
6.1 关系型数据库核心表结构
6.1.1 用户与权限相关表
-- 用户表
CREATE TABLE sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id VARCHAR(64) UNIQUE NOT NULL COMMENT '用户唯一标识',
    username VARCHAR(64) NOT NULL COMMENT '用户名',
    password VARCHAR(255) NOT NULL COMMENT '加密密码',
    real_name VARCHAR(64) COMMENT '真实姓名',
    email VARCHAR(128) COMMENT '邮箱',
    phone VARCHAR(32) COMMENT '手机号',
    dept_id BIGINT COMMENT '所属部门ID',
    status TINYINT DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
    security_level TINYINT DEFAULT 1 COMMENT '安全等级：1-公开 2-内部 3-敏感 4-机密',
    is_deleted TINYINT DEFAULT 0 COMMENT '是否删除',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_dept_id (dept_id),
    INDEX idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- 部门表
CREATE TABLE sys_dept (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    dept_id VARCHAR(64) UNIQUE NOT NULL,
    dept_name VARCHAR(128) NOT NULL,
    parent_id BIGINT DEFAULT 0 COMMENT '父部门ID',
    sort INT DEFAULT 0 COMMENT '排序',
    status TINYINT DEFAULT 1,
    is_deleted TINYINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门表';

-- 角色表
CREATE TABLE sys_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_id VARCHAR(64) UNIQUE NOT NULL,
    role_name VARCHAR(64) NOT NULL,
    role_code VARCHAR(64) NOT NULL COMMENT '角色标识',
    role_level INT DEFAULT 0 COMMENT '角色等级',
    description VARCHAR(255),
    status TINYINT DEFAULT 1,
    is_deleted TINYINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- 用户角色关联表
CREATE TABLE sys_user_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id VARCHAR(64) NOT NULL,
    role_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_role (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

-- 文档权限表
CREATE TABLE document_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id VARCHAR(64) NOT NULL COMMENT '文档ID',
    space_id VARCHAR(64) NOT NULL COMMENT '知识库空间ID',
    security_level TINYINT DEFAULT 1 COMMENT '安全等级',
    owning_dept_id VARCHAR(64) COMMENT '所属部门ID',
    allowed_dept_ids JSON COMMENT '允许访问的部门列表',
    allowed_role_ids JSON COMMENT '允许访问的角色列表',
    min_role_level INT DEFAULT 0 COMMENT '最低角色等级',
    allowed_user_ids JSON COMMENT '用户白名单',
    creator_id VARCHAR(64) NOT NULL COMMENT '创建者ID',
    valid_from DATETIME COMMENT '生效时间',
    valid_to DATETIME COMMENT '失效时间',
    requires_approval TINYINT DEFAULT 0 COMMENT '是否需要审批',
    is_deleted TINYINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_document_id (document_id),
    INDEX idx_space_id (space_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档权限表';
6.1.2 文档与知识库相关表
-- 知识库空间表
CREATE TABLE knowledge_space (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    space_id VARCHAR(64) UNIQUE NOT NULL,
    space_name VARCHAR(128) NOT NULL,
    description VARCHAR(255),
    space_type TINYINT DEFAULT 1 COMMENT '1-私有 2-部门 3-公共',
    owner_id VARCHAR(64) NOT NULL COMMENT '所属者ID',
    dept_id VARCHAR(64) COMMENT '所属部门ID',
    storage_quota BIGINT DEFAULT 0 COMMENT '存储配额，0为无限制',
    status TINYINT DEFAULT 1,
    is_deleted TINYINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库空间表';

-- 文档主表
CREATE TABLE document_info (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id VARCHAR(64) UNIQUE NOT NULL,
    space_id VARCHAR(64) NOT NULL,
    parent_id VARCHAR(64) DEFAULT '0' COMMENT '父文件夹ID',
    document_name VARCHAR(255) NOT NULL,
    document_type VARCHAR(32) COMMENT '文档类型',
    file_path VARCHAR(512) COMMENT '源文件存储路径',
    file_size BIGINT DEFAULT 0 COMMENT '文件大小，单位字节',
    version VARCHAR(32) DEFAULT '1.0.0' COMMENT '当前版本',
    creator_id VARCHAR(64) NOT NULL,
    last_editor_id VARCHAR(64),
    status TINYINT DEFAULT 0 COMMENT '0-草稿 1-已发布 2-已归档',
    is_folder TINYINT DEFAULT 0 COMMENT '0-文档 1-文件夹',
    is_deleted TINYINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_space_parent (space_id, parent_id),
    INDEX idx_creator_id (creator_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档主表';

-- 文档版本表
CREATE TABLE document_version (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    version_id VARCHAR(64) UNIQUE NOT NULL,
    document_id VARCHAR(64) NOT NULL,
    version VARCHAR(32) NOT NULL,
    content LONGTEXT COMMENT '文档内容',
    change_log VARCHAR(512) COMMENT '变更说明',
    editor_id VARCHAR(64) NOT NULL,
    is_deleted TINYINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_document_id (document_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档版本表';

-- 文档解析任务表
CREATE TABLE document_parse_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id VARCHAR(64) UNIQUE NOT NULL,
    document_id VARCHAR(64) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(32) NOT NULL,
    space_id VARCHAR(64) NOT NULL,
    status ENUM('PENDING', 'PARSING', 'VECTORIZING', 'COMPLETED', 'FAILED', 'NEED_REVIEW') NOT NULL,
    parser_type VARCHAR(32),
    quality_score FLOAT DEFAULT 0.0,
    chunk_count INT DEFAULT 0,
    table_count INT DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status_space (status, space_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档解析任务表';
6.1.3 AI与审计相关表
-- 用户记忆表
CREATE TABLE user_memory (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id VARCHAR(64) NOT NULL,
    memory_id VARCHAR(64) UNIQUE NOT NULL,
    content TEXT NOT NULL,
    memory_type ENUM('SEMANTIC', 'EPISODIC', 'PROCEDURAL') NOT NULL,
    embedding VECTOR(1536) NOT NULL COMMENT '向量字段，适配PostgreSQL pgvector',
    metadata JSON,
    ttl BIGINT DEFAULT -1 COMMENT '过期时间戳，-1为永久',
    is_deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_memory (user_id, memory_type, is_deleted),
    INDEX idx_ttl (ttl) WHERE ttl > 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户记忆表';

-- RAG Badcase表
CREATE TABLE rag_badcase (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    case_id VARCHAR(64) UNIQUE NOT NULL,
    query TEXT NOT NULL,
    answer TEXT,
    expected_answer TEXT,
    badcase_type ENUM('RETRIEVAL_FAILURE', 'HALLUCINATION', 'ROUTING_ERROR', 'KNOWLEDGE_GAP') NOT NULL,
    source ENUM('USER_FEEDBACK', 'CUSTOMER_TICKET', 'AUTO_DETECTION') NOT NULL,
    user_id VARCHAR(64),
    session_id VARCHAR(64),
    confidence_score FLOAT,
    status ENUM('NEW', 'IN_REVIEW', 'FIXED', 'WONT_FIX') DEFAULT 'NEW',
    assigned_to VARCHAR(64),
    fix_strategy VARCHAR(255),
    resolved_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_type_status (badcase_type, status),
    INDEX idx_source_created (source, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG Badcase表';

-- RAG审计日志表
CREATE TABLE rag_audit_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    audit_id VARCHAR(64) UNIQUE NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    dept_id VARCHAR(64),
    user_role VARCHAR(64),
    security_level TINYINT,
    query TEXT NOT NULL,
    retrieved_doc_ids JSON,
    retrieved_security_levels JSON,
    retrieved_dept_ids JSON,
    session_id VARCHAR(64),
    client_ip VARCHAR(64),
    user_agent VARCHAR(512),
    response_time BIGINT COMMENT '响应耗时，单位毫秒',
    is_success TINYINT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG审计日志表';
6.2 向量数据库Schema设计
以Milvus为例，核心Collection Schema设计如下，核心特点是将权限元数据与向量数据绑定，支持前置过滤检索：
字段名	字段类型	字段说明	索引类型
chunk_id	VARCHAR(64)	分片唯一ID	主键
document_id	VARCHAR(64)	所属文档ID	标量索引
space_id	VARCHAR(64)	所属知识库空间ID	标量索引
content	TEXT	分片文本内容	-
embedding	FLOAT_VECTOR(1536)	文本向量，维度可根据Embedding模型调整	HNSW/IVF_FLAT 向量索引
tenant_id	VARCHAR(64)	租户ID，多租户隔离	标量索引
owning_dept_id	VARCHAR(64)	所属部门ID	标量索引
allowed_dept_ids	ARRAY(VARCHAR)	允许访问的部门列表	标量索引
security_level	INT	安全等级	标量索引
min_role_level	INT	最低角色等级	标量索引
allowed_role_ids	ARRAY(VARCHAR)	允许访问的角色列表	标量索引
allowed_user_ids	ARRAY(VARCHAR)	允许访问的用户白名单	标量索引
created_at	BIGINT	创建时间戳	标量索引
6.3 缓存与消息数据结构设计
1. Redis权限缓存
  ○ Key: user:permissions:{userId}
  ○ Value: 用户权限对象JSON序列化数据
  ○ 过期时间：5分钟，权限变更时主动失效
2. Redis会话缓存
  ○ Key: user:session:{sessionId}
  ○ Value: 会话上下文、短期对话记忆
  ○ 过期时间：24小时
3. RabbitMQ消息队列设计
  ○ 交换机：wiki.document.exchange
  ○ 队列：wiki.document.parse.queue（文档解析任务）、wiki.document.vector.queue（向量化任务）
  ○ 死信队列：wiki.document.dlq（失败任务处理）
  ○ 交换机：wiki.ai.exchange
  ○ 队列：wiki.ai.memory.queue（记忆提取任务）、wiki.ai.badcase.queue（Badcase分析任务）
七、系统监控与优化闭环
7.1 核心监控指标体系
系统建立三大类监控指标，实现全维度可观测：
7.1.1 检索质量指标（核心业务指标）
● 召回率@K、精确率@K：衡量检索结果的相关性
● MRR（平均倒数排名）、NDCG（归一化折损累计增益）：衡量检索排序质量
● 首条结果命中率：用户需求命中首条结果的比例
● 答案忠实度：AI答案与检索上下文的一致性比例
● Badcase发生率：用户点踩/反馈的问题占比
7.1.2 系统性能指标
● 端到端问答延迟：P50、P95、P99分位延迟
● 向量检索QPS、平均检索耗时
● 文档解析成功率、平均解析耗时
● 缓存命中率、API请求成功率
● 服务CPU/内存使用率、数据库连接池使用率
7.1.3 业务运营指标
● 用户活跃数、文档增长数、知识库数量
● AI问答次数、问题解决率、平均对话轮次
● 用户满意度（点赞/点踩比例）
● 文档访问量、热门文档排行
● 审批流程平均处理时长、通过率
7.2 Badcase自动化优化闭环
系统建立完整的Badcase闭环优化体系，实现AI能力的持续迭代优化：
1. Badcase多渠道收集
  ○ 用户反馈：答案点赞/点踩、问题反馈、留言评论
  ○ 客服工单：用户通过客服提交的问题与需求
  ○ 自动检测：基于大模型自动检测答案的幻觉、不相关、无来源等问题
2. 自动分类与归因
  ○ 按四大类自动分类：检索失败、幻觉生成、路由错误、知识缺失
  ○ 自动归因：定位问题根因，如分块不合理、检索策略问题、模型参数问题、知识库内容缺失
3. 优化策略执行
  ○ 检索优化：调整分块策略、混合检索权重、重排序阈值
  ○ 内容优化：补充缺失的知识库内容、优化文档分块、修正错误内容
  ○ 模型优化：调整Prompt模板、优化模型参数、切换适配的模型
  ○ 流程优化：优化文档解析流程、权限过滤策略
4. A/B测试与效果验证
  ○ 针对优化策略，进行小流量A/B测试
  ○ 对比优化前后的核心指标，验证优化效果
  ○ 效果达标后全量发布，不达标则重新调整优化策略
5. 闭环归档
  ○ 优化完成后，记录完整的优化过程、策略、效果数据
  ○ 归档Badcase案例，形成优化知识库，支撑后续问题快速解决
7.3 性能优化策略
1. 检索性能优化
  ○ 向量索引优化：根据数据规模选择合适的索引类型（HNSW/IVF_FLAT），调整索引参数，平衡检索速度与准确率
  ○ 混合检索优化：并行执行向量检索与关键词检索，降低整体耗时
  ○ 权限过滤优化：优先使用分区物理隔离，减少元数据过滤的性能开销，建立合理的标量索引
  ○ 热点数据缓存：高频访问的文档分片、Embedding结果缓存到Redis，降低重复计算与检索开销
2. AI服务性能优化
  ○ 容器预热池：为代码沙箱建立容器预热池，避免冷启动延迟，提升代码执行响应速度
  ○ 模型批量处理：文档解析、向量化任务采用批量处理，提升吞吐量
  ○ 异步化处理：非实时任务全部异步化，通过消息队列削峰填谷，提升系统稳定性
  ○ Embedding模型优化：本地部署轻量级高性能Embedding模型，降低API调用延迟与成本
3. 系统架构优化
  ○ 服务水平扩展：无状态服务支持K8s水平扩缩容，应对流量波动
  ○ 数据库优化：慢SQL优化、合理建立索引、分库分表，应对大规模数据增长
  ○ 多级缓存架构：本地缓存+Caffeine+Redis多级缓存，降低数据库与远程调用开销
  ○ 读写分离：数据库主从分离，读请求走从库，写请求走主库，提升数据库并发能力
八、部署与运维方案
8.1 环境规划
系统规划4套环境，适配开发、测试、预发、生产全流程：
环境名称	用途	部署规模	数据隔离
开发环境（DEV）	开发人员日常开发、联调	单机Docker Compose部署	开发测试库，与生产完全隔离
测试环境（TEST）	功能测试、集成测试、自动化测试	小规模集群，3节点	独立测试库，定期从生产同步脱敏数据
预发环境（UAT）	上线前验证、压力测试、用户验收测试	与生产环境同配置，小规模集群	独立预发库，使用生产脱敏数据
生产环境（PROD）	正式业务运行	高可用集群，多节点冗余	生产独立库，多副本备份
8.2 容器化部署方案
8.2.1 开发/测试环境 Docker Compose 部署
提供一键部署的docker-compose.yml配置，整合所有依赖组件，快速搭建环境，核心包含：
● 前端Nginx服务
● Java后端服务集群
● Python AI服务集群
● MySQL、Redis、MinIO、RabbitMQ
● Milvus/Qdrant向量数据库
● Elasticsearch单节点
8.2.2 生产环境 Kubernetes 部署
生产环境采用Kubernetes集群部署，核心架构如下：
1. 集群规划
  ○ 控制平面：3节点高可用，etcd集群3副本
  ○ 业务节点：按服务类型拆分节点池，Java业务节点、AI计算节点、存储节点、网关节点
  ○ 存储：使用分布式存储（Ceph），提供持久化存储
2. 核心部署资源
  ○ 无状态服务：Deployment，配置资源限制、健康检查、自动扩缩容HPA
  ○ 有状态服务：StatefulSet，如MySQL、Milvus、Elasticsearch，保证稳定的网络标识与存储
  ○ 配置管理：ConfigMap存储配置文件，Secret存储敏感信息、密钥
  ○ 服务暴露：通过Ingress-Nginx暴露网关服务，配置HTTPS证书、限流、WAF
3. 高可用设计
  ○ 所有服务多副本部署，跨节点调度，避免单点故障
  ○ 数据库主从架构、Redis集群、向量数据库集群，保证数据高可用
  ○ 配置Pod反亲和性，避免同一服务的多个副本调度到同一节点
  ○ 配置健康检查、就绪探针，故障自动重启、自动重建
8.3 CI/CD持续集成流程
基于GitLab CI/GitHub Actions + ArgoCD实现完整的DevOps流程：
1. 代码提交触发：开发人员提交代码到Git仓库，触发CI流水线
2. 持续集成：
  ○ 代码检查：SonarQube代码质量扫描、漏洞检测
  ○ 单元测试：执行单元测试、集成测试，生成测试报告
  ○ 构建打包：构建Docker镜像，推送至私有镜像仓库，标记版本号
3. 持续部署：
  ○ 自动更新K8s部署清单中的镜像版本
  ○ ArgoCD监听部署清单变更，自动同步到目标环境
  ○ 执行滚动更新，保证服务不中断
4. 发布策略：
  ○ 开发/测试环境：自动发布
  ○ 预发/生产环境：人工审批后发布
  ○ 生产环境支持蓝绿发布、金丝雀发布，降低发布风险
8.4 备份与灾难恢复
1. 数据备份策略
  ○ 数据库备份：MySQL每日全量备份、每小时增量备份，备份文件存储到对象存储，保留30天
  ○ 向量数据库备份：定期全量快照备份，增量数据同步，支持时间点恢复
  ○ 对象存储备份：MinIO开启版本控制、跨区域复制，保障文档文件不丢失
  ○ 配置数据备份：K8s资源、系统配置定期备份，支持快速恢复
2. 灾难恢复预案
  ○ RTO（恢复时间目标）：核心业务4小时内恢复，非核心业务8小时内恢复
  ○ RPO（恢复点目标）：数据丢失不超过1小时
  ○ 故障场景预案：单节点故障、服务集群故障、数据库故障、机房故障，均有对应的恢复流程
  ○ 定期演练：每季度执行一次灾难恢复演练，验证预案有效性
3. 高可用容灾
  ○ 同城双活：核心组件支持同城双机房部署，主机房故障可切换到备用机房
  ○ 异地备份：核心备份数据同步到异地对象存储，应对区域性灾难
九、项目实施路线图
项目采用渐进式迭代开发策略，分4个阶段落地，兼顾快速上线与长期演进：
9.1 第一阶段：MVP基础版（2-3个月）
核心目标：实现核心功能闭环，快速上线验证业务价值
核心交付物：
1. 基础的用户权限、部门、角色管理体系
2. 知识库空间、文档管理、富文本编辑、文档上传基础能力
3. 基础的文档解析、向量化、单路向量检索能力
4. 基础的RAG智能问答，支持流式输出、来源引用
5. 极简的前端门户与管理后台界面
6. 开发环境一键部署方案
验收标准：实现文档上传→解析→向量化→智能问答的完整闭环，支持基础的权限管控。
9.2 第二阶段：增强版（3-4个月）
核心目标：完善AI核心能力，提升系统可用性与体验
核心交付物：
1. 智能记忆管理系统，支持多轮对话个性化体验
2. 增强型文档解析引擎，支持复杂PDF、扫描件OCR、表格专项处理
3. 多路召回、重排序、查询理解的增强RAG引擎
4. 基于Docker的安全代码沙箱与代码解释器能力
5. 完善的管理后台、运营统计、Badcase基础管理
6. 完整的权限体系优化，支持向量检索前置过滤
7. 测试环境、预发环境部署方案，自动化测试体系
验收标准：AI能力达到企业级可用标准，文档解析适配企业复杂场景，系统功能完整，体验流畅。
9.3 第三阶段：企业版（2-3个月）
核心目标：满足企业级安全合规、高可用、大规模部署需求
核心交付物：
1. 完整的多租户隔离体系，支持三种隔离粒度
2. 深度权限管控体系，分级审批流程、动态权限变更、全链路审计
3. 完整的安全合规能力，异常检测、安全告警、等保适配
4. 全链路监控体系、性能优化、高可用集群部署方案
5. 生产环境K8s部署方案、CI/CD流水线、灾备方案
6. Agent编排基础能力、工作流基础支持
7. 完整的系统操作手册、运维手册、开发文档
验收标准：系统满足企业级安全合规要求，支持大规模生产部署，高可用、可运维、可扩展。
9.4 第四阶段：智能版（持续迭代）
核心目标：打造智能化、自动化的企业知识管理平台，构建生态能力
核心交付物：
1. 多Agent协作框架，支持复杂企业场景的自动化处理
2. Badcase自动化优化闭环，AI能力自迭代优化
3. 领域模型微调，适配企业专属行业场景
4. 智能工作流引擎，适配企业标准化知识管理流程
5. 第三方系统集成能力，支持企业微信、钉钉、OA、CRM等系统对接
6. 可视化低代码平台，支持企业自定义AI能力与流程
验收标准：系统具备高度的智能化、自动化能力，可深度适配企业个性化业务场景，形成完整的知识管理生态。
十、方案总结与未来展望
10.1 方案总结
本方案为企业提供了一套完整、可落地、安全可控的企业级智能知识库解决方案，核心价值如下：
1. 解决核心痛点：彻底解决了传统知识库检索低效、知识孤岛的问题，同时解决了AI大模型在企业应用中数据安全、权限管控、幻觉等核心痛点。
2. 安全深度融合：独创的三层纵深防御权限架构，将权限管控深度融入RAG全流程，实现了“智能赋能”与“数据安全”的平衡，满足强监管行业的合规要求。
3. 企业级能力完备：从文档解析、知识管理、AI问答、权限管控到审计运维，提供了企业级知识库所需的全量能力，适配中大型企业复杂的组织架构与业务场景。
4. 架构先进可扩展：采用前后端分离、微服务架构，拆分Java业务服务与Python AI服务，兼顾了业务稳定性与AI能力的灵活性，支持水平扩展与持续迭代。
5. 可落地性强：方案提供了完整的功能设计、核心流程、技术栈、数据模型、部署方案、实施路线图，可直接作为项目开发的蓝图，快速落地实施。
10.2 未来演进方向
1. 零信任架构升级：基于身份的动态访问控制，实现持续验证、永不信任的零信任安全架构，进一步提升系统安全防护能力。
2. 隐私计算技术融合：引入同态加密、联邦学习技术，实现跨机构、跨企业的安全知识共享，在不泄露原始数据的前提下，实现联合知识检索与模型训练。
3. 多模态知识库升级：支持图片、音频、视频等多模态内容的解析、向量化、检索与问答，打造全模态的企业智能知识库。
4. 知识图谱增强：构建企业知识图谱，实现实体、关系、事件的结构化管理，基于知识图谱实现推理问答、关联分析，进一步提升AI问答的深度与准确性。
5. 端侧AI轻量化部署：支持端侧轻量化大模型部署，实现敏感数据本地处理，进一步降低数据泄露风险，适配离线、内网等特殊场景。
6. 生态化平台建设：打造开放的插件市场、应用市场，支持第三方开发者开发适配不同行业、不同场景的插件与应用，构建完整的企业知识管理生态。








已完成功能（三阶段全部交付）
模块	功能	状态
用户认证	登录/登出、密码修改、权限查询、RBAC	✅
用户/部门/角色管理	CRUD、角色权限关联、部门树	✅
多租户隔离	三级隔离(METADATA/PARTITION/COLLECTION)、TenantService	✅
动态权限变更	PermissionChangeService + Redis Token黑名单 + Sa-Token强制下线	✅
知识库空间	CRUD、存储配额	✅
文档管理	CRUD、上传、解析触发、向量化触发	✅
文档详情页	文本分块查看、内容预览、版本历史、权限管理	✅
审批流程	提交/处理/查询、分级审批路由、事件通知	✅
全链路审计	@AuditLog AOP + Redis热数据 + ES冷数据双写	✅
三层RLS行级安全	DataPermissionInterceptor (tenant→security→dept)	✅
安全告警	6种检测类型、处理/忽略、统计	✅
Badcase管理	分页查询、统计、处理	✅
统计报表	系统概览、AI统计、用户活跃度、ECharts图表	✅
向量库管理	统计/分页/删除/重建索引/集合列表	✅
LLM配置管理	数据库配置、前端页面	✅
文档解析	多格式解析、质量评估、Celery异步任务	✅
向量检索	Milvus/Qdrant双引擎、权限前置过滤、混合检索	✅
RAG问答	多路召回、重排序、SSE流式、答案反馈	✅
记忆管理	创建/搜索/提取/删除/过期清理	✅
Agent编排	多角色Agent、工具调用、工作流引擎	✅
代码沙箱	安全检测、Docker隔离执行	✅
部署方案	Docker Compose + K8s + Prometheus监控	✅
定时任务	XXL-JOB(记忆清理/状态同步/统计报表/权限缓存)	✅
未完成功能清单（已更新至最新代码状态）
一、3.1 前台用户门户模块（6项，已全部补全）
#	功能	状态
1	OAuth2/SSO单点登录	✅ 已实现 - OAuth2Controller + OAuth2Service，支持企微/钉钉/通用OAuth2.0
2	双因素认证(2FA)	✅ 已实现 - TwoFactorAuthController + TwoFactorAuthService，TOTP生成/验证/禁用
3	文档收藏/点赞/评论	✅ 已实现 - DocumentInteractionController + 前端互动Tab
4	文档编辑器(Vditor)	✅ 已实现 - knowledge/editor.vue，集成Vditor富文本编辑器
5	浏览历史	✅ 已实现 - BrowseHistory表 + 前端API + profile页展示
6	内容创作辅助	✅ 已实现 - Python content_assist.py + AiProxyController代理 + 前端API
二、3.2 后台管理运营模块（3项，已全部补全）
#	功能	状态
7	动态菜单/路由管理	✅ 已实现 - admin/menu.vue + RolePermissionController
8	系统配置管理	✅ 已实现 - SysConfigController + admin/config.vue
9	批量用户导入	✅ 已实现 - UserBatchImportController + 前端userImportApi
三、3.3 AI智能能力中心（4项，已全部补全）
#	功能	状态
10	PaddleOCR增强	✅ 已实现 - document_parser.py中OCR分支 + requirements.txt已声明
11	记忆冲突检测	✅ 已实现 - quality_tools.py conflict_detect + AiProxyController代理
12	答案忠实度校验	✅ 已实现 - faithfulness_validator.py + quality_tools.py + AiProxyController代理
13	父文档检索/上下文增强	✅ 已实现 - parent_document_retriever.py，分块时保留parent_document_id
四、3.4 安全管控与审计模块（3项，已全部补全）
#	功能	状态
14	传输/存储加密	✅ 已实现 - EncryptTypeHandler + FieldEncrypt注解 + DataEncryptor集成
15	Vault密钥管理	✅ 已实现 - VaultSecretService + VaultProperties + application-vault.yml，部署Vault后启用
16	用户数据导出/销毁	✅ 已实现 - UserDataExportController，支持导出/销毁/匿名化
五、5.5 基础设施与运维（4项，已全部补全代码/配置）
#	功能	状态
17	SkyWalking链路追踪	✅ 已实现 - SkyWalkingConfiguration(MDC+TraceId桥接) + Python OpenTelemetry + skywalking-config.yml
18	CI/CD流水线	✅ 已实现 - .gitlab-ci.yml(5阶段:lint→build→test→package→deploy) + ArgoCD生产部署
19	数据库读写分离	✅ 已实现 - DynamicDataSourceConfiguration + ReadOnlyDataSourceAspect(AOP自动路由) + application-replication.yml
20	备份与灾难恢复	✅ 已实现 - backup.sh(MySQL+Redis+ES全量备份/恢复/清理) + 保留策略
六、第四阶段智能版（6项，代码骨架+API已全部补全）
#	功能	状态
21	多Agent协作框架	✅ 已实现 - enterprise_agent_orchestrator.py + AiProxyController代理 + 前端API
22	Badcase自动化优化闭环	✅ 已实现 - badcase_optimizer.py + AiProxyController代理 + 前端API
23	领域模型微调	✅ 已实现 - domain_model_tuning.py + AiProxyController代理 + 前端API
24	智能工作流引擎	✅ 已实现 - workflow_engine.py + AiProxyController代理 + 前端API
25	第三方系统集成	✅ 已实现 - third_party.py + AiProxyController代理 + 前端API
26	可视化低代码平台	✅ 已实现 - admin/lowcode.vue + 前端路由
七、10.2 未来演进方向（5项，属远期规划）
#	功能	状态
27	零信任架构	⬜ 远期规划
28	隐私计算/联邦学习	⬜ 远期规划
29	多模态知识库	⬜ 远期规划
30	知识图谱增强	⬜ 远期规划
31	端侧AI轻量化部署	⬜ 远期规划
总结：代码层面功能已全部补全！#1-26均为✅已实现，#27-31属远期规划（零信任架构/隐私计算/多模态/知识图谱/端侧AI），按业务优先级后续迭代。本轮新增：#15 Vault密钥管理(VaultSecretService)、#17 SkyWalking链路追踪(MDC+OpenTelemetry)、#19 数据库读写分离(DynamicDataSource+AOP)、网关路由顺序修复、Logback traceId集成。


 第五阶段开发内容
根据项目规划，第五阶段包含5项前沿技术探索：
#27 零信任架构 - 基于身份的动态访问控制
#28 隐私计算/联邦学习 - 跨机构安全知识共享
#29 多模态知识库 - 图片/音频/视频解析与检索
#30 知识图谱增强 - 实体关系结构化+推理问答
#31 端侧AI轻量化部署 - 本地敏感数据处理
