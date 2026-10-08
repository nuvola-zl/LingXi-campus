# DAG 设备申领模块排查实录：我让 AI 审查代码，但每个结论都要我先点头

> 项目：Haze-AI-Hub · 行政域设备申领模块
> 主题：一次完整的「AI 协作代码审查 → 我验证 → 修复 → 回归测试 → 新问题再定位」闭环
> 说明：本文如实记录了与 AI 协作的全过程，包括 AI 最初的判断、我的质疑、验证结果（4 个报告问题中 1 个我判定为设计取舍不修）、以及回归测试中暴露的新问题。所有代码分析与修复由 AI 执行，**每一个结论都经过我读源码或查数据库确认后才采纳**。

## 一、起点：我先让 AI 把模块讲清楚

在联调之前，我让 AI 通读设备申领模块的全部代码，给我梳理整体逻辑。以下为 AI 分析得出、经我对照源码确认的内容。

### 核心数据表（5 张）

| 表 | 用途 |
| --- | --- |
| admin_device_request | 申领单，记录谁申领了什么设备，状态、关联的设备ID和采购单号 |
| admin_device_inventory | 按设备类型维度的库存（总数/可用数） |
| admin_device_detail | 每台设备的明细（SN号、状态、分配给谁） |
| admin_purchase_request | 采购单（库存不够时自动生成） |
| admin_device_return | 归还记录（用户归还→管理员确认入库） |

### 三种入口

| 入口 | 路径 | 说明 |
| --- | --- | --- |
| 普通 HTTP API | /admin/device/apply | 同步提交，无 DAG |
| DAG 驱动 API | /dag/device/apply | 事务提交后异步触发 DAG |
| AI 智能申领 | DeviceTools.requestDevice() | LLM 通过 function calling 触发，自动查重 + 异步 DAG |

### DAG 编排流程

整个申领流程由 DagEngine 驱动，DAG 定义在 `DeviceDagService.buildDagDefinition()`：

```text
checkInventory（检查库存）
    │
    ▼
branchByStock（条件分支）
    │
    ├── 有库存 ──→ allocateDevice ──→ installSystem ──┐
    │                 │                                ├──→ notifyPickup（完成申领）
    │                 └──→ configPermission ───────────┘
    │
    └── 无库存 ──→ createPurchase ──→ waitPurchase（挂起等待）
                                           │
                                    管理员确认到货后 resume
                                           │
                                           ▼
                                    allocateDevice → installSystem / configPermission → notifyPickup
```

各节点职责：

1. **CheckInventoryNode** —— 读申领单的 stockStatus 字段（提交时已判定）
2. **BranchByStockNode** —— 条件路由：有库存走分配，无库存走采购
3. **AllocateDeviceNode** —— 悲观锁（SELECT ... FOR UPDATE）扣库存 + 分配一台具体设备给用户，设置状态为「待领取」
4. **InstallSystemNode** —— 模拟安装操作系统（目前是 sleep 500ms 的占位实现）
5. **ConfigPermissionNode** —— 模拟配置系统权限（同样是占位）
6. **CreatePurchaseNode** —— 生成采购单号（PO 开头），关联到申领单
7. **WaitPurchaseNode** —— 异步节点：检查采购单是否到货（status=4），未到货则挂起 DAG
8. **NotifyPickupNode** —— 调用 completeRequest() 把申领单状态改为「已完成」

DAG 引擎特性：

- **幂等**：每个节点执行前查 sys_dag_execution 表，已成功的节点跳过
- **挂起/恢复**：WaitPurchaseNode 未到货时返回 WAITING_ASYNC，管理员确认到货后调用 resumeOnArrived() 恢复执行
- **并行**：installSystem 和 configPermission 无相互依赖，DAG 引擎并行执行

### 申领单状态流转

```text
提交 → status=2(采购中) 或 status=3(准备中) → status=4(待领取) → status=5(已完成)
                                                    ↓ 取消              ↓ 归还
                                               status=6(已取消)    admin_device_return
```

### 功能清单

用户侧（DeviceUserController /api/device）：

| 功能 | 接口 | 说明 |
| --- | --- | --- |
| 确认领取 | POST /{requestNo}/confirmReceive | 状态=4 时可确认，改为已完成 |
| 取消申领 | POST /{requestNo}/cancel | 采购中→取消申领+采购单；待领取→回滚设备+库存+取消 |
| 归还设备 | POST /{requestNo}/return | 已完成的可归还，生成归还记录待管理员确认 |

管理侧（AdminManageController /admin/manage）：

| 功能 | 接口 | 说明 |
| --- | --- | --- |
| 待审批列表 | GET /pending | 聚合请假/打卡/采购所有待审批任务 |
| 审批采购单 | POST /purchase/{id}/approve | 审批通过/拒绝 |
| 确认到货 | POST /purchase/{orderNo}/arrived | 入库+生成SN+更新库存+恢复DAG |
| 确认提货 | POST /device/{requestNo}/confirmHandover | 线下场景管理员扫码确认 |
| 确认归还入库 | POST /device/return/{returnId}/confirm | 设备回库+库存+1+更新归还记录 |
| 采购列表 | GET /purchase/list | 分页查询采购单 |
| 库存列表 | GET /device/inventory | 查看所有设备类型库存 |

定时任务：

| 任务 | 频率 | 说明 |
| --- | --- | --- |
| DeviceTimeoutJob | 每天凌晨 2 点 | 扫描待领取超 7 天的单子，自动回滚设备+库存，取消申领 |
| DeviceCompensationJob | 每 10 分钟 | ① 回收失败单子（status=6）已分配的设备资源 ② 检测「已到货但 DAG 未恢复」的死锁单子并自动恢复 |

并发安全设计：

- 设备分配使用 SELECT ... FOR UPDATE 悲观锁（AdminDeviceInventoryMapper.selectForUpdate）
- 采购到货使用乐观锁（`ne(AdminPurchaseRequest::getStatus, 4)` 防重复入库）
- DAG 节点全部幂等（数据库记录检查）

## 二、我的质疑：完整吗？有没有逻辑 Bug？是不是过度设计？

看完梳理后我没有直接接受「这个模块很完整」的结论，而是追问了三个问题：功能有没有严重逻辑 bug？DAG 跟直接 CRUD 到底有啥区别？是不是设计得太复杂了？

### AI 报告的 4 个逻辑 Bug

**Bug 1（严重）：NotifyPickupNode 过早完成申领。** `NotifyPickupNode.java:28` 调用了 `completeRequest()`，直接把状态从 4（待领取）改成 5（已完成）。但用户确认领取的接口 `DeviceUserController.confirmReceive()` 校验 `status != 4` 就报错。结果：DAG 跑完后状态变成 5，用户永远无法调用「确认领取」。NotifyPickupNode 的职责应该是「通知」，不应该替用户完成申领。

**Bug 2（中等）：submitRequest 查库存无锁，存在竞态。** `DeviceRequestServiceImpl.java:56-59` 查库存判断 hasStock 时没有加锁。两个并发请求可能都认为「有库存」，都创建 status=3 的申领单，然后到 allocateDevice 时只有一个能成功（悲观锁），另一个抛异常。用户会看到「申领成功，有库存，正在处理」，然后 DAG 失败，体验很差。

**Bug 3（中等）：补偿任务可能重复释放资源。** `cancelAllocated()` 已经把设备回库、库存+1 了，但 allocatedDeviceId 没清空。然后 DeviceCompensationJob 每 10 分钟扫 `status=6 且 allocated_device_id IS NOT NULL` 的单子，又调 compensate() 再释放一次。结果：库存会被重复+1，数据不一致。

**Bug 4（轻微）：「准备中」状态不可取消。** `DeviceUserController.java:89-90` —— status=3（准备中，DAG 运行中）直接抛异常不允许取消。但如果 DAG 中途卡住了（比如 InstallSystemNode 异常），用户永远取消不了。

### 我的验证与裁决

我没有让 AI 直接改，而是要求它先逐个验证这 4 个问题是否真实存在，再动手。验证结果：

- **Bug 1、2、3 属实**，逐一修复；
- **Bug 4 我判定为设计取舍问题，不修**：DAG 运行窗口只有几秒，失败会走补偿任务兜底，为一个极端小概率场景增加「运行中可取消」的复杂度不值得。

这次协作我坚持的底线是：**AI 报的 bug 不能照单全收，先验证存在性，再评估严重性，最后才改**。事实证明这个流程是必要的——4 个报告里就有 1 个被我拦下了。

### 修复内容（4 个文件，编译通过）

1. **NotifyPickupNode.java** —— 去掉 `completeRequest()` 调用和 DeviceRequestService 注入；DAG 跑完后状态停留在 status=4（待领取），由用户或管理员确认领取时才改为 status=5（已完成）。
2. **CheckInventoryNode.java** —— 恢复实时查库存，不再信任 submitRequest 无锁预判的 stockStatus 值；运行时重新查询 admin_device_inventory 表获取真实库存；库存状态若与预判不一致，自动纠正申领记录的 stockStatus 和 status，确保 BranchByStockNode 路由到正确分支。
3. **DeviceUserController.java** —— `cancelAllocated()` 释放设备+回滚库存后，将 allocatedDeviceId 置为 null，补偿任务不会再误扫。
4. **DeviceCompensationService.java** —— 库存回滚加防护：只有真正释放了设备（device.status==2）才回滚库存，用 `deviceReleased` 布尔标记替代原来的无条件 inventory+1。

## 三、回归测试：我自己测出了 3 个新 Bug

修复部署后，我按照测试计划（见 [testing/10](../testing/10-设备申领联调测试计划.md)）跑了完整联调，把测试流程和结果整理成测试报告（见 [testing/11](../testing/11-AI对话驱动测试与测试报告.md)），然后让 AI 验证我测出的 bug 是否真实、是不是代码问题。

**AI 定位结果：我发现的 3 个 bug 全是同一个根因——MyBatis-Plus 配置了 `update-strategy: not_null`**（application.yaml），导致所有 `setXxx(null) + updateById()` 操作中，null 字段直接被跳过不更新到数据库。

| Bug | 代码位置 | 操作 | 为什么失效 |
| --- | --- | --- | --- |
| 取消后 allocated_device_id 未清空 | DeviceUserController:152-154 | `request.setAllocatedDeviceId(null)` | MP 忽略 null |
| 取消后 assigned_user_id 未清空 | DeviceUserController:136-138 | `device.setAssignedUserId(null)` | MP 忽略 null |
| 归还后 assigned_user_id 未清空 | AdminManageController:143-145 | `device.setAssignedUserId(null)` | MP 忽略 null |

改法统一：用 `LambdaUpdateWrapper.set(Entity::getField, null)` 代替 `entity.setField(null) + updateById(entity)`。同时 AI 主动排查了另外两条同模式代码路径（DeviceCompensationService、DeviceTimeoutJob），一并修复，共 4 个文件。

同时 AI 复核了我的测试报告，确认除上述 3 个 bug 外其余结论全部正确（DAG 6 节点成功、并行执行、waitPurchase 挂起 23 分钟后恢复、联动取消、归还入库、重复拦截均验证通过）。

## 四、一次「冤枉 AI」的经历：偶发 NPE 到底是谁改出来的

回归测试时我遇到一个新报错：configPermission 节点抛 NPE，导致 DAG 整体失败。由于这个 NPE 恰好出现在 AI 修改之后，我的第一反应是「是不是你改 null 的时候把原本的逻辑改崩了」，直接要求 AI 先修。

**AI 的反驳有理有据**：它逐一论证修改过的四个方法（cancelAllocated、confirmReturn、compensate、autoRecycle）都属于取消/归还/定时任务路径，正常申领流程根本不会调用；随后定位到真正根因——`DagContext.results` 用的是普通 HashMap，而 installSystem 和 configPermission 并行执行，两个线程同时读写这个 HashMap，竞态条件下 `entry.getValue()` 返回 null → NPE。

**为什么之前没触发、现在触发了？** 这是竞态条件，取决于两个并行节点的完成时机；重新编译部署后 JVM 的 JIT 编译策略、线程调度有微小变化，窗口变大了。**这是一个原有的并发 bug，与本次修改无关。**

我接受了这个结论，让它修复：DagContext 的 results 从 HashMap 改为 ConcurrentHashMap，toJson() 里加 `if (nodeResult == null) continue` 防御。重新测试后问题消失。

这件事给我的教训：**「改动之后出问题」不等于「改动导致问题」，定位要靠证据链（调用路径分析）而不是时间相关性。**

### 附带讨论：后端报错但前端显示成功，怎么办？

我顺带问了一个设计问题（明确说不修改、只讨论）：为什么 DAG 失败了前端还显示「申领成功」？

原因：整个流程是异步的——`submitRequest()` 成功就立即返回 200，DAG 在另一线程跑，前端早就收到「成功」了，不知道后面的事。这是异步架构的固有问题。

| 方案 | 复杂度 | 效果 |
| --- | --- | --- |
| 提交后前端自动轮询状态 2-3 次 | 低 | 2-3 秒后能发现失败 |
| SSE 服务端推送 DAG 节点状态 | 中 | 实时看到每个节点进度 |
| WebSocket 双向通信 | 高 | 最实时，但重 |

结论：最实用的是提交后自动查 3 次状态——DeviceTools.requestDevice 返回的文案里已经有提示让用户查进度了，只是没做成自动轮询。**此条记录为待办，未实施。**

## 五、联调中暴露的前后端交互问题（同一轮协作）

### 5.1 「确认到货」按钮永远不显示：前后端字段名不一致

测试中我发现管理员的审批流程走不通，让 AI 排查。结论：

- 后端 `ApprovalTaskServiceImpl.java:77` 返回的字段名是 `no`（`task.put("no", purchase.getOrderNo())`）；
- 前端 `ApprovalManageView.vue:61` 判断的是 `a.orderNo` —— 永远 false，「确认到货」按钮永远不出现。

修复：后端字段名 `no` → `orderNo`；同时把待审批列表查询范围从 `status=1` 扩大到 `status IN (1, 2)`，审批通过后采购单仍在列表中，管理员可以接着点「确认到货」；并补充 title、reason、requester 字段供前端卡片展示。

### 5.2 我的设计建议被采纳：采购操作挪到设备管理页面

我提出：审批管理页面看采购单不详细，为什么不在「设备管理 → 采购单管理」里直接加批准/驳回按钮？AI 认可这个判断，在 DeviceManageView.vue 的采购单表格中增加操作列（status=1 显示批准+驳回，status=2 显示确认到货），ApprovalManageView 的批准/驳回按钮也加了状态条件防止重复操作。

### 5.3 三个前端体验问题的完整改造

我提出的三个体验问题及最终方案：

| 问题 | 修复方式 |
| --- | --- |
| 「我的设备」需手动输入申领编号才能操作 | 新增 `GET /api/device/my-requests` 接口，切换到 tab 时自动加载当前用户所有申领记录列表 |
| 归还按钮提交后仍然显示可点 | `returnDevice()` 设置 status=7（归还中），前端归还按钮校验 `status===5 && allocatedDeviceId` 双重条件 |
| 管理员归还确认需手动输入归还记录 ID（管理员根本不可能知道 ID） | 新增 `GET /admin/manage/device/returns/pending` 接口，设备交接页改为待确认归还列表表格，每条记录带「确认入库」按钮 |

共修改后端 2 个文件、前端 4 个文件，编译通过。

## 六、本轮协作的沉淀

这一轮的三个深度问题（MyBatis-Plus null 静默忽略、DAG 并发 NPE、LLM 幻觉）我单独整理成了详细的技术记录，见 [troubleshooting/07](./07-三个深度问题排查记录.md)。

**协作方法论小结：**

1. AI 适合做「全面扫描 + 假设提出」，不适合做「最终裁决」——存在性和严重性都要人工验证；
2. 修复后必须用数据库验证，不能只看接口返回成功（MyBatis-Plus 那个 bug 接口全程返回成功）；
3. 偶发问题优先怀疑并发与共享状态，而不是最近一次的改动。
